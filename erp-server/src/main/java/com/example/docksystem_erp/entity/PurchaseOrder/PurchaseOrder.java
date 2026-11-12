package com.example.docksystem_erp.entity.PurchaseOrder;

import com.example.docksystem_erp.entity.Client.Client;
import com.example.docksystem_erp.entity.Store.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 발주서: 식자재를 받을 곳(store)과 보내는 곳을 담은 기록. 받는 곳이 입고 검수를 하면 재고에 더해진다.
//  - 직송: 공급업체 → 매장 (client)
//  - 출고: 물류센터 → 매장 (fromStore). 만들 때 물류센터 재고에서 바로 빠진다
//  - 보충: 공급업체 → 물류센터 (client, store = 물류센터)
@Entity
@Getter
@Setter
@Table(name = "purchase_orders", indexes = {
        @Index(name = "idx_po_store_date", columnList = "store_no,order_date"),
        @Index(name = "idx_po_status", columnList = "status")
})
public class PurchaseOrder {
    public static final String ORDERED = "발주완료";
    public static final String RECEIVED = "입고완료";
    public static final String CANCELED = "취소";
    public static final String KIND_DIRECT = "직송";
    public static final String KIND_SHIP = "출고";
    public static final String KIND_RESTOCK = "보충";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long poNo;
    // 발주 번호 (예: PO-261106-004 공급업체 발주, SO-261106-002 물류센터 출고)
    @Column(nullable = false, unique = true, length = 40)
    private String poNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_no")
    private Store store;
    // 공급업체 (출고는 없음)
    @ManyToOne
    @JoinColumn(name = "client_no")
    private Client client;
    // 출고일 때 보내는 물류센터
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_store_no")
    private Store fromStore;
    // 직송 · 출고 · 보충 (예전 기록은 비어 있으면 직송)
    @Column(length = 10)
    private String kind;
    @Column(nullable = false)
    private LocalDate orderDate;
    // 입고 예정일 (발주일 + 공급업체 납품 소요일)
    @Column(nullable = false)
    private LocalDate expectedDate;
    private LocalDate receivedDate;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 300)
    private String note;
    @Column(length = 300)
    private String receiveNote;
    @Column(length = 50)
    private String orderedBy;
    @Column(length = 50)
    private String receivedBy;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    public String kindOrDefault() {
        return kind == null ? KIND_DIRECT : kind;
    }

    // 보내는 곳 이름: 공급업체 또는 물류센터
    public String sourceName() {
        if (client != null) return client.getClientName();
        return fromStore == null ? "물류센터" : fromStore.getStoreName();
    }

    public long getOrderAmount() {
        return lines.stream().mapToLong(l -> l.getOrderPacks() * l.getPackPrice()).sum();
    }
}
