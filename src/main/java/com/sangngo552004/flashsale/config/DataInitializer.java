package com.sangngo552004.flashsale.config;

import com.sangngo552004.flashsale.entity.Product;
import com.sangngo552004.flashsale.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        long productId = 1L;
        String productName = "iPhone 16 Pro Max";
        int initialStock = 10;

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            product = new Product(productId, productName, initialStock, false, 0L);
            productRepository.save(product);
            return;
        }

        if (product.getVersion() == null) {
            product.setVersion(0L);
            productRepository.save(product);
        }
    }
}
