package com.smartservice.rag;

import com.smartservice.common.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final HybridRetrievalService retrieval;
    private final KnowledgeRepository repository;

    public KnowledgeController(HybridRetrievalService retrieval, KnowledgeRepository repository) {
        this.retrieval = retrieval;
        this.repository = repository;
    }

    @GetMapping("/documents")
    ApiResponse<List<KnowledgeDocument>> documents() {
        return ApiResponse.ok(repository.findAll());
    }

    @GetMapping("/search")
    ApiResponse<List<HybridRetrievalService.RetrievalResult>> search(
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int topK) {
        return ApiResponse.ok(retrieval.search(query, topK));
    }
}
