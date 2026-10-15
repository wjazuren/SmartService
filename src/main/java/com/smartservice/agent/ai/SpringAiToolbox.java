package com.smartservice.agent.ai;

import com.smartservice.agent.Intent;
import com.smartservice.agent.safety.ConfirmationService;
import com.smartservice.agent.tool.RiskLevel;
import com.smartservice.agent.tool.ToolContext;
import com.smartservice.agent.tool.ToolRegistry;
import com.smartservice.audit.AuditService;
import com.smartservice.order.OrderView;
import com.smartservice.rag.HybridRetrievalService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-request Spring AI tools. The object carries the authenticated user context, so model supplied
 * arguments can never select another user's data. Only preparation is exposed for write intents;
 * the actual create_refund tool remains behind the existing confirmation guard.
 */
public final class SpringAiToolbox {
    private final ToolRegistry tools;
    private final ConfirmationService confirmations;
    private final AuditService audit;
    private final ToolContext context;
    private final String originalMessage;
    private final List<ToolRegistry.ToolResult> calls = new ArrayList<>();
    private final List<HybridRetrievalService.RetrievalResult> sources = new ArrayList<>();
    private ConfirmationService.Confirmation confirmation;
    private Intent observedIntent = Intent.GENERAL;

    public SpringAiToolbox(ToolRegistry tools, ConfirmationService confirmations, AuditService audit,
                           long userId, String traceId, String requestId, String originalMessage) {
        this.tools = tools;
        this.confirmations = confirmations;
        this.audit = audit;
        this.context = new ToolContext(userId, traceId, requestId, false);
        this.originalMessage = originalMessage;
    }

    @Tool(name = "query_order", description = "按当前已登录用户和商品关键词查询最近订单。查询物流或准备售后前必须先调用。")
    public OrderView queryOrder(
            @ToolParam(description = "用户提到的商品关键词；未提到商品时传空字符串") String productKeyword) {
        ToolRegistry.ToolResult result = execute("query_order", Map.of("productKeyword", safe(productKeyword)));
        return (OrderView) result.data();
    }

    @Tool(name = "query_logistics", description = "查询当前登录用户指定订单的最新物流轨迹。orderId 必须来自 query_order。")
    public OrderView.LogisticsView queryLogistics(
            @ToolParam(description = "query_order 返回的订单 ID") long orderId) {
        observedIntent = Intent.QUERY_LOGISTICS;
        ToolRegistry.ToolResult result = execute("query_logistics", Map.of("orderId", orderId));
        return (OrderView.LogisticsView) result.data();
    }

    @SuppressWarnings("unchecked")
    @Tool(name = "search_policy", description = "从售后、退款、物流、优惠与商品政策知识库检索依据。回答政策问题前必须调用。")
    public List<HybridRetrievalService.RetrievalResult> searchPolicy(
            @ToolParam(description = "完整、具体的用户问题") String query) {
        observedIntent = Intent.QUERY_POLICY;
        ToolRegistry.ToolResult result = execute("search_policy", Map.of("query", safe(query)));
        List<HybridRetrievalService.RetrievalResult> found =
                (List<HybridRetrievalService.RetrievalResult>) result.data();
        addSources(found);
        return found;
    }

    @SuppressWarnings("unchecked")
    @Tool(name = "prepare_refund", description = "查询当前用户订单和售后政策，并生成退货退款二次确认。只准备申请，不会创建或提交售后单。")
    public RefundPreparation prepareRefund(
            @ToolParam(description = "用户提到的商品关键词；未提到商品时传空字符串") String productKeyword,
            @ToolParam(description = "用户描述的退货退款原因") String reason) {
        long started = System.nanoTime();
        observedIntent = Intent.APPLY_REFUND;
        ToolRegistry.ToolResult orderCall = execute("query_order", Map.of("productKeyword", safe(productKeyword)));
        OrderView order = (OrderView) orderCall.data();
        ToolRegistry.ToolResult policyCall = execute("search_policy", Map.of("query", originalMessage));
        List<HybridRetrievalService.RetrievalResult> found =
                (List<HybridRetrievalService.RetrievalResult>) policyCall.data();
        addSources(found);

        String actualReason = safe(reason).isBlank() ? originalMessage : safe(reason);
        String summary = "确认对 %s（订单 %s，¥%s）发起退货退款".formatted(
                order.productName(), order.orderNo(), order.amount().toPlainString());
        confirmation = confirmations.issue(context.userId(), Intent.APPLY_REFUND,
                order.id(), actualReason, summary);
        audit.record(context.traceId(), context.userId(), "CREATE_REFUND", order.orderNo(),
                "CONFIRMATION_REQUIRED", summary);
        RefundPreparation preparation = new RefundPreparation(order, summary,
                found.isEmpty() ? null : found.get(0), confirmation.expiresAt().toString());
        calls.add(new ToolRegistry.ToolResult("prepare_refund", RiskLevel.READ_ONLY, preparation,
                elapsedMillis(started)));
        return preparation;
    }

    public Intent observedIntent() {
        return observedIntent;
    }

    public ConfirmationService.Confirmation confirmation() {
        return confirmation;
    }

    public List<ToolRegistry.ToolResult> calls() {
        return List.copyOf(calls);
    }

    public List<HybridRetrievalService.RetrievalResult> sources() {
        return List.copyOf(sources);
    }

    public boolean called(String name) {
        return calls.stream().anyMatch(call -> call.tool().equals(name));
    }

    private ToolRegistry.ToolResult execute(String name, Map<String, Object> arguments) {
        ToolRegistry.ToolResult result = tools.execute(name, context, arguments);
        calls.add(result);
        return result;
    }

    private void addSources(List<HybridRetrievalService.RetrievalResult> found) {
        for (HybridRetrievalService.RetrievalResult source : found) {
            if (sources.stream().noneMatch(existing -> existing.documentId() == source.documentId())) {
                sources.add(source);
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private long elapsedMillis(long started) {
        return Math.max(1, (System.nanoTime() - started) / 1_000_000);
    }

    public record RefundPreparation(OrderView order, String confirmationSummary,
                                    HybridRetrievalService.RetrievalResult primaryPolicy,
                                    String confirmationExpiresAt) {}
}
