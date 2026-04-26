package com.aicsp.biz.security;

import com.aicsp.biz.config.InternalSecurityProperties;
import com.aicsp.common.constant.HeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternalTokenFilter extends OncePerRequestFilter {

    private final InternalSecurityProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String expectedToken = properties.getBizToken();
        String actualToken = request.getHeader(HeaderConstants.X_INTERNAL_TOKEN);
        if (!StringUtils.hasText(expectedToken)) {
            log.warn("biz internal token is not configured");
            response.sendError(HttpStatus.FORBIDDEN.value(), "internal token is not configured");
            return;
        }
        if (!expectedToken.equals(actualToken)) {
            log.warn("invalid biz internal token for path {}", request.getRequestURI());
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "invalid internal token");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
