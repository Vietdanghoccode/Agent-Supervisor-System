package com.viettel.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtIdentityFilter implements WebFilter {
    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLE = "X-User-Role";
    private final byte[] signingKey;

    public JwtIdentityFilter(@Value("${jwt.secret}") String secret) {
        this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        boolean protectedInvite = path.equals("/api/auth/invites")
                || (path.startsWith("/api/auth/invites/") && path.endsWith("/resend"));
        if (!path.startsWith("/conversations") && !path.startsWith("/conversation/agent") && !protectedInvite) {
            return chain.filter(stripIdentity(exchange));
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) return unauthorized(exchange);
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(signingKey))
                    .build().parseClaimsJws(authorization.substring(7)).getBody();
            Object userId = claims.get("userId");
            Object role = claims.get("role");
            if (userId == null || role == null) return unauthorized(exchange);
            ServerWebExchange authenticated = exchange.mutate().request(builder -> builder.headers(headers -> {
                headers.remove(USER_ID);
                headers.remove(USER_ROLE);
                headers.set(USER_ID, userId.toString());
                headers.set(USER_ROLE, role.toString().toLowerCase());
            })).build();
            return chain.filter(authenticated);
        } catch (RuntimeException exception) {
            return unauthorized(exchange);
        }
    }

    private ServerWebExchange stripIdentity(ServerWebExchange exchange) {
        return exchange.mutate().request(builder -> builder.headers(headers -> {
            headers.remove(USER_ID);
            headers.remove(USER_ROLE);
        })).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
