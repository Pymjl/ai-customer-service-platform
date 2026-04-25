package com.aicsp.gateway.client;

import com.aicsp.gateway.config.GatewayModuleProperties;
import com.aicsp.gateway.dto.IntrospectionResponse;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class IdentityClient {
    private final WebClient webClient;
    private final GatewayModuleProperties properties;

    public IdentityClient(WebClient.Builder builder, GatewayModuleProperties properties) {
        this.webClient = builder.build();
        this.properties = properties;
    }

    public Mono<IntrospectionResponse> introspect(String authorizationHeader) {
        return webClient.post().uri(properties.getIntrospectUri()).header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve().bodyToMono(String.class).map(this::parseProfile).onErrorResume(error -> Mono.just(inactive()));
    }

    public Mono<Boolean> authorize(String authorizationHeader, String method, String path) {
        return webClient.post().uri(properties.getAuthorizeUri()).header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .bodyValue(Map.of("httpMethod", method, "path", path))
                .retrieve().bodyToMono(String.class).map(body -> body.contains("\"allowed\":true")).onErrorReturn(false);
    }

    private IntrospectionResponse parseProfile(String body) {
        IntrospectionResponse response = new IntrospectionResponse();
        response.setActive(body.contains("\"active\":true"));
        response.setUserId(read(body, "userId"));
        response.setTenantId(read(body, "tenantId"));
        response.setUsername(read(body, "username"));
        response.setRoles(read(body, "roles"));
        return response;
    }

    private String read(String body, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = body.indexOf(pattern);
        if (start < 0) return "";
        start += pattern.length();
        int end = body.indexOf('"', start);
        return end < 0 ? "" : body.substring(start, end);
    }

    private IntrospectionResponse inactive() {
        IntrospectionResponse response = new IntrospectionResponse();
        response.setActive(false);
        return response;
    }
}
