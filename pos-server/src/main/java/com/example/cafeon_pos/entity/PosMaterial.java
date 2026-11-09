package com.example.cafeon_pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


// 본사에서 받은 식자재 (폐기 · 실사 · 발주 요청에 쓰는 코드 · 단위 · 포장 단위)
@Entity
@Getter
@Setter
@Table(name = "pos_materials", uniqueConstraints = @UniqueConstraint(columnNames = {"store_code", "material_code"}))
public class PosMaterial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_code", nullable = false, length = 20)
    private String storeCode;
    @Column(name = "material_code", nullable = false, length = 30)
    private String materialCode;
    @Column(nullable = false, length = 50)
    private String materialName;
    @Column(length = 20)
    private String materialType;
    @Column(length = 10)
    private String unit;
    @Column(length = 30)
    private String packLabel;
    private Double packQty;
    private Long packPrice;
    @Column(length = 10)
    private String storageType;
}
