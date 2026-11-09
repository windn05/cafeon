package com.example.cafeon_pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 매장 일 마감 (POS 매출 기준 시재 확인)
@Entity
@Getter
@Setter
@Table(name = "pos_closings", uniqueConstraints = @UniqueConstraint(columnNames = {"store_code", "closing_date"}))
public class PosClosing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_code", nullable = false, length = 20)
    private String storeCode;
    @Column(name = "closing_date", nullable = false)
    private LocalDate closingDate;
    private long totalAmount;
    private int receipts;
    private long cardAmount;
    private long easyPayAmount;
    private long cashAmount;
    private long cashFloat;
    private long expectedCash;
    private long cashCounted;
    private long cashDiff;
    @Column(length = 300)
    private String note;
    private LocalDateTime closedAt;
    @Column(length = 10)
    private String sendStatus;
}
