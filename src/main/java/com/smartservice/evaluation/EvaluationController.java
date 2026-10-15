package com.smartservice.evaluation;

import com.smartservice.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/evaluation")
public class EvaluationController {
    private final EvaluationService evaluation;

    public EvaluationController(EvaluationService evaluation) {
        this.evaluation = evaluation;
    }

    @GetMapping
    ApiResponse<EvaluationService.EvaluationReport> run() {
        return ApiResponse.ok(evaluation.run());
    }
}
