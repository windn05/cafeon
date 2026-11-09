package com.example.docksystem_erp.entity.OrderRequest;

import com.example.docksystem_erp.entity.Material.Material;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "order_request_lines")
public class OrderRequestLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lineNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_no")
    private OrderRequest request;
    @ManyToOne(optional = false)
    @JoinColumn(name = "material_no")
    private Material material;
    // 매장이 요청한 포장 수 · 본사가 승인한 포장 수
    @Column(nullable = false)
    private int requestedPacks;
    private Integer approvedPacks;
}
