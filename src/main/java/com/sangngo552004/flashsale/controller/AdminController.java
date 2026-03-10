package com.sangngo552004.flashsale.controller;

import com.sangngo552004.flashsale.entity.Product;
import com.sangngo552004.flashsale.repository.OrderRepository;
import com.sangngo552004.flashsale.repository.OutboxRepository;
import com.sangngo552004.flashsale.repository.ProductRepository;
import com.sangngo552004.flashsale.service.OrderService;
import com.sangngo552004.flashsale.util.Const;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;
    private final OutboxRepository outboxRepo;
    private final StringRedisTemplate redisTemplate;
    private final OrderService orderService;

    @PostMapping("/reset")
    @Transactional
    public String resetSystem(@RequestParam(defaultValue = "10") int stock) {
        orderRepo.deleteAll();
        outboxRepo.deleteAll();

        Product product = productRepo.findByIdForUpdate(1L)
                .orElseGet(() -> new Product(1L, "iPhone 16 Pro Max", 0, false, 0L));
        product.setStock(stock);
        product.setFlashSale(false);
        productRepo.save(product);

        redisTemplate.delete(Const.REDIS_KEY_PRODUCT_STOCK + 1L);
        orderService.clearAllLocalSoldOut();

        return "Hệ thống đã được reset! Stock DB = " + stock + ", flash sale đã tắt và Redis cache đã được xóa.";
    }

    @PostMapping("/campaign/start")
    @Transactional
    public String startCampaign(@RequestParam Long productId, @RequestParam int flashSaleStock) {
        if (flashSaleStock <= 0) {
            return "SỐ LƯỢNG FLASH SALE KHÔNG HỢP LỆ";
        }

        Product product = productRepo.findByIdForUpdate(productId).orElse(null);
        if (product == null) {
            return "SẢN PHẨM KHÔNG TỒN TẠI";
        }
        if (product.isFlashSale()) {
            return "CHIẾN DỊCH FLASH SALE ĐANG CHẠY";
        }
        if (product.getStock() < flashSaleStock) {
            return "KHÔNG ĐỦ TỒN KHO ĐỂ CẤP PHÁT FLASH SALE";
        }

        product.setStock(product.getStock() - flashSaleStock);
        product.setFlashSale(true);
        productRepo.save(product);

        redisTemplate.opsForValue().set(Const.REDIS_KEY_PRODUCT_STOCK + productId, String.valueOf(flashSaleStock));
        orderService.clearLocalSoldOut(productId);

        return "Đã bắt đầu flash sale cho productId=" + productId + ", phân bổ " + flashSaleStock + " sản phẩm vào Redis.";
    }

    @PostMapping("/campaign/end")
    @Transactional
    public String endCampaign(@RequestParam Long productId) {
        Product product = productRepo.findByIdForUpdate(productId).orElse(null);
        if (product == null) {
            return "SẢN PHẨM KHÔNG TỒN TẠI";
        }
        if (!product.isFlashSale()) {
            return "CHIẾN DỊCH FLASH SALE KHÔNG ĐANG CHẠY";
        }

        String redisKey = Const.REDIS_KEY_PRODUCT_STOCK + productId;
        int remainingFlashSaleStock = readRedisStock(redisKey);

        product.setStock(product.getStock() + remainingFlashSaleStock);
        product.setFlashSale(false);
        productRepo.save(product);

        redisTemplate.delete(redisKey);
        orderService.clearLocalSoldOut(productId);

        return "Đã kết thúc flash sale cho productId=" + productId + ", hoàn " + remainingFlashSaleStock + " sản phẩm về MySQL.";
    }

    private int readRedisStock(String redisKey) {
        String value = redisTemplate.opsForValue().get(redisKey);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Redis stock is invalid for key " + redisKey, e);
        }
    }
}
