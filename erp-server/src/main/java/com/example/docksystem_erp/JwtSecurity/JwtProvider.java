package com.example.docksystem_erp.JwtSecurity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;


@Component
public class JwtProvider {
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long validityInMs;
    private SecretKey signingKey;

    @PostConstruct
    public void init(){
        signingKey = Keys.hmacShaKeyFor(
                secretKey.getBytes(StandardCharsets.UTF_8));
    }



    // JWT 토큰 생성
    public String createToken(String username) {
        long now = (new Date()).getTime();
        Date expiration = new Date(now + validityInMs); // jwt.expiration 설정값(기본 8시간) 후 만료

        return Jwts.builder()
                .setSubject(username) // 토큰 주체(사용자 이름)
                .setIssuedAt(new Date()) // 토큰 발행 시간
                .setExpiration(expiration) // 토큰 만료 시간
                .signWith(signingKey, SignatureAlgorithm.HS256) // 서명
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | io.jsonwebtoken.MalformedJwtException e) {
            // 유효하지 않은 JWT 서명
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // 만료된 JWT 토큰
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            // 지원되지 않는 JWT 토큰
        } catch (IllegalArgumentException e) {
            // JWT가 잘못됨
        }
        return false;
    }


    // JWT 토큰에서 사용자 이름 추출
    public String getUsername(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }
}








