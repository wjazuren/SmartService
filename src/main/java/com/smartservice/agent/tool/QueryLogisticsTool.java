package com.smartservice.agent.tool;

import com.smartservice.common.BusinessException;
import com.smartservice.order.OrderRepository;
import com.smartservice.order.OrderView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QueryLogisticsTool implements AgentTool {
    private final OrderRepository orders;

    public QueryLogisticsTool(OrderRepository orders) {
        this.orders = orders;
    }

    @Override public String name() { return "query_logistics"; }
    @Override public String description() { return "查询当前用户订单的最新物流轨迹"; }
    @Override public RiskLevel riskLevel() { return RiskLevel.READ_ONLY; }

    @Override
    public Object execute(ToolContext context, Map<String, Object> arguments) {
        long orderId = ((Number) arguments.get("orderId")).longValue();
        OrderView order = orders.findOwnedById(context.userId(), orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "没有找到该订单"));
        if (order.logistics() == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "该订单暂时没有物流信息");
        }
        return order.logistics();
    }
}
