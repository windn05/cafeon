package com.example.docksystem_erp.entity.Inventory;

import com.example.docksystem_erp.entity.Material.Material;
import com.example.docksystem_erp.entity.Store.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 재고 변동 한 건. 매장의 현재고 = 이 기록의 수량 합계.
// 수량은 사용 단위(g·ml·EA) 기준이며 들어오면 +, 나가면 -.
@Entity
@Getter
@Setter
@Table(name = "inventory_txns", indexes = {
        @Index(name = "idx_inv_store_material", columnList = "store_no,material_no"),
        @Index(name = "idx_inv_store_date", columnList = "store_no,txn_date"),
        @Index(name = "idx_inv_ref", columnList = "ref_type,ref_no")
})
public class InventoryTxn {
    public static final String OPENING = "기초재고";
    public static final String RECEIPT = "입고";
    public static final String CONSUME = "판매소진";
    public static final String WASTE = "폐기";
    public static final String ADJUST = "실사조정";
    // 물류센터에서 매장으로 내보낸 수량 (물류센터 재고에서 빠진다)
    public static final String SHIP = "출고";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long txnNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_no")
    private Store store;
    @ManyToOne(optional = false)
    @JoinColumn(name = "material_no")
    private Material material;
    @Column(nullable = false)
    private LocalDate txnDate;
    @Column(nullable = false, length = 20)
    private String txnType;
    @Column(nullable = false)
    private double quantity;
    // 사용 단위 1개당 원가 (기록 시점)
    @Column(nullable = false)
    private double unitCost;
    // 폐기 사유 등
    @Column(length = 50)
    private String reason;
    @Column(length = 200)
    private String note;
    // 어떤 마감·실사에서 생긴 기록인지 (CLOSING / STOCKTAKE)
    @Column(length = 20)
    private String refType;
    private Long refNo;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(length = 50)
    private String createdBy;
}
