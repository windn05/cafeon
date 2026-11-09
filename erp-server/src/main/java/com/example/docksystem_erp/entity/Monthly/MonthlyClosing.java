package com.example.docksystem_erp.entity.Monthly;

import com.example.docksystem_erp.entity.Store.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 매장 월 마감: 그달 손익을 확정해 남기고, 그달 판매·재고 기록을 잠근다.
@Entity
@Getter
@Setter
@Table(name = "monthly_closings",
        uniqueConstraints = @UniqueConstraint(name = "uk_monthly_store_ym", columnNames = {"store_no", "year_month_key"}))
public class MonthlyClosing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long monthlyNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_no")
    private Store store;
    // 마감한 달 (예: 2026-10)
    @Column(name = "year_month_key", nullable = false, length = 7)
    private String yearMonth;

    // 확정 당시 손익 (원)
    private long salesAmount;
    private long netSales;
    private long foodCost;
    private long theoreticalCost;
    private long wasteAmount;
    private long lossAmount;
    private long laborCost;
    private long rent;
    private long fixedCost;
    private long feeAmount;
    private long operatingProfit;
    private int receiptCount;

    @Column(length = 300)
    private String note;
    @Column(length = 50)
    private String closedBy;
    @Column(nullable = false)
    private LocalDateTime closedAt;
}
