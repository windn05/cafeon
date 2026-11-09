package com.example.docksystem_erp.entity.OrderRequest;

import com.example.docksystem_erp.entity.Store.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 매장 발주 요청: 매장 앱에서 올린 요청을 본사가 승인하면 공급업체별 발주서가 만들어진다
@Entity
@Getter
@Setter
@Table(name = "order_requests", indexes = {
        @Index(name = "idx_req_status", columnList = "status"),
        @Index(name = "idx_req_store_date", columnList = "store_no,request_date")
})
public class OrderRequest {
    public static final String REQUESTED = "요청";
    public static final String APPROVED = "승인";
    public static final String REJECTED = "반려";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestNo;
    // 요청 번호 (예: RQ-ST-GN01-261106-01)
    @Column(nullable = false, unique = true, length = 40)
    private String requestNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_no")
    private Store store;
    @Column(nullable = false)
    private LocalDate requestDate;
    // 매장이 원하는 도착일 (선택)
    private LocalDate neededDate;
    @Column(nullable = false, length = 10)
    private String status;
    @Column(length = 300)
    private String note;
    // 승인 · 반려
    @Column(length = 50)
    private String decidedBy;
    private LocalDateTime decidedAt;
    @Column(length = 300)
    private String rejectReason;
    // 승인해서 만든 발주서 번호들 (쉼표로 구분)
    @Column(length = 300)
    private String poNumbers;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<OrderRequestLine> lines = new ArrayList<>();
}
