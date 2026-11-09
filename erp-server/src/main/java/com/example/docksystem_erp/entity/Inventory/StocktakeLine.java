package com.example.docksystem_erp.entity.Inventory;

import com.example.docksystem_erp.entity.Material.Material;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "stocktake_lines")
public class StocktakeLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lineNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stocktake_no")
    private Stocktake stocktake;
    @ManyToOne(optional = false)
    @JoinColumn(name = "material_no")
    private Material material;
    // 장부 재고 · 실사 수량 · 차이 (사용 단위)
    @Column(nullable = false)
    private double expectedQty;
    @Column(nullable = false)
    private double countedQty;
    @Column(nullable = false)
    private double diffQty;
    @Column(nullable = false)
    private double unitCost;
}
