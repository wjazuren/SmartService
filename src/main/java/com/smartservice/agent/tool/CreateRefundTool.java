package com.smartservice.agent.tool;

import com.smartservice.aftersale.AfterSaleService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CreateRefundTool implements AgentTool {
    private final AfterSaleService afterSale;

    public CreateRefundTool(AfterSaleService afterSale) {
        this.afterSale = afterSale;
    }

    @Override public String name() { return "create_refund"; }
    @Override public String description() { return "为已确认的订单创建退货退款申请"; }
    @Override public RiskLevel riskLevel() { return RiskLevel.HIGH_RISK; }

    @Override
    public Object execute(ToolContext context, Map<String, Object> arguments) {
        long orderId = ((Number) arguments.get("orderId")).longValue();
        String reason = String.valueOf(arguments.getOrDefault("reason", "用户通过智能客服申请退货"));
        String key = String.valueOf(arguments.get("idempotencyKey"));
        return afterSale.createRefund(context.userId(), orderId, reason, key, context.traceId());
    }
}
