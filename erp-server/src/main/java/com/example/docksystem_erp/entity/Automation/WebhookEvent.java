package com.example.docksystem_erp.entity.Automation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 외부 자동화(n8n)로 보낼 이벤트 보관함 (outbox).
// 업무 처리와 같은 트랜잭션에서 저장하고, 전송은 WebhookSender가 따로 맡는다 → 자동화 서버가 꺼져 있어도 이벤트가 사라지지 않는다.
@Entity
@Getter
@Setter
@Table(name = "webhook_events", indexes = @Index(name = "idx_webhook_due", columnList = "status, nextAttemptAt"))
public class WebhookEvent {
    public static final String PENDING = "대기";
    public static final String SENT = "전송";
    public static final String FAILED = "실패";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventNo;

    // 받는 쪽이 같은 이벤트를 두 번 처리하지 않도록 쓰는 고유 ID
    @Column(nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 10)
    private String status = PENDING;

    @Column(nullable = false)
    private int attempts;

    // 재시도 시각 (전송 실패 시 점점 늦춘다)
    private LocalDateTime nextAttemptAt;

    @Column(length = 500)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}
