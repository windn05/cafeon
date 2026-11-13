package com.example.docksystem_erp.service.Automation;

import com.example.docksystem_erp.entity.Automation.WebhookEvent;
import com.example.docksystem_erp.repository.Automation.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

// 보관함의 이벤트를 자동화 서버로 보낸다.
//  - 서명: X-Signature = sha256=HMAC-SHA256(비밀키, "타임스탬프.본문") → 받는 쪽이 위조 · 변조 · 재전송을 걸러낸다
//  - 실패하면 간격을 늘려 다시 보내고, 정해진 횟수를 넘기면 '실패'로 두어 화면에서 다시 보낼 수 있게 한다
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookSender {
    // 재시도 간격 (초): 10초 → 30초 → 1분 → 5분 → 15분 → 30분 → 1시간, 그다음은 실패
    private static final long[] BACKOFF = {10, 30, 60, 300, 900, 1800, 3600};
    static final int MAX_ATTEMPTS = BACKOFF.length + 1;
    private static final int BATCH = 20;

    private final WebhookEventRepository repository;
    private final WebhookPublisher publisher;
    // HTTP/1.1 고정: 기본값(HTTP/2)은 평문 주소에 h2c 업그레이드를 시도하는데,
    // n8n은 업그레이드 요청을 웹소켓으로 넘겨 응답하지 않는다 (요청 시간 초과)
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Scheduled(initialDelay = 5_000, fixedDelay = 3_000)
    public void flush() {
        if (!publisher.enabled()) return;
        List<WebhookEvent> due = repository.findByStatusAndNextAttemptAtLessThanEqualOrderByEventNoAsc(
                WebhookEvent.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH));
        for (WebhookEvent e : due) send(e);
    }

    void send(WebhookEvent e) {
        String body = e.getPayload();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        e.setAttempts(e.getAttempts() + 1);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(publisher.url()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-Event-Id", e.getEventId())
                    .header("X-Event-Type", e.getEventType())
                    .header("X-Timestamp", timestamp)
                    .header("X-Signature", "sha256=" + sign(publisher.secret(), timestamp + "." + body))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 == 2) {
                e.setStatus(WebhookEvent.SENT);
                e.setSentAt(LocalDateTime.now());
                e.setLastError(null);
            } else {
                fail(e, "HTTP " + res.statusCode() + " " + trim(res.body()));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail(e, "전송 중단");
        } catch (Exception ex) {
            fail(e, ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
        }
        repository.save(e);
    }

    private void fail(WebhookEvent e, String error) {
        e.setLastError(trim(error));
        if (e.getAttempts() >= MAX_ATTEMPTS) {
            e.setStatus(WebhookEvent.FAILED);
            e.setNextAttemptAt(null);
            log.warn("웹훅 전송 실패 (재시도 끝): {} {} - {}", e.getEventType(), e.getEventId(), error);
        } else {
            e.setNextAttemptAt(LocalDateTime.now().plusSeconds(BACKOFF[e.getAttempts() - 1]));
        }
    }

    static String sign(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("웹훅 서명을 만들지 못했습니다.", ex);
        }
    }

    private static String trim(String s) {
        if (s == null) return null;
        return s.length() > 480 ? s.substring(0, 480) + "…" : s;
    }
}
