package com.example.cafeon_pos.config;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

// POS 기기 로그인 토큰. 매장 연동 키로 기기를 등록하면 발급하고, 토큰 안에는 매장 코드만 넣는다.
@Component
public class PosTokens {
    private final SecretKey key;
    private final Duration ttl;

    public PosTokens(@Value("${pos.jwt.secret}") String secret, @Value("${pos.jwt.days:7}") int days) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofDays(days);
    }

    public String issue(String storeCode) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(storeCode)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 맞지 않거나 만료된 토큰이면 null
    public String storeCode(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
