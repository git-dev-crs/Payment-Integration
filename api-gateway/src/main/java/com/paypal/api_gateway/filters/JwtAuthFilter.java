package com.paypal.api_gateway.filters;

import com.paypal.api_gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;


@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/signup",
            "/auth/login"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain){
        String path = exchange.getRequest().getPath().value();
        String normalizedPath = path.replaceAll("/+$", "");

        System.out.println("Incoming request path: " + normalizedPath);

        // Allow public paths
        if (PUBLIC_PATHS.contains(normalizedPath) || normalizedPath.startsWith("/auth/")) {
            System.out.println("Public path, skipping auth check: " + normalizedPath);
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        System.out.println("Authorization header: " + authHeader);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("❌ Missing or invalid Authorization header");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = JwtUtil.validateToken(token);

            System.out.println("✅ Token validated. Claims:");
            System.out.println("   userId=" + claims.get("userId"));
            System.out.println("   email=" + claims.getSubject());
            System.out.println("   role=" + claims.get("role"));

            // Mutate request with claims
            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(exchange.getRequest().mutate()
                            .header("X-User-Email", claims.getSubject())
                            .header("X-User-Id", String.valueOf(claims.get("userId")))
                            .header("X-User-Role", (String) claims.get("role"))
                            .build())
                    .build();

            return chain.filter(mutatedExchange);

        } catch (Exception e) {
            System.out.println("❌ JWT validation failed: " + e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
