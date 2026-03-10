package com.sangngo552004.flashsale.controller;

import com.sangngo552004.flashsale.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flashsale")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/buy")
    public String buy(@RequestParam String userId,
                      @RequestParam Long productId,
                      @RequestHeader(value = "X-Request-ID") String requestId) {

        return orderService.purchase(userId, productId, requestId);
    }
}
