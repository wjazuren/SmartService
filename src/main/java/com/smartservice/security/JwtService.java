package com.smartservice.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartservice.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final Duration ttl;

    public JwtService(ObjectMapper objectMapper,
                      @Value("${smartservice.jwt.secret}") String secret,
                      @Value("${smartservice.jwt.ttl:8h}") Duration ttl) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
    }

    public String issue(UserPrincipal user) {
        try {
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", user.username());
            claims.put("uid", user.id());
            claims.put("name", user.displayName());
            claims.put("role", user.role());
            claims.put("iat", Instant.now().getEpochSecond());
            claims.put("exp", Instant.now().plus(ttl).getEpochSecond());
            String payload = encode(objectMapper.writeValueAsBytes(claims));
            String content = header + "." + payload;
            return content + "." + encode(sign(content));
        } catch (Exception ex) {
            throw new IllegalStateException("无法签发登录令牌", ex);
        }
    }

    public UserPrincipal parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw invalid();
            }
            String content = parts[0] + "." + parts[1];
            if (!java.security.MessageDigest.isEqual(sign(content), DECODER.decode(parts[2]))) {
                throw invalid();
            }
            Map<String, Object> claims = objectMapper.readValue(DECODER.decode(parts[1]), new TypeReference<>() {});
            long expiresAt = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= expiresAt) {
                throw invalid();
            }
            return new UserPrincipal(
                    ((Number) claims.get("uid")).longValue(),
                    (String) claims.get("sub"),
                    (String) claims.get("name"),
                    (String) claims.get("role"));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }

    private BusinessException invalid() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "登录状态已失效");
    }
}
