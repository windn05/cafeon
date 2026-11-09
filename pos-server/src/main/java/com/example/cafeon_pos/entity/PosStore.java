package com.example.cafeon_pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

// 이 POS에 등록된 매장 (본사가 발급한 매장 연동 키로 등록)
@Entity
@Getter
@Setter
@Table(name = "pos_stores")
public class PosStore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 20)
    private String storeCode;
    @Column(nullable = false, length = 50)
    private String storeName;
    @Column(length = 20)
    private String storeType;
    @Column(nullable = false, length = 80)
    private String integrationKey;
    private LocalDateTime registeredAt;

    // 본사와 마지막으로 연결된 상태
    private boolean online;
    private LocalDateTime lastContactAt;
    @Column(length = 300)
    private String lastError;
    // 본사가 연동 키를 다시 발급해서 지금 키가 막힘 → 기기 다시 등록 필요
    private boolean keyRejected;

    // 메뉴 · 식자재를 마지막으로 받은 시각
    private LocalDateTime masterSyncedAt;
    // 본사 메시지: 마지막으로 받은 번호
    private Long inboxCursor = 0L;
    private LocalDateTime inboxSyncedAt;
    // 본사 기준 재고 · 입고 예정 · 발주 요청 현황 (본사 /today 응답 그대로)
    @Column(columnDefinition = "MEDIUMTEXT")
    private String snapshotJson;
    private LocalDateTime snapshotAt;
}
