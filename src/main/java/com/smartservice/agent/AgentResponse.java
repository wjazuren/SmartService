package com.smartservice.agent;

import com.smartservice.agent.safety.ConfirmationService;
import com.smartservice.agent.tool.ToolRegistry;
import com.smartservice.rag.HybridRetrievalService;

import java.util.List;

public record AgentResponse(
        String traceId,
        Intent intent,
        Status status,
        String message,
        ConfirmationService.Confirmation confirmation,
        List<HybridRetrievalService.RetrievalResult> sources,
        List<ToolRegistry.ToolResult> toolCalls,
        long latencyMs,
        Execution execution) {
    public enum Status { ANSWERED, CONFIRMATION_REQUIRED, ACTION_COMPLETED, TRANSFERRED }

    public enum Engine { SPRING_AI, RULE_ENGINE }

    public record Execution(Engine engine, String model, boolean fallback, String fallbackReason) {
        public static Execution springAi(String model) {
            return new Execution(Engine.SPRING_AI, model, false, null);
        }

        public static Execution rules() {
            return new Execution(Engine.RULE_ENGINE, null, false, null);
        }

        public static Execution fallback(String model) {
            return new Execution(Engine.RULE_ENGINE, model, true, "模型调用失败或超时，已自动降级");
        }
    }
}
