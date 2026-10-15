package com.smartservice.order;

import com.smartservice.common.ApiResponse;
import com.smartservice.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderRepository repository;

    public OrderController(OrderRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    ApiResponse<List<OrderView>> list(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(repository.findByUser(user.id()));
    }
}
