package com.aicsp.user.security;

import com.aicsp.user.config.JwtProperties;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {
    @Test
    void shouldCreateAndParseToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("01234567890123456789012345678901");
        JwtTokenService service = new JwtTokenService(properties);

        String token = service.createToken("U1", "default", "admin", List.of("ADMIN", "USER"));
        JwtTokenService.TokenClaims claims = service.parse(token);

        Assertions.assertEquals("U1", claims.userId());
        Assertions.assertEquals("default", claims.tenantId());
        Assertions.assertEquals("admin", claims.username());
        Assertions.assertEquals("ADMIN,USER", claims.roles());
    }

    @Test
    void shouldRejectInvalidToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("01234567890123456789012345678901");
        JwtTokenService service = new JwtTokenService(properties);

        Assertions.assertThrows(IllegalArgumentException.class, () -> service.parse("bad.token.value"));
    }
}
