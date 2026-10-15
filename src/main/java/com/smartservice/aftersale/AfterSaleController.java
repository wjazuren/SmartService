package com.smartservice.aftersale;

import com.smartservice.common.ApiResponse;
import com.smartservice.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/after-sales")
public class AfterSaleController {
    private final AfterSaleService afterSaleService;

    public AfterSaleController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    @GetMapping
    ApiResponse<List<AfterSaleService.AfterSaleView>> list(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(afterSaleService.listByUser(user.id()));
    }
}
