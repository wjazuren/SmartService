package com.smartservice.agent.tool;

import java.util.Map;

public interface AgentTool {
    String name();
    String description();
    RiskLevel riskLevel();
    Object execute(ToolContext context, Map<String, Object> arguments);
}
