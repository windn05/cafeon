package com.example.docksystem_erp.dto.Store;

import com.example.docksystem_erp.entity.Store.Store;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StoreResponseDto {
    private Long storeNo;
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
    // 이 매장 소속 직원 · 설비 수 (목록 표시용)
    private long staffCount;
    private long equipmentCount;

    public static StoreResponseDto fromEntity(Store store) {
        StoreResponseDto dto = new StoreResponseDto();
        dto.setStoreNo(store.getStoreNo());
        dto.setStoreCode(store.getStoreCode());
        dto.setStoreName(store.getStoreName());
        dto.setStoreType(store.getStoreType());
        dto.setStatus(store.getStatus());
        dto.setAddress(store.getAddress());
        dto.setPhone(store.getPhone());
        dto.setManagerName(store.getManagerName());
        dto.setOpenDate(store.getOpenDate());
        dto.setMonthlyRent(store.getMonthlyRent());
        dto.setMonthlyFixedCost(store.getMonthlyFixedCost());
        return dto;
    }
}
