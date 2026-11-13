package com.example.docksystem_erp.JwtSecurity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.regex.Pattern;

// 자동화 서버(n8n) 인증: 로그인 대신 X-Automation-Key 헤더.
// 사람 계정과 달리 할 수 있는 일을 정해 둔다 (최소 권한) — 조회 전부 + 아래 처리 몇 가지만 허용.
// 처리 기록의 작성자는 '자동화(n8n)'로 남아 사람이 한 일과 구분된다.
public class AutomationKeyFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Automation-Key";
    public static final String ACTOR = "자동화(n8n)";
    private static final String BASE = "/api/erp/v1";

    private record Rule(String method, Pattern path) {}

    private static final List<Rule> WRITE_RULES = List.of(
            new Rule("POST", Pattern.compile(BASE + "/order-requests/\\d+/(approve|reject)")),
            new Rule("POST", Pattern.compile(BASE + "/purchase-orders")),
            new Rule("POST", Pattern.compile(BASE + "/store-messages")),
            new Rule("POST", Pattern.compile(BASE + "/automation/logs(/\\d+/resolve)?")));

    private final byte[] key;

    public AutomationKeyFilter(String key) {
        this.key = key == null || key.isBlank() ? null : key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String sent = request.getHeader(HEADER);
        if (sent == null) {
            chain.doFilter(request, response);
            return;
        }
        // 길이와 상관없이 같은 시간에 비교 (키를 한 글자씩 맞춰 보는 공격 방지)
        if (key == null || !MessageDigest.isEqual(key, sent.getBytes(StandardCharsets.UTF_8))) {
            deny(response, HttpServletResponse.SC_UNAUTHORIZED, "자동화 키가 맞지 않습니다.");
            return;
        }
        String path = request.getRequestURI();
        if (!allowed(request.getMethod(), path)) {
            deny(response, HttpServletResponse.SC_FORBIDDEN, "자동화 키로는 할 수 없는 요청입니다: " + request.getMethod() + " " + path);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ACTOR, "", List.of()));
        chain.doFilter(request, response);
    }

    static boolean allowed(String method, String path) {
        if (!path.startsWith(BASE + "/")) return false;
        // 조회는 열어 두되 직원 정보(연락처 · 급여)는 자동화에 필요 없으므로 뺀다
        if ("GET".equals(method)) return !path.startsWith(BASE + "/users");
        return WRITE_RULES.stream().anyMatch(r -> r.method().equals(method) && r.path().matcher(path).matches());
    }

    private static void deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message.replace("\"", "'") + "\"}");
    }
}
