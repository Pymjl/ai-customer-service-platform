package com.aicsp.user.config;

import com.aicsp.common.constant.HeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    private final UserSecurityProperties securityProperties;

    public SecurityConfig(UserSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/oauth2/**", "/actuator/health").permitAll()
                        .requestMatchers(this::hasInternalToken).permitAll()
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private boolean hasInternalToken(HttpServletRequest request) {
        String token = request.getHeader(HeaderConstants.X_INTERNAL_TOKEN);
        return token != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                securityProperties.getInternalToken().getBytes(StandardCharsets.UTF_8));
    }
}
