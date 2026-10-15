package com.smartservice.aftersale;

import com.smartservice.audit.AuditService;
import com.smartservice.common.BusinessException;
import com.smartservice.order.OrderRepository;
import com.smartservice.order.OrderView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.List;

@Service
public class AfterSaleService {
    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    private final AuditService audit;

    public AfterSaleService(JdbcTemplate jdbc, OrderRepository orders, AuditService audit) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.audit = audit;
    }

    @Transactional
    public AfterSaleResult createRefund(long userId, long orderId, String reason, String idempotencyKey, String traceId) {
        OrderView order = orders.findOwnedById(userId, orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "没有找到可申请售后的订单"));
        if (!(order.status().equals("DELIVERED") || order.status().equals("COMPLETED"))) {
            throw new BusinessException(HttpStatus.CONFLICT, "该订单当前状态不支持退货申请");
        }
        var existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            audit.record(traceId, userId, "CREATE_REFUND", order.orderNo(), "IDEMPOTENT_HIT", existing.requestNo());
            return existing;
        }

        String requestNo = "AS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        try {
            jdbc.update("insert into after_sale(request_no, order_id, user_id, reason, status, idempotency_key, created_at) values(?,?,?,?,?,?,?)",
                    requestNo, orderId, userId, reason, "PENDING_REVIEW", idempotencyKey, LocalDateTime.now());
        } catch (DuplicateKeyException ex) {
            return findByIdempotencyKey(idempotencyKey);
        }
        audit.record(traceId, userId, "CREATE_REFUND", order.orderNo(), "SUCCESS", requestNo);
        return new AfterSaleResult(requestNo, order.orderNo(), order.productName(), "PENDING_REVIEW", false);
    }

    public int countByUser(long userId) {
        Integer count = jdbc.queryForObject("select count(*) from after_sale where user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    public List<AfterSaleView> listByUser(long userId) {
        return jdbc.query("""
                        select a.request_no, o.order_no, o.product_name, o.amount, a.reason, a.status, a.created_at
                        from after_sale a join orders o on o.id = a.order_id
                        where a.user_id = ? order by a.created_at desc
                        """, (rs, row) -> new AfterSaleView(rs.getString("request_no"), rs.getString("order_no"),
                        rs.getString("product_name"), rs.getBigDecimal("amount"), rs.getString("reason"),
                        rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime()), userId);
    }

    private AfterSaleResult findByIdempotencyKey(String key) {
        return jdbc.query("""
                        select a.request_no, o.order_no, o.product_name, a.status
                        from after_sale a join orders o on o.id = a.order_id where a.idempotency_key = ?
                        """, (rs, row) -> new AfterSaleResult(rs.getString("request_no"), rs.getString("order_no"),
                        rs.getString("product_name"), rs.getString("status"), true), key)
                .stream().findFirst().orElse(null);
    }

    public record AfterSaleResult(String requestNo, String orderNo, String productName, String status,
                                  boolean idempotentReplay) {}
    public record AfterSaleView(String requestNo, String orderNo, String productName, java.math.BigDecimal amount,
                                String reason, String status, LocalDateTime createdAt) {}
}
