package com.smartservice.observability;

import com.smartservice.common.ApiResponse;
import com.smartservice.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observability")
public class TraceController {
    private final TraceService traces;

    public TraceController(TraceService traces) {
        this.traces = traces;
    }

    @GetMapping("/summary")
    ApiResponse<TraceService.TraceSummary> summary(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(traces.summary(user.id()));
    }
}
