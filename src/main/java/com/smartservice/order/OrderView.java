package com.smartservice.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderView(
        long id,
        String orderNo,
        long userId,
        String productName,
        BigDecimal amount,
        String status,
        LocalDateTime paidAt,
        LogisticsView logistics) {

    public record LogisticsView(String carrier, String trackingNo, String currentStatus,
                                String latestEvent, LocalDateTime updatedAt) {}
}
