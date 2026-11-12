package com.example.cafeon_pos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

// 본사 ERP 데이터 수집 API 호출. 매장 연동 키(X-Store-Key)로 인증한다.
@Component
public class HqClient {
    public static final String KEY_HEADER = "X-Store-Key";

    private final RestTemplate rest;
    private final ObjectMapper json;
    private final String baseUrl;

    // 본사가 요청을 거절함 (잘못된 데이터 · 이미 마감한 날 · 키 오류 등). 다시 보내도 같은 결과.
    public static class Rejected extends RuntimeException {
        private final int status;

        public Rejected(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    // 본사에 연결 못 함 (꺼져 있음 · 네트워크). 나중에 다시 보내면 된다.
    public static class Unavailable extends RuntimeException {
        public Unavailable(String message) {
            super(message);
        }
    }

    public HqClient(RestTemplate hqRestTemplate, ObjectMapper json, @Value("${hq.ingest-url}") String baseUrl) {
        this.rest = hqRestTemplate;
        this.json = json;
        this.baseUrl = baseUrl;
    }

    public JsonNode get(String key, String path) {
        return call(key, HttpMethod.GET, path, null);
    }

    public JsonNode post(String key, String path, String bodyJson) {
        return call(key, HttpMethod.POST, path, bodyJson);
    }

    // 개발 · 시험용: 본사 연결이 끊긴 것처럼 (매장 영업 시뮬레이터의 본사 연결 끊김)
    private static volatile boolean simulatedOffline;

    public static void setSimulatedOffline(boolean on) {
        simulatedOffline = on;
    }

    private JsonNode call(String key, HttpMethod method, String path, String bodyJson) {
        if (simulatedOffline) throw new Unavailable("본사 연결 끊김 (시험)");
        HttpHeaders headers = new HttpHeaders();
        headers.set(KEY_HEADER, key);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        try {
            String body = rest.exchange(baseUrl + path, method, new HttpEntity<>(bodyJson, headers), String.class).getBody();
            return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            String message = message(e.getResponseBodyAsString());
            if (status >= 500) throw new Unavailable("본사 서버 오류 (" + status + "): " + message);
            throw new Rejected(status, message == null ? "본사가 요청을 거절했습니다 (" + status + ")" : message);
        } catch (RestClientException e) {
            throw new Unavailable(e.getMessage());
        } catch (java.io.IOException e) {
            throw new Unavailable("본사 응답을 읽지 못했습니다: " + e.getMessage());
        }
    }

    private String message(String body) {
        try {
            JsonNode n = json.readTree(body);
            return n.hasNonNull("message") ? n.get("message").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
