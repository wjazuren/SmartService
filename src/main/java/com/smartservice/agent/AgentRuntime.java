package com.smartservice.agent;

import com.smartservice.aftersale.AfterSaleService;
import com.smartservice.agent.ai.SpringAiAgentService;
import com.smartservice.agent.safety.ConfirmationService;
import com.smartservice.agent.tool.ToolContext;
import com.smartservice.agent.tool.ToolRegistry;
import com.smartservice.audit.AuditService;
import com.smartservice.order.OrderView;
import com.smartservice.rag.HybridRetrievalService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AgentRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);
    private final IntentRouter router;
    private final ToolRegistry tools;
    private final ConfirmationService confirmations;
    private final AuditService audit;
    private final com.smartservice.observability.TraceService traces;
    private final MeterRegistry metrics;
    private final ObjectProvider<SpringAiAgentService> aiProvider;

    public AgentRuntime(IntentRouter router, ToolRegistry tools, ConfirmationService confirmations,
                        AuditService audit, com.smartservice.observability.TraceService traces, MeterRegistry metrics,
                        ObjectProvider<SpringAiAgentService> aiProvider) {
        this.router = router;
        this.tools = tools;
        this.confirmations = confirmations;
        this.audit = audit;
        this.traces = traces;
        this.metrics = metrics;
        this.aiProvider = aiProvider;
    }

    public AgentResponse execute(long userId, AgentRequest request) {
        return execute(userId, request, ignored -> {});
    }

    public RuntimeStatus status() {
        SpringAiAgentService ai = aiProvider.getIfAvailable();
        return ai == null
                ? new RuntimeStatus(false, "RULE_ENGINE", null)
                : new RuntimeStatus(true, "SPRING_AI", ai.model());
    }

    public AgentResponse execute(long userId, AgentRequest request, Consumer<String> tokenConsumer) {
        SpringAiAgentService ai = aiProvider.getIfAvailable();
        Intent preflightIntent = router.route(request.message());
        boolean confirmedWrite = request.confirmationToken() != null && !request.confirmationToken().isBlank();
        boolean deterministicControlFlow = preflightIntent == Intent.TRANSFER_HUMAN
                || preflightIntent == Intent.CANCEL_ORDER;
        AgentResponse result;
        if (ai != null && !confirmedWrite && !deterministicControlFlow) {
            try {
                result = ai.execute(userId, request, tokenConsumer);
            } catch (RuntimeException ex) {
                log.warn("Spring AI execution failed; falling back to deterministic runtime: {}", ex.toString());
                metrics.counter("smartservice.agent.fallbacks", "model", ai.model()).increment();
                result = withExecution(executeRules(userId, request), AgentResponse.Execution.fallback(ai.model()));
            }
        } else {
            result = executeRules(userId, request);
        }

        traces.record(userId, result);
        metrics.counter("smartservice.agent.requests", "intent", result.intent().name(),
                "status", result.status().name(), "engine", result.execution().engine().name()).increment();
        metrics.timer("smartservice.agent.latency", "intent", result.intent().name(),
                "engine", result.execution().engine().name())
                .record(java.time.Duration.ofMillis(result.latencyMs()));
        return result;
    }

    private AgentResponse executeRules(long userId, AgentRequest request) {
        long started = System.nanoTime();
        String traceId = UUID.randomUUID().toString();
        String requestId = request.requestId() == null || request.requestId().isBlank()
                ? UUID.randomUUID().toString() : request.requestId();
        Intent intent = router.route(request.message());
        ToolContext readContext = new ToolContext(userId, traceId, requestId, false);
        List<ToolRegistry.ToolResult> calls = new ArrayList<>();
        List<HybridRetrievalService.RetrievalResult> sources = new ArrayList<>();

        return switch (intent) {
            case QUERY_LOGISTICS -> logistics(request, readContext, calls, sources, traceId, started);
            case APPLY_REFUND -> refund(request, readContext, calls, sources, traceId, started);
            case QUERY_POLICY -> policy(request, readContext, calls, sources, traceId, started);
            case CANCEL_ORDER -> cancellation(request, readContext, calls, sources, traceId, started);
            case TRANSFER_HUMAN -> response(traceId, intent, AgentResponse.Status.TRANSFERRED,
                    "已为你创建人工客服接入请求。当前演示环境会保留上下文，客服接入后无需重复描述问题。",
                    null, sources, calls, started);
            case GENERAL -> response(traceId, intent, AgentResponse.Status.ANSWERED,
                    "你好，我可以帮你查订单与物流、解释售后政策，或在二次确认后创建退货申请。你可以试试说：“耳机到哪了？”",
                    null, sources, calls, started);
        };
    }

    private AgentResponse logistics(AgentRequest request, ToolContext context, List<ToolRegistry.ToolResult> calls,
                                    List<HybridRetrievalService.RetrievalResult> sources, String traceId, long started) {
        ToolRegistry.ToolResult orderCall = tools.execute("query_order", context,
                Map.of("productKeyword", router.productKeyword(request.message())));
        calls.add(orderCall);
        OrderView order = (OrderView) orderCall.data();
        ToolRegistry.ToolResult logisticsCall = tools.execute("query_logistics", context, Map.of("orderId", order.id()));
        calls.add(logisticsCall);
        OrderView.LogisticsView logistics = (OrderView.LogisticsView) logisticsCall.data();
        String message = "%s（订单 %s）由%s承运，当前状态：%s。%s".formatted(
                order.productName(), order.orderNo(), logistics.carrier(), logistics.currentStatus(), logistics.latestEvent());
        return response(traceId, Intent.QUERY_LOGISTICS, AgentResponse.Status.ANSWERED, message,
                null, sources, calls, started);
    }

    @SuppressWarnings("unchecked")
    private AgentResponse refund(AgentRequest request, ToolContext context, List<ToolRegistry.ToolResult> calls,
                                 List<HybridRetrievalService.RetrievalResult> sources, String traceId, long started) {
        if (request.confirmationToken() != null && !request.confirmationToken().isBlank()) {
            ConfirmationService.PendingAction pending = confirmations.verify(request.confirmationToken(), context.userId(), Intent.APPLY_REFUND);
            ToolContext confirmedContext = new ToolContext(context.userId(), traceId, context.requestId(), true);
            ToolRegistry.ToolResult createCall = tools.execute("create_refund", confirmedContext, Map.of(
                    "orderId", pending.orderId(), "reason", pending.reason(),
                    "idempotencyKey", "confirm:" + request.confirmationToken()));
            calls.add(createCall);
            AfterSaleService.AfterSaleResult result = (AfterSaleService.AfterSaleResult) createCall.data();
            String message = result.idempotentReplay()
                    ? "该申请已处理过，售后单号仍为 %s，请勿重复提交。".formatted(result.requestNo())
                    : "退货申请已创建，售后单号 %s，当前状态为待审核。后续进度会通过站内通知同步。".formatted(result.requestNo());
            return response(traceId, Intent.APPLY_REFUND, AgentResponse.Status.ACTION_COMPLETED,
                    message, null, sources, calls, started);
        }

        ToolRegistry.ToolResult orderCall = tools.execute("query_order", context,
                Map.of("productKeyword", router.productKeyword(request.message())));
        calls.add(orderCall);
        OrderView order = (OrderView) orderCall.data();
        ToolRegistry.ToolResult policyCall = tools.execute("search_policy", context, Map.of("query", request.message()));
        calls.add(policyCall);
        sources.addAll((List<HybridRetrievalService.RetrievalResult>) policyCall.data());
        String summary = "确认对 %s（订单 %s，¥%s）发起退货退款".formatted(
                order.productName(), order.orderNo(), order.amount().toPlainString());
        ConfirmationService.Confirmation confirmation = confirmations.issue(context.userId(), Intent.APPLY_REFUND,
                order.id(), request.message(), summary);
        audit.record(traceId, context.userId(), "CREATE_REFUND", order.orderNo(), "CONFIRMATION_REQUIRED", summary);
        String policy = sources.isEmpty() ? "" : "根据《%s》，%s".formatted(sources.get(0).title(), sources.get(0).content());
        String message = "%s\n\n我找到了订单 %s。提交申请前需要你确认：%s。".formatted(policy, order.orderNo(), summary);
        return response(traceId, Intent.APPLY_REFUND, AgentResponse.Status.CONFIRMATION_REQUIRED,
                message, confirmation, sources, calls, started);
    }

    @SuppressWarnings("unchecked")
    private AgentResponse policy(AgentRequest request, ToolContext context, List<ToolRegistry.ToolResult> calls,
                                 List<HybridRetrievalService.RetrievalResult> sources, String traceId, long started) {
        ToolRegistry.ToolResult call = tools.execute("search_policy", context, Map.of("query", request.message()));
        calls.add(call);
        sources.addAll((List<HybridRetrievalService.RetrievalResult>) call.data());
        String message = sources.isEmpty() ? "知识库中暂时没有匹配规则，我可以为你转人工客服。"
                : "%s：%s".formatted(sources.get(0).title(), sources.get(0).content());
        return response(traceId, Intent.QUERY_POLICY, AgentResponse.Status.ANSWERED,
                message, null, sources, calls, started);
    }

    @SuppressWarnings("unchecked")
    private AgentResponse cancellation(AgentRequest request, ToolContext context, List<ToolRegistry.ToolResult> calls,
                                       List<HybridRetrievalService.RetrievalResult> sources, String traceId, long started) {
        ToolRegistry.ToolResult orderCall = tools.execute("query_order", context,
                Map.of("productKeyword", router.productKeyword(request.message())));
        calls.add(orderCall);
        OrderView order = (OrderView) orderCall.data();
        ToolRegistry.ToolResult policyCall = tools.execute("search_policy", context, Map.of("query", "订单取消规则"));
        calls.add(policyCall);
        sources.addAll((List<HybridRetrievalService.RetrievalResult>) policyCall.data());
        String message = order.status().equals("SHIPPED") || order.status().equals("DELIVERED")
                ? "订单 %s 已出库或签收，不能直接取消。你可以在签收后申请退货，我也可以现在帮你查看售后规则。".formatted(order.orderNo())
                : "订单取消属于敏感操作。当前版本已完成风险拦截，取消写入工具将在下一里程碑接入。";
        return response(traceId, Intent.CANCEL_ORDER, AgentResponse.Status.ANSWERED,
                message, null, sources, calls, started);
    }

    private AgentResponse response(String traceId, Intent intent, AgentResponse.Status status, String message,
                                   ConfirmationService.Confirmation confirmation,
                                   List<HybridRetrievalService.RetrievalResult> sources,
                                   List<ToolRegistry.ToolResult> calls, long started) {
        long latency = Math.max(1, (System.nanoTime() - started) / 1_000_000);
        return new AgentResponse(traceId, intent, status, message, confirmation,
                List.copyOf(sources), List.copyOf(calls), latency, AgentResponse.Execution.rules());
    }

    private AgentResponse withExecution(AgentResponse source, AgentResponse.Execution execution) {
        return new AgentResponse(source.traceId(), source.intent(), source.status(), source.message(),
                source.confirmation(), source.sources(), source.toolCalls(), source.latencyMs(), execution);
    }

    public record RuntimeStatus(boolean aiEnabled, String engine, String model) {}
}
