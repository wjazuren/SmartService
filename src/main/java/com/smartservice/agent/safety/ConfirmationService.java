package com.smartservice.agent.safety;

import com.smartservice.agent.Intent;
import com.smartservice.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConfirmationService {
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingAction> pending = new ConcurrentHashMap<>();
    private final Duration ttl;

    public ConfirmationService(@Value("${smartservice.confirmation.ttl:5m}") Duration ttl) {
        this.ttl = ttl;
    }

    public Confirmation issue(long userId, Intent intent, long orderId, String reason, String summary) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(ttl);
        pending.put(token, new PendingAction(userId, intent, orderId, reason, expiresAt));
        return new Confirmation(token, summary, expiresAt);
    }

    public PendingAction verify(String token, long userId, Intent intent) {
        PendingAction action = pending.get(token);
        if (action == null || action.expiresAt().isBefore(Instant.now())) {
            pending.remove(token);
            throw new BusinessException(HttpStatus.GONE, "确认已过期，请重新发起操作");
        }
        if (action.userId() != userId || action.intent() != intent) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "确认信息与当前用户或操作不匹配");
        }
        return action;
    }

    public record PendingAction(long userId, Intent intent, long orderId, String reason, Instant expiresAt) {}
    public record Confirmation(String token, String summary, Instant expiresAt) {}
}
