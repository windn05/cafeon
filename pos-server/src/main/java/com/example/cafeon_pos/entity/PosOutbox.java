package com.example.cafeon_pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

// 본사로 보낼 데이터 대기열. 본사가 꺼져 있거나 인터넷이 끊겨도 쌓아 두었다가 순서대로 보낸다.
@Entity
@Getter
@Setter
@Table(name = "pos_outbox", indexes = @Index(name = "idx_pos_outbox_status", columnList = "store_code,status,id"))
public class PosOutbox {
    public static final String WAITING = "대기";
    public static final String DONE = "완료";
    public static final String FAILED = "실패";
    public static final String SKIPPED = "제외";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_code", nullable = false, length = 20)
    private String storeCode;
    // 판매 · 일마감 · 폐기 · 실사 · 발주요청 · 입고 · 공지확인 · 지시회신
    @Column(nullable = false, length = 20)
    private String dataType;
    @Column(length = 60)
    private String refNo;
    // 화면용 한 줄 설명
    @Column(length = 200)
    private String summary;
    // 본사 수집 API 경로 (예: /sales)
    @Column(nullable = false, length = 80)
    private String path;
    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String payload;
    @Column(nullable = false, length = 10)
    private String status;
    private int attempts;
    @Column(length = 300)
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    @Column(columnDefinition = "TEXT")
    private String response;
}
