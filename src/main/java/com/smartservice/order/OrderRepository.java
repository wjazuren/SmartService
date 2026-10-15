package com.smartservice.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {
    private static final String SELECT = """
            select o.id, o.order_no, o.user_id, o.product_name, o.amount, o.status, o.paid_at,
                   l.carrier, l.tracking_no, l.current_status, l.latest_event, l.updated_at
            from orders o left join logistics l on l.order_id = o.id
            """;
    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OrderView> findByUser(long userId) {
        return jdbc.query(SELECT + " where o.user_id = ? order by o.paid_at desc", this::map, userId);
    }

    public Optional<OrderView> findLatestMatching(long userId, String productKeyword) {
        String keyword = productKeyword == null || productKeyword.isBlank() ? "%" : "%" + productKeyword + "%";
        return jdbc.query(SELECT + " where o.user_id = ? and o.product_name like ? order by o.paid_at desc limit 1",
                this::map, userId, keyword).stream().findFirst();
    }

    public Optional<OrderView> findOwnedById(long userId, long orderId) {
        return jdbc.query(SELECT + " where o.user_id = ? and o.id = ?", this::map, userId, orderId)
                .stream().findFirst();
    }

    private OrderView map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        OrderView.LogisticsView logistics = rs.getString("carrier") == null ? null : new OrderView.LogisticsView(
                rs.getString("carrier"), rs.getString("tracking_no"), rs.getString("current_status"),
                rs.getString("latest_event"), rs.getTimestamp("updated_at").toLocalDateTime());
        return new OrderView(rs.getLong("id"), rs.getString("order_no"), rs.getLong("user_id"),
                rs.getString("product_name"), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getTimestamp("paid_at").toLocalDateTime(), logistics);
    }
}
