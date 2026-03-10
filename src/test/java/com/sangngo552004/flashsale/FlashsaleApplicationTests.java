package com.sangngo552004.flashsale;

import com.sangngo552004.flashsale.service.OrderService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
class FlashsaleApplicationTests {

    @Test
    void contextLoads() {
    }
    @Autowired
    private OrderService orderService;

    // SCENARIO 1: 100 người tranh nhau 10 cái iPhone
    @Test
    void testConcurrency_Overselling() throws InterruptedException {
        int numberOfUsers = 100;
        int stockAvailable = 10;
        Long productId = 1L; // Đảm bảo Redis đã set stock = 10

        // Bộ đếm số người mua thành công
        AtomicInteger successCount = new AtomicInteger(0);

        // Giả lập 100 luồng chạy song song
        ExecutorService executor = Executors.newFixedThreadPool(numberOfUsers);
        CountDownLatch latch = new CountDownLatch(numberOfUsers);

        System.out.println("=== BẮT ĐẦU FLASH SALE ===");

        for (int i = 0; i < numberOfUsers; i++) {
            String userId = "User-" + i;
            executor.submit(() -> {
                try {
                    // Mỗi user có request ID riêng
                    String requestId = UUID.randomUUID().toString();
                    String result = orderService.purchase(userId, productId, requestId);

                    if (result.contains("THÀNH CÔNG")) {
                        System.out.println(userId + ": Mua được!");
                        successCount.incrementAndGet();
                    } else {
                        // System.out.println(userId + ": " + result); // In ra sẽ rất rối
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // Chờ 100 người chạy xong
        executor.shutdown();

        System.out.println("=== KẾT THÚC FLASH SALE ===");
        System.out.println("Tổng số người mua được: " + successCount.get());

        // Assert: Không bao giờ được bán quá 10 cái
        Assertions.assertTrue(successCount.get() <= stockAvailable, "TOANG! Bán lố hàng rồi!");
        // Assert: Phải bán được ít nhất 1 cái (hệ thống ko bị chết)
        Assertions.assertTrue(successCount.get() > 0, "Hệ thống lỗi, không ai mua được gì.");
    }

    // SCENARIO 2: 1 người spam nút mua 10 lần (Idempotency)
    @Test
    void testIdempotency_SpamClick() throws InterruptedException {
        String userId = "Spammer-002";
        Long productId = 1L;
        String fixedRequestId = "req-unique-1234567"; // Cố định Request ID

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    String result = orderService.purchase(userId, productId, fixedRequestId);
                    if (result.contains("THÀNH CÔNG")) successCount.incrementAndGet();
                    if (result.contains("ĐÃ ĐẶT ĐƠN NÀY RỒI")) duplicateCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("Thành công: " + successCount.get());
        System.out.println("Bị chặn trùng: " + duplicateCount.get());

        // Assert: Chỉ CÓ ĐÚNG 1 request thành công
        Assertions.assertEquals(1, successCount.get());
        // Assert: Các request còn lại phải bị báo lỗi trùng
        Assertions.assertTrue(duplicateCount.get() >= 9); // Redis có thể trả lỗi khác nếu hết hàng, nhưng ít nhất phải chặn dc đa số.
    }
}
