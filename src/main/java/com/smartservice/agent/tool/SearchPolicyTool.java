package com.smartservice.agent.tool;

import com.smartservice.rag.HybridRetrievalService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SearchPolicyTool implements AgentTool {
    private final HybridRetrievalService retrieval;

    public SearchPolicyTool(HybridRetrievalService retrieval) {
        this.retrieval = retrieval;
    }

    @Override public String name() { return "search_policy"; }
    @Override public String description() { return "从售后、物流与商品政策知识库进行混合检索"; }
    @Override public RiskLevel riskLevel() { return RiskLevel.READ_ONLY; }

    @Override
    public Object execute(ToolContext context, Map<String, Object> arguments) {
        return retrieval.search(String.valueOf(arguments.get("query")), 3);
    }
}
