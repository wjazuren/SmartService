package com.smartservice.agent.tool;

import com.smartservice.common.BusinessException;
import com.smartservice.order.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QueryOrderTool implements AgentTool {
    private final OrderRepository orders;

    public QueryOrderTool(OrderRepository orders) {
        this.orders = orders;
    }

    @Override public String name() { return "query_order"; }
    @Override public String description() { return "按当前登录用户和商品关键词查询最近订单"; }
    @Override public RiskLevel riskLevel() { return RiskLevel.READ_ONLY; }

    @Override
    public Object execute(ToolContext context, Map<String, Object> arguments) {
        String keyword = String.valueOf(arguments.getOrDefault("productKeyword", ""));
        return orders.findLatestMatching(context.userId(), keyword)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "没有找到匹配的订单"));
    }
}
