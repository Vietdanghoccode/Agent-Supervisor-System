package com.viettel.gateway;

import com.viettel.gateway.config.JwtIdentityFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtIdentityFilterTest {
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final JwtIdentityFilter filter = new JwtIdentityFilter(SECRET);

    @Test
    void validatesTokenAndReplacesSpoofedIdentityHeaders() {
        String token = Jwts.builder().setSubject("customer@example.com")
                .claim("userId", 42L).claim("role", "customer")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/conversations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "999").header("X-User-Role", "agent"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, request -> {
            forwarded.set(request);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("customer");
    }

    @Test
    void rejectsMissingAndExpiredTokens() {
        MockServerWebExchange missing = MockServerWebExchange.from(MockServerHttpRequest.get("/conversations/x"));
        filter.filter(missing, request -> Mono.empty()).block();
        assertThat(missing.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String expired = Jwts.builder().claim("userId", 1).claim("role", "customer")
                .setExpiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        MockServerWebExchange expiredExchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/conversation/agent/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired));
        filter.filter(expiredExchange, request -> Mono.empty()).block();
        assertThat(expiredExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
