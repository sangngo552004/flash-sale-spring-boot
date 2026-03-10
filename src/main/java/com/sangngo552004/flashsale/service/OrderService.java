package com.sangngo552004.flashsale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangngo552004.flashsale.entity.Order;
import com.sangngo552004.flashsale.entity.OutboxEvent;
import com.sangngo552004.flashsale.entity.Product;
import com.sangngo552004.flashsale.repository.OrderRepository;
import com.sangngo552004.flashsale.repository.OutboxRepository;
import com.sangngo552004.flashsale.repository.ProductRepository;
import com.sangngo552004.flashsale.util.Const;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OrderService {
    private static final int PURCHASE_AMOUNT = 1;
    private static final String MESSAGE_TOO_FAST = "BẠN THAO TÁC QUÁ NHANH";
    private static final String MESSAGE_SOLD_OUT = "HẾT HÀNG";
    private static final String MESSAGE_PRODUCT_NOT_FOUND = "SẢN PHẨM KHÔNG TỒN TẠI";
    private static final String MESSAGE_DUPLICATE = "BẠN ĐÃ ĐẶT ĐƠN NÀY RỒI (DUPLICATE REQUEST)";
    private static final String MESSAGE_SYSTEM_ERROR = "LỖI HỆ THỐNG, VUI LÒNG THỬ LẠI";

    private final StringRedisTemplate redisTemplate;
    private final OrderRepository orderRepo;
    private final OutboxRepository outboxRepo;
    private final ProductRepository productRepo;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    private final Map<Long, Boolean> localSoldOutCache = new ConcurrentHashMap<>();
    private final DefaultRedisScript<Long> decrementStockScript = createDecrementStockScript();

    public String purchase(String userId, Long productId, String idempotencyKey) {
        if (isRateLimited(userId)) {
            return MESSAGE_TOO_FAST;
        }

        if (Boolean.TRUE.equals(localSoldOutCache.get(productId))) {
            return MESSAGE_SOLD_OUT;
        }

        Product product = productRepo.findById(productId).orElse(null);
        if (product == null) {
            return MESSAGE_PRODUCT_NOT_FOUND;
        }

        if (product.isFlashSale()) {
            return purchaseFlashSale(userId, productId, idempotencyKey);
        }

        return purchaseNormalSale(userId, productId, idempotencyKey);
    }

    public void clearLocalSoldOut(Long productId) {
        localSoldOutCache.remove(productId);
    }

    public void clearAllLocalSoldOut() {
        localSoldOutCache.clear();
    }

    private String purchaseNormalSale(String userId, Long productId, String idempotencyKey) {
        try {
            return executeInTransaction(() -> {
                int updatedRows = productRepo.decrementStockForNormalSale(productId, PURCHASE_AMOUNT);
                if (updatedRows == 0) {
                    return MESSAGE_SOLD_OUT;
                }

                Order order = createPendingOrder(userId, productId, idempotencyKey);
                persistOutbox(order);
                return "ĐẶT HÀNG THÀNH CÔNG! Order ID: " + order.getId();
            });
        } catch (DataIntegrityViolationException e) {
            return MESSAGE_DUPLICATE;
        } catch (RuntimeException e) {
            return MESSAGE_SYSTEM_ERROR;
        }
    }

    private String purchaseFlashSale(String userId, Long productId, String idempotencyKey) {
        Long stockLeft = executeLuaScript(stockKey(productId), PURCHASE_AMOUNT);
        if (stockLeft == null) {
            return MESSAGE_SYSTEM_ERROR;
        }
        if (stockLeft == -2L) {
            localSoldOutCache.put(productId, true);
            return MESSAGE_SOLD_OUT;
        }
        if (stockLeft == -1L) {
            return MESSAGE_PRODUCT_NOT_FOUND;
        }

        try {
            return executeInTransaction(() -> {
                Order order = createPendingOrder(userId, productId, idempotencyKey);
                persistOutbox(order);
                return "ĐẶT HÀNG THÀNH CÔNG! Order ID: " + order.getId();
            });
        } catch (DataIntegrityViolationException e) {
            compensateFlashSaleStock(productId);
            return MESSAGE_DUPLICATE;
        } catch (RuntimeException e) {
            compensateFlashSaleStock(productId);
            return MESSAGE_SYSTEM_ERROR;
        }
    }

    private Order createPendingOrder(String userId, Long productId, String idempotencyKey) {
        Order order = new Order();
        order.setUserId(userId);
        order.setProductId(productId);
        order.setAmount(BigDecimal.ONE);
        order.setIdempotencyKey(idempotencyKey);
        order.setStatus(Order.STATUS_PENDING);
        return orderRepo.save(order);
    }

    private void persistOutbox(Order order) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setAggregateType("ORDER");
            event.setAggregateId(order.getId().toString());
            event.setType("ORDER_CREATED");
            event.setPayload(objectMapper.writeValueAsString(order));
            event.setStatus(Const.OUTBOX_STATUS_PENDING);
            outboxRepo.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot persist outbox event", e);
        }
    }

    private boolean isRateLimited(String userId) {
        String key = Const.REDIS_KEY_RATE_LIMIT + userId + ":" + Instant.now().getEpochSecond();
        Long currentCount = redisTemplate.opsForValue().increment(key);
        if (currentCount == null) {
            return false;
        }
        if (currentCount == 1L) {
            redisTemplate.expire(key, java.time.Duration.ofSeconds(1));
        }
        return currentCount > 2;
    }

    private void compensateFlashSaleStock(Long productId) {
        redisTemplate.opsForValue().increment(stockKey(productId), PURCHASE_AMOUNT);
        localSoldOutCache.remove(productId);
    }

    private Long executeLuaScript(String key, int amount) {
        return redisTemplate.execute(decrementStockScript, Collections.singletonList(key), String.valueOf(amount));
    }

    private DefaultRedisScript<Long> createDecrementStockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/decrement_stock.lua"));
        script.setResultType(Long.class);
        return script;
    }

    public void restoreStockForExpiredOrder(Long productId) {
        Product product = productRepo.findByIdForUpdate(productId).orElse(null);
        if (product == null) {
            return;
        }

        if (product.isFlashSale()) {
            redisTemplate.opsForValue().increment(stockKey(productId), PURCHASE_AMOUNT);
            localSoldOutCache.remove(productId);
            return;
        }

        productRepo.incrementStock(productId, PURCHASE_AMOUNT);
    }

    private String stockKey(Long productId) {
        return Const.REDIS_KEY_PRODUCT_STOCK + productId;
    }

    private String executeInTransaction(TransactionCallback callback) {
        return new TransactionTemplate(transactionManager).execute(status -> callback.doInTransaction());
    }

    @FunctionalInterface
    private interface TransactionCallback {
        String doInTransaction();
    }
}
