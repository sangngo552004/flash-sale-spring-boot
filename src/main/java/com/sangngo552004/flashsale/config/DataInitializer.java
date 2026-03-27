package com.sangngo552004.flashsale.config;

import com.sangngo552004.flashsale.entity.Product;
import com.sangngo552004.flashsale.repository.ProductRepository;
import com.sangngo552004.flashsale.util.Const;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(String... args) {
        // Chỉ chạy khởi tạo dữ liệu MỘT LẦN DUY NHẤT.
        // Kiểm tra xem đã có dữ liệu trong DB chưa, nếu chưa có thì mới tạo.
        if (productRepository.count() == 0) {
            System.out.println("No products found. Initializing sample data...");

            // Product 1: Normal Sale Product
            long productId1 = 1L;
            String productName1 = "iPhone 16 Pro Max";
            int initialStock1 = 10;
            Product product1 = new Product(productId1, productName1, initialStock1, false, 0L);
            productRepository.save(product1);

            // Product 2: Flash Sale Product
            long productId2 = 2L;
            String productName2 = "Samsung Galaxy S24 Ultra";
            int initialStock2 = 10;
            Product product2 = new Product(productId2, productName2, initialStock2, true, 0L);
            productRepository.save(product2);

            // Đồng bộ stock của Flash Sale product lên Redis
            String redisKey = Const.REDIS_KEY_PRODUCT_STOCK + productId2;
            redisTemplate.opsForValue().set(redisKey, String.valueOf(initialStock2));
            System.out.println("Initialized Redis stock for Flash Sale Product ID " + productId2 + " to " + initialStock2);
            
            System.out.println("Sample data initialization completed.");
        } else {
            System.out.println("Products already exist in the database. Skipping data initialization.");
        }
    }
}
