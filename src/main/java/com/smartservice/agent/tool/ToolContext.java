package com.smartservice.agent.tool;

public record ToolContext(long userId, String traceId, String requestId, boolean confirmed) {
}
