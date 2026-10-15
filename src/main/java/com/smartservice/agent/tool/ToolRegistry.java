package com.smartservice.agent.tool;

import com.smartservice.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> toolList) {
        Map<String, AgentTool> indexed = new LinkedHashMap<>();
        toolList.forEach(tool -> indexed.put(tool.name(), tool));
        this.tools = Map.copyOf(indexed);
    }

    public ToolResult execute(String name, ToolContext context, Map<String, Object> arguments) {
        AgentTool tool = tools.get(name);
        if (tool == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "未知工具：" + name);
        if (tool.riskLevel() == RiskLevel.HIGH_RISK && !context.confirmed()) {
            throw new BusinessException(HttpStatus.PRECONDITION_REQUIRED, "敏感操作必须经过用户二次确认");
        }
        long started = System.nanoTime();
        Object data = tool.execute(context, arguments);
        return new ToolResult(name, tool.riskLevel(), data, (System.nanoTime() - started) / 1_000_000);
    }

    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(it -> new ToolDefinition(it.name(), it.description(), it.riskLevel())).toList();
    }

    public record ToolResult(String tool, RiskLevel riskLevel, Object data, long latencyMs) {}
    public record ToolDefinition(String name, String description, RiskLevel riskLevel) {}
}
