package com.example.docksystem_erp.entity.Message;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 본사 → 매장 POS로 보내는 메시지 (공지 · 메뉴·가격 변경 · 발주 처리 결과 · 지시·점검 요청)
@Entity
@Getter
@Setter
@Table(name = "store_messages", indexes = @Index(name = "idx_msg_sent", columnList = "sent_at"))
public class StoreMessage {
    public static final String NOTICE = "공지";
    public static final String MENU = "메뉴·가격";
    public static final String ORDER = "발주 결과";
    public static final String TASK = "지시";
    public static final List<String> KINDS = List.of(NOTICE, MENU, ORDER, TASK);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageNo;
    @Column(nullable = false, length = 20)
    private String kind;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String body;
    // 매장 POS가 화면에 쓰는 추가 정보 (JSON). 예: 발주 결과의 발주서 번호 · 도착 예정일
    @Column(columnDefinition = "TEXT")
    private String payload;
    private boolean important;
    // 매장이 답해야 하는 메시지 (지시 · 점검 요청)
    private boolean needsReply;
    private LocalDate dueDate;
    // 화면용 대상 설명 (예: 전 매장, 홍대입구점 외 2곳)
    @Column(length = 120)
    private String targetLabel;
    // 어떤 일에서 나온 메시지인지 (예: closing:3:2026-11-03, request:RQ-...)
    @Column(length = 80)
    private String refKey;
    @Column(nullable = false, length = 50)
    private String sentBy;
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("targetNo ASC")
    private List<StoreMessageTarget> targets = new ArrayList<>();
}
