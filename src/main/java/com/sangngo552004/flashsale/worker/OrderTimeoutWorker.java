package com.sangngo552004.flashsale.worker;

import com.sangngo552004.flashsale.entity.Order;
import com.sangngo552004.flashsale.repository.OrderRepository;
import com.sangngo552004.flashsale.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderTimeoutWorker {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cancelExpiredOrders() {
        List<Order> expiredOrders = orderRepository.findExpiredPendingOrdersForUpdate(LocalDateTime.now());
        if (expiredOrders.isEmpty()) {
            return;
        }

        for (Order order : expiredOrders) {
            order.setStatus(Order.STATUS_CANCELLED);
            orderService.restoreStockForExpiredOrder(order.getProductId());
        }
    }
}
