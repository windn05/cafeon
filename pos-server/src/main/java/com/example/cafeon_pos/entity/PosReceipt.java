package com.example.cafeon_pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// POS 영수증 (매장 기록이 원본. 본사에는 전송 대기열로 보낸다)
@Entity
@Getter
@Setter
@Table(name = "pos_receipts", indexes = @Index(name = "idx_pos_receipt_day", columnList = "store_code,sale_date"))
public class PosReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_code", nullable = false, length = 20)
    private String storeCode;
    @Column(nullable = false, unique = true, length = 40)
    private String receiptNo;
    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;
    @Column(nullable = false)
    private LocalTime saleTime;
    @Column(nullable = false, length = 10)
    private String paymentMethod;
    @Column(nullable = false, length = 10)
    private String channel;
    private long totalAmount;
    // 본사 전송 상태 (대기 · 완료 · 실패)
    @Column(length = 10)
    private String sendStatus;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<PosReceiptLine> lines = new ArrayList<>();
}
