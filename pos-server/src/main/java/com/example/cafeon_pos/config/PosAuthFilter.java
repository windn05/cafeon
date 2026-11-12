package com.example.cafeon_pos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// POS API 인증: 기기 등록(/auth/**) · 개발용(/dev/**) 말고는 POS 토큰이 있어야 한다. 토큰의 매장 코드를 요청에 붙여 준다.
public class PosAuthFilter extends OncePerRequestFilter {
    public static final String STORE_ATTR = "storeCode";
    private final PosTokens tokens;

    public PosAuthFilter(PosTokens tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || path.startsWith("/api/pos/v1/auth/")
                // 개발용 시뮬레이터 API는 컨트롤러가 설정 · 접속 주소로 막는다
                || path.startsWith("/api/pos/v1/dev/")) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        String storeCode = header != null && header.startsWith("Bearer ") ? tokens.storeCode(header.substring(7)) : null;
        if (storeCode == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"POS 기기 등록이 필요합니다.\"}");
            return;
        }
        request.setAttribute(STORE_ATTR, storeCode);
        chain.doFilter(request, response);
    }
}
