package com.example.docksystem_erp.service.Simulation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 매장 영업 시뮬레이터가 매장 직원처럼 POS API를 부른다 (실제 POS 화면이 부르는 것과 같은 API)
@Component
public class PosClient {
    private final RestTemplate rest;
    private final String base;

    public PosClient(@Value("${pos.api-url:http://localhost:8081/api/pos/v1}") String base) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(60000);
        this.rest = new RestTemplate(f);
        this.base = base;
    }

    public static class PosError extends RuntimeException {
        public PosError(String message) { super(message); }
    }

    // ----- 개발용 (POS 업무 시계 · 기록 비우기 · 전체 동기화) -----

    public void clock(LocalDateTime at) {
        Map<String, Object> body = new HashMap<>();
        body.put("at", at == null ? null : at.withNano(0).toString());
        call(HttpMethod.POST, null, "/dev/clock", body);
    }

    public void reset() {
        call(HttpMethod.POST, null, "/dev/reset", Map.of());
    }

    public void syncAll() {
        call(HttpMethod.POST, null, "/dev/sync-all", Map.of());
    }

    // 본사 연결 끊김 흉내 (켜 두는 동안 POS 전송이 대기열에 쌓인다)
    public void offline(boolean on) {
        call(HttpMethod.POST, null, "/dev/offline", Map.of("on", on));
    }

    // 이 날부터 이후 POS 판매 · 마감 · 전송 기록 지우기
    public void rewind(LocalDate from) {
        call(HttpMethod.POST, null, "/dev/rewind", Map.of("from", from.toString()));
    }

    // ----- 매장 업무 -----

    public String register(String storeKey) {
        return (String) call(HttpMethod.POST, null, "/auth/register", Map.of("storeKey", storeKey)).get("token");
    }

    public void sync(String token) {
        call(HttpMethod.POST, token, "/sync", Map.of());
    }

    public Map<String, Object> today(String token) {
        return call(HttpMethod.GET, token, "/today", null);
    }

    // at: 결제 시각 (빠른 재생용, POS 개발 모드에서만 받음). null이면 POS의 지금 시각
    public void sale(String token, LocalDateTime at, String payment, String channel, List<Map<String, Object>> items) {
        Map<String, Object> body = new HashMap<>();
        body.put("paymentMethod", payment);
        body.put("channel", channel);
        body.put("items", items);
        if (at != null) body.put("at", at.withNano(0).toString());
        call(HttpMethod.POST, token, "/sales", body);
    }

    public void waste(String token, String materialCode, double qty, String reason) {
        call(HttpMethod.POST, token, "/wastes", Map.of("materialCode", materialCode, "quantity", qty, "reason", reason));
    }

    public void stocktake(String token, String note, List<Map<String, Object>> lines) {
        call(HttpMethod.POST, token, "/stocktakes", Map.of("note", note, "lines", lines));
    }

    public void orderRequest(String token, LocalDate neededDate, String note, List<Map<String, Object>> lines) {
        call(HttpMethod.POST, token, "/order-requests", Map.of("neededDate", neededDate.toString(), "note", note, "lines", lines));
    }

    public void delivery(String token, String poNumber, String note, List<Map<String, Object>> lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("poNumber", poNumber);
        body.put("note", note);
        body.put("lines", lines);
        call(HttpMethod.POST, token, "/deliveries", body);
    }

    public void close(String token, long cashCounted, String note) {
        Map<String, Object> body = new HashMap<>();
        body.put("cashCounted", cashCounted);
        body.put("note", note);
        call(HttpMethod.POST, token, "/closings", body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(HttpMethod method, String token, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        try {
            Map<String, Object> r = rest.exchange(base + path, method, new HttpEntity<>(body, h), Map.class).getBody();
            return r == null ? Map.of() : r;
        } catch (HttpStatusCodeException e) {
            String msg = e.getResponseBodyAsString();
            throw new PosError("POS " + path + " " + e.getStatusCode().value() + " " + (msg.length() > 200 ? msg.substring(0, 200) : msg));
        } catch (ResourceAccessException e) {
            throw new PosError("POS 서버에 연결할 수 없습니다 (" + base + ")");
        }
    }
}
