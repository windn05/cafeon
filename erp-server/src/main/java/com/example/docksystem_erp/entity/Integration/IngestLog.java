package com.example.docksystem_erp.entity.Integration;

import com.example.docksystem_erp.entity.Store.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 매장 시스템에서 데이터를 받은 기록 (수집 현황 · 누락 확인용)
@Entity
@Getter
@Setter
@Table(name = "ingest_logs", indexes = {
        @Index(name = "idx_ingest_store_type", columnList = "store_no,data_type,received_at"),
        @Index(name = "idx_ingest_received", columnList = "received_at")
})
public class IngestLog {
    // 데이터 종류
    public static final String SALES = "판매";
    public static final String CLOSING = "일마감";
    public static final String WASTE = "폐기";
    public static final String STOCKTAKE = "실사";
    public static final String ORDER = "발주요청";
    public static final String DELIVERY = "입고";
    public static final String REPLY = "지시회신";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_no")
    private Store store;
    @Column(nullable = false, length = 20)
    private String dataType;
    // 데이터가 해당하는 영업일
    private LocalDate businessDate;
    @Column(nullable = false)
    private LocalDateTime receivedAt;
    private int recordCount;
    // 성공 · 실패
    @Column(nullable = false, length = 10)
    private String status;
    @Column(length = 300)
    private String message;
}
