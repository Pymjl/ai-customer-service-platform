package com.aicsp.user.security;

import com.aicsp.user.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {
    private final JwtProperties properties;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
    }

    public String createToken(String userId, String tenantId, String username, List<String> roles) {
        long now = Instant.now().getEpochSecond();
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = b64("{\"sub\":\"" + esc(userId) + "\",\"tenantId\":\"" + esc(tenantId) + "\",\"username\":\"" + esc(username) + "\",\"roles\":\"" + esc(String.join(",", roles)) + "\",\"iat\":" + now + ",\"exp\":" + (now + properties.getTtlSeconds()) + "}");
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    public TokenClaims parse(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3 || !constantTimeEquals(sign(parts[0] + "." + parts[1]), parts[2])) {
            throw new IllegalArgumentException("invalid token");
        }
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        long exp = Long.parseLong(read(json, "exp"));
        if (Instant.now().getEpochSecond() > exp) {
            throw new IllegalArgumentException("expired token");
        }
        return new TokenClaims(readString(json, "sub"), readString(json, "tenantId"), readString(json, "username"), readString(json, "roles"), exp);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String esc(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String readString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return "";
        start += pattern.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }

    private static String read(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return "0";
        start += pattern.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return json.substring(start, end);
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigestLike.equals(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    public record TokenClaims(String userId, String tenantId, String username, String roles, long exp) {}

    private static final class MessageDigestLike {
        static boolean equals(byte[] a, byte[] b) {
            if (a.length != b.length) return false;
            int result = 0;
            for (int i = 0; i < a.length; i++) result |= a[i] ^ b[i];
            return result == 0;
        }
    }
}
