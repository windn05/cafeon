package com.example.docksystem_erp.dto.Store;

import lombok.Data;

import java.time.LocalDate;

// 매장 등록 · 수정 공용
@Data
public class StoreRequestDto {
    private String storeCode;
    private String storeName;
    private String storeType;
    private String status;
    private String address;
    private String phone;
    private String managerName;
    private LocalDate openDate;
    private Long monthlyRent;
    private Long monthlyFixedCost;
}
