package com.smartservice.audit;

import com.smartservice.common.ApiResponse;
import com.smartservice.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audits")
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    ApiResponse<List<AuditService.AuditEntry>> recent(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(auditService.recent(user.id()));
    }
}
