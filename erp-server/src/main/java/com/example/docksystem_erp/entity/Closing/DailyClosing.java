package com.example.docksystem_erp.entity.Closing;

import com.example.docksystem_erp.entity.Store.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 매장 일 마감: 그날 매출을 확정하고 현금 시재를 확인한다.
// 마감하면 그날 판매량만큼 레시피 기준 식자재가 재고에서 빠진다(판매소진).
@Entity
@Getter
@Setter
@Table(name = "daily_closings",
        uniqueConstraints = @UniqueConstraint(name = "uk_closing_store_date", columnNames = {"store_no", "closing_date"}))
public class DailyClosing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long closingNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_no")
    private Store store;
    @Column(nullable = false)
    private LocalDate closingDate;

    private int receiptCount;
    private int itemCount;
    private long salesAmount;
    private double costAmount;
    // 결제수단별
    private long cardAmount;
    private long cashAmount;
    private long easyPayAmount;
    // 판매 채널별
    private long dineInAmount;
    private long takeoutAmount;
    private long deliveryAmount;
    // 현금 시재: 준비금 + 현금 매출 = 있어야 할 현금, 실제로 센 현금과의 차이
    private long cashFloat;
    private long cashCounted;
    private long cashDiff;
    private double wasteAmount;

    @Column(length = 300)
    private String note;
    @Column(length = 50)
    private String closedBy;
    @Column(nullable = false)
    private LocalDateTime closedAt;
    // 매장이 이 날 마감을 취소하고 다시 마감한 횟수 · 마지막 취소 사유
    private Integer reopenCount;
    @Column(length = 200)
    private String reopenNote;
}
