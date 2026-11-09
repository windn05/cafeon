package com.example.docksystem_erp.service.Home;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service

public class NaverApiService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    // 키는 설정(naver.client-id · naver.client-secret)에서 받는다. 비어 있으면 뉴스를 부르지 않는다
    @Value("${naver.client-id:}")
    private String clientId;
    @Value("${naver.client-secret:}")
    private String clientSecret;

    // 최신 뉴스 5개 가져오기 (제목 + 링크)
    public List<Map<String, String>> searchNewsTop5(String query) {
        try {
            // URL을 직접 조립하지 않고, Builder를 사용하여 안전하게 생성
            URI uri = UriComponentsBuilder
                    .fromUriString("https://openapi.naver.com")
                    .path("/v1/search/news.json")
                    .queryParam("query", query)
                    .queryParam("display", 5)
                    .queryParam("sort", "date")
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUri();

            // 요청 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", clientId);
            headers.set("X-Naver-Client-Secret", clientSecret);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // API 요청 및 응답 수신
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);

            // JSON 파싱
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode items = root.get("items");

            List<Map<String, String>> newsList = new ArrayList<>();
            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    String title = item.get("title").asText().replaceAll("<.*?>", "");
                    String link = item.get("link").asText();
                    newsList.add(Map.of("title", title, "link", link));
                }
            }
            return newsList;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>(); // 에러 발생 시 빈 리스트 반환
        }
    }
}
