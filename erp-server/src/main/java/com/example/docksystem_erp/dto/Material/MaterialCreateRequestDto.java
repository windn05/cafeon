package com.example.docksystem_erp.dto.Material;

import lombok.Data;

// 식자재 등록 · 수정 공용
@Data
public class MaterialCreateRequestDto {
    private String materialCode;
    private String materialName;
    private String materialType;
    private String materialSize;
    private Long materialPrice;
    private String materialUnit;
    private Double packQty;
    private String storageType;
    private Integer shelfLifeDays;
    private Long clientNo;
}
