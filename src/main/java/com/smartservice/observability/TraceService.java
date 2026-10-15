package com.smartservice.observability;

import com.smartservice.agent.AgentResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class TraceService {
    private final JdbcTemplate jdbc;

    public TraceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(long userId, AgentResponse response) {
        jdbc.update("insert into agent_trace(trace_id,user_id,intent,status,tool_calls,retrieval_docs,latency_ms,created_at) values(?,?,?,?,?,?,?,?)",
                response.traceId(), userId, response.intent().name(), response.status().name(),
                response.toolCalls().size(), response.sources().size(), response.latencyMs(), LocalDateTime.now());
    }

    public TraceSummary summary(long userId) {
        List<TraceRow> rows = jdbc.query("select intent,status,tool_calls,retrieval_docs,latency_ms,created_at from agent_trace where user_id=? order by created_at desc limit 500",
                (rs, row) -> new TraceRow(rs.getString("intent"), rs.getString("status"), rs.getInt("tool_calls"),
                        rs.getInt("retrieval_docs"), rs.getLong("latency_ms"), rs.getTimestamp("created_at").toLocalDateTime()), userId);
        long total = rows.size();
        double averageLatency = rows.stream().mapToLong(TraceRow::latencyMs).average().orElse(0);
        List<Long> sorted = rows.stream().map(TraceRow::latencyMs).sorted(Comparator.naturalOrder()).toList();
        long p95 = sorted.isEmpty() ? 0 : sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1));
        int toolCalls = rows.stream().mapToInt(TraceRow::toolCalls).sum();
        long completed = rows.stream().filter(it -> it.status().equals("ACTION_COMPLETED")).count();
        long confirmations = rows.stream().filter(it -> it.status().equals("CONFIRMATION_REQUIRED")).count();
        return new TraceSummary(total, Math.round(averageLatency * 10d) / 10d, p95, toolCalls, completed, confirmations, rows.stream().limit(12).toList());
    }

    public record TraceSummary(long requests, double averageLatencyMs, long p95LatencyMs, int toolCalls,
                               long completedActions, long confirmationsIntercepted, List<TraceRow> recent) {}
    public record TraceRow(String intent, String status, int toolCalls, int retrievalDocs,
                           long latencyMs, LocalDateTime createdAt) {}
}
