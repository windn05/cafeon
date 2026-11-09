package com.example.docksystem_erp.entity.PurchaseOrder;

import com.example.docksystem_erp.entity.Material.Material;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// 발주서 한 줄. 수량은 포장 단위(봉·팩·박스)로 주문하고, 단가·용량은 발주 시점 값을 남긴다.
@Entity
@Getter
@Setter
@Table(name = "purchase_order_lines")
public class PurchaseOrderLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lineNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_no")
    private PurchaseOrder purchaseOrder;
    @ManyToOne(optional = false)
    @JoinColumn(name = "material_no")
    private Material material;
    @Column(nullable = false)
    private int orderPacks;
    // 입고 검수 때 실제로 받은 포장 수 (입고 전에는 null)
    private Integer receivedPacks;
    // 포장 1개 단가 · 포장 1개 용량(사용 단위)
    @Column(nullable = false)
    private long packPrice;
    @Column(nullable = false)
    private double packQty;
}
