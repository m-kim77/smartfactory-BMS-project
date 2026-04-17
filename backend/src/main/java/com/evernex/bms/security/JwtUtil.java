package com.evernex.bms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expiresMs;

    public JwtUtil(
        @Value("${bms.jwt.secret}") String secret,
        @Value("${bms.jwt.expires-hours:12}") long expiresHours
    ) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 needs at least 32 bytes; application.yml default is long enough.
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiresMs = expiresHours * 3600_000L;
    }

    public String sign(long uid, String role, String email, String name) {
        Instant now = Instant.now();
        return Jwts.builder()
            .claim("uid", uid)
            .claim("role", role)
            .claim("email", email)
            .claim("name", name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expiresMs)))
            .signWith(key)
            .compact();
    }

    public Claims verify(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
