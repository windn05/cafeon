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

// 발주서: 매장이 공급업체 한 곳에 식자재를 주문한 기록. 입고 검수를 하면 재고에 더해진다.
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long poNo;
    // 발주 번호 (예: PO-ST-GN01-261106-01)
    @Column(nullable = false, unique = true, length = 40)
    private String poNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_no")
    private Store store;
    @ManyToOne(optional = false)
    @JoinColumn(name = "client_no")
    private Client client;
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

    public long getOrderAmount() {
        return lines.stream().mapToLong(l -> l.getOrderPacks() * l.getPackPrice()).sum();
    }
}
