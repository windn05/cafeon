package com.example.docksystem_erp.entity.Store;

import com.example.docksystem_erp.dto.Store.StoreRequestDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

// 매장 · 물류센터 · 센트럴키친 같은 사업장
@Entity
@Getter
@Setter
@Table(name = "stores")
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long storeNo;
    @Column(nullable = false, unique = true, length = 30)
    private String storeCode;
    @Column(nullable = false, length = 50)
    private String storeName;
    // 직영점 · 가맹점 · 물류센터 · 센트럴키친
    @Column(nullable = false, length = 20)
    private String storeType;
    // 영업중 · 휴점 · 폐점
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false, length = 200)
    private String address;
    @Column(length = 30)
    private String phone;
    @Column(length = 30)
    private String managerName;
    private LocalDate openDate;
    // 월 임차료 · 월 기타 고정비(관리비·공과금·통신 등). 월간 손익 계산에 쓴다
    private Long monthlyRent;
    private Long monthlyFixedCost;
    // 매장 시스템(POS·매장 앱)이 본사로 데이터를 보낼 때 쓰는 연동 키
    @Column(unique = true, length = 64)
    private String integrationKey;

    public void apply(StoreRequestDto dto) {
        this.storeCode = dto.getStoreCode();
        this.storeName = dto.getStoreName();
        this.storeType = dto.getStoreType();
        this.status = dto.getStatus();
        this.address = dto.getAddress();
        this.phone = dto.getPhone();
        this.managerName = dto.getManagerName();
        this.openDate = dto.getOpenDate();
        this.monthlyRent = dto.getMonthlyRent();
        this.monthlyFixedCost = dto.getMonthlyFixedCost();
    }
}
