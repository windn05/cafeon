package com.example.docksystem_erp.service.Automation;

import com.example.docksystem_erp.entity.Automation.WebhookEvent;
import com.example.docksystem_erp.repository.Automation.WebhookEventRepository;
import com.example.docksystem_erp.util.BizClock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// 업무 이벤트를 웹훅 보관함(outbox)에 넣는다. 부르는 쪽 트랜잭션에 같이 묶여서
// 업무 처리가 롤백되면 이벤트도 남지 않고, 업무가 저장되면 이벤트도 반드시 남는다.
@Service
@RequiredArgsConstructor
public class WebhookPublisher {
    public static final String ORDER_REQUEST_CREATED = "order_request.created";
    public static final String CLOSING_COMPLETED = "closing.completed";
    public static final String CLOSING_CANCELLED = "closing.cancelled";
    public static final String PING = "ping";

    // 영업 기록을 한꺼번에 다시 만드는 동안(시뮬레이터 빠른 재생)에는 이벤트를 내지 않는다
    private static volatile boolean muted;

    public static void mute(boolean value) {
        muted = value;
    }

    private final WebhookEventRepository repository;
    private final ObjectMapper mapper;

    @Value("${automation.webhook-url:}")
    private String url;
    @Value("${automation.webhook-secret:}")
    private String secret;

    // 받을 주소와 서명 키가 둘 다 있어야 켜진다 (없으면 이벤트를 쌓지 않는다)
    public boolean enabled() {
        return url != null && !url.isBlank() && secret != null && !secret.isBlank();
    }

    String url() {
        return url;
    }

    String secret() {
        return secret;
    }

    @Transactional
    public void publish(String type, Map<String, Object> data) {
        if (!enabled() || muted) return;
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("type", type);
        envelope.put("occurredAt", BizClock.now()); // 업무 시각 (시뮬레이션 날짜 기준)
        envelope.put("data", data);
        WebhookEvent e = new WebhookEvent();
        e.setEventId(eventId);
        e.setEventType(type);
        try {
            e.setPayload(mapper.writeValueAsString(envelope));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("웹훅 이벤트를 만들지 못했습니다: " + type, ex);
        }
        // 전송 · 재시도 시각은 실제 시계로 잡는다 (업무 날짜와 상관없이 지금 보내야 하므로)
        LocalDateTime now = LocalDateTime.now();
        e.setCreatedAt(now);
        e.setNextAttemptAt(now);
        repository.save(e);
    }
}
