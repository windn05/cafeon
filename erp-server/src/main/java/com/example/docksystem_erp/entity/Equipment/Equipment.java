package com.example.docksystem_erp.entity.Equipment;

import com.example.docksystem_erp.dto.Equipment.EquipmentUpdateRequestDto;
import com.example.docksystem_erp.entity.Store.Store;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Data
@Table(name = "equipments")
public class Equipment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long equipNo;
    @Column(nullable = false,unique = true,length = 100)
    private String equipCode;
    @Column(nullable = false,length = 100)
    private String equipName;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EquipmentType type;
    @Column(nullable = false)
    private Long equipPrice;
    @Column(nullable = false)
    private Date equipPurchaseDate;
    // 내용연수(년). 감가상각은 취득가 · 취득일 · 내용연수로 계산한다 (정액법, 월할)
    @Column(nullable = false)
    private Integer usefulLifeYears = 5;
    // 장비 분류 (절단 장비, 크레인/운반 …). 기존 데이터는 비어 있을 수 있어 null 허용
    @Column(length = 50)
    private String equipCategory;
    // 둔 곳 (직영점 · 센트럴키친 · 물류센터). 가맹점 자산은 점주 소유라 받지 않는다
    @ManyToOne
    @JoinColumn(name = "store_no")
    private Store store;

    public void updateEquipment(EquipmentUpdateRequestDto requestDto){
        this.equipCode = requestDto.getEquipCode();
        this.equipName = requestDto.getEquipName();
        this.type = requestDto.getType();
        this.equipPrice = requestDto.getEquipPrice();
        this.equipPurchaseDate = requestDto.getEquipPurchaseDate();
        this.usefulLifeYears = requestDto.getUsefulLifeYears();
        this.equipCategory = requestDto.getEquipCategory();
    }
}

