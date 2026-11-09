package com.example.cafeon_pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 본사에서 받은 메시지 (공지 · 메뉴·가격 · 발주 결과 · 지시)
@Entity
@Getter
@Setter
@Table(name = "pos_messages", uniqueConstraints = @UniqueConstraint(columnNames = {"store_code", "hq_id"}))
public class PosMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_code", nullable = false, length = 20)
    private String storeCode;
    // 본사 받은 편지함 번호 (확인 · 회신을 보낼 때 씀)
    @Column(name = "hq_id", nullable = false)
    private Long hqId;
    @Column(nullable = false, length = 20)
    private String kind;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String body;
    @Column(columnDefinition = "TEXT")
    private String payloadJson;
    private boolean important;
    private boolean needsReply;
    private LocalDate dueDate;
    @Column(length = 50)
    private String sentBy;
    private LocalDateTime sentAt;
    private LocalDateTime receivedAt;
    private LocalDateTime readAt;
    @Column(length = 500)
    private String reply;
    private LocalDateTime repliedAt;
}
