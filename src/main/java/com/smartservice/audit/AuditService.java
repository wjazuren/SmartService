package com.smartservice.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String traceId, long userId, String action, String resourceId, String result, String detail) {
        jdbc.update("insert into audit_log(trace_id,user_id,action,resource_id,result,detail,created_at) values(?,?,?,?,?,?,?)",
                traceId, userId, action, resourceId, result, detail, LocalDateTime.now());
    }

    public List<AuditEntry> recent(long userId) {
        return jdbc.query("select trace_id, action, resource_id, result, detail, created_at from audit_log where user_id=? order by created_at desc limit 20",
                (rs, row) -> new AuditEntry(rs.getString("trace_id"), rs.getString("action"),
                        rs.getString("resource_id"), rs.getString("result"), rs.getString("detail"),
                        rs.getTimestamp("created_at").toLocalDateTime()), userId);
    }

    public record AuditEntry(String traceId, String action, String resourceId, String result,
                             String detail, LocalDateTime createdAt) {}
}
