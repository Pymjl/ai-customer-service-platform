package com.aicsp.user.security;

import com.aicsp.user.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RefreshTokenService {
    private static final String TOKEN_KEY_PREFIX = "aicsp:auth:refresh:";
    private static final String INDEX_KEY_PREFIX = "aicsp:auth:refresh:index:";

    private final JwtProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(JwtProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    public String create(String userId) {
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        byte[] random = new byte[properties.getRefreshTokenBytes()];
        secureRandom.nextBytes(random);
        String token = tokenId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String tokenKey = tokenKey(tokenId);
        Duration ttl = Duration.ofSeconds(properties.getRefreshTtlSeconds());

        redisTemplate.opsForValue().set(tokenKey, userId + "." + sha256(token), ttl);
        redisTemplate.opsForSet().add(indexKey(userId), tokenKey);
        redisTemplate.expire(indexKey(userId), ttl);
        return token;
    }

    public String consume(String refreshToken) {
        ParsedRefreshToken parsed = parse(refreshToken);
        String tokenKey = tokenKey(parsed.tokenId());
        String storedHash = redisTemplate.opsForValue().getAndDelete(tokenKey);
        StoredRefreshToken stored = parseStored(storedHash);
        if (stored != null) {
            redisTemplate.opsForSet().remove(indexKey(stored.userId()), tokenKey);
        }
        if (stored == null || !constantTimeEquals(stored.hash(), sha256(refreshToken))) {
            if (stored != null) {
                revokeAll(stored.userId());
            }
            throw new IllegalArgumentException("invalid or expired refresh token");
        }
        return stored.userId();
    }

    public void revoke(String refreshToken) {
        ParsedRefreshToken parsed = parse(refreshToken);
        String tokenKey = tokenKey(parsed.tokenId());
        String storedHash = redisTemplate.opsForValue().getAndDelete(tokenKey);
        StoredRefreshToken stored = parseStored(storedHash);
        if (stored != null) {
            redisTemplate.opsForSet().remove(indexKey(stored.userId()), tokenKey);
        }
    }

    public void revokeAll(String userId) {
        String indexKey = indexKey(userId);
        Set<String> tokenKeys = redisTemplate.opsForSet().members(indexKey);
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
        redisTemplate.delete(indexKey);
    }

    private ParsedRefreshToken parse(String refreshToken) {
        String[] parts = refreshToken == null ? new String[0] : refreshToken.split("\\.");
        if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
            throw new IllegalArgumentException("invalid refresh token");
        }
        return new ParsedRefreshToken(parts[0]);
    }

    private StoredRefreshToken parseStored(String stored) {
        if (!StringUtils.hasText(stored)) {
            return null;
        }
        int separator = stored.indexOf('.');
        if (separator <= 0 || separator == stored.length() - 1) {
            return null;
        }
        return new StoredRefreshToken(stored.substring(0, separator), stored.substring(separator + 1));
    }

    private String tokenKey(String tokenId) {
        return TOKEN_KEY_PREFIX + tokenId;
    }

    private String indexKey(String userId) {
        return INDEX_KEY_PREFIX + userId;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private record ParsedRefreshToken(String tokenId) {
    }

    private record StoredRefreshToken(String userId, String hash) {
    }
}
