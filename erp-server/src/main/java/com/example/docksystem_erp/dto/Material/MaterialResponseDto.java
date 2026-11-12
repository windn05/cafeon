package com.example.docksystem_erp.dto.Material;

import com.example.docksystem_erp.entity.Material.Material;
import lombok.Data;

@Data
public class MaterialResponseDto {
    private Long materialNo;
    private String materialCode;
    private String materialName;
    private String materialType;
    private String materialSize;
    private Long materialPrice;
    private String materialUnit;
    private Double packQty;
    private String storageType;
    private String supplyType;
    private Integer shelfLifeDays;
    private Long clientNo;
    private String clientName;
    // 사용 단위 1개당 원가
    private double unitCost;
    // 이 식자재를 쓰는 메뉴 수
    private long menuCount;

    public static MaterialResponseDto fromEntity(Material material){
        MaterialResponseDto dto = new MaterialResponseDto();
        dto.setMaterialNo(material.getMaterialNo());
        dto.setMaterialCode(material.getMaterialCode());
        dto.setMaterialName(material.getMaterialName());
        dto.setMaterialType(material.getMaterialType());
        dto.setMaterialSize(material.getMaterialSize());
        dto.setMaterialPrice(material.getMaterialPrice());
        dto.setMaterialUnit(material.getMaterialUnit());
        dto.setPackQty(material.getPackQty());
        dto.setStorageType(material.getStorageType());
        dto.setSupplyType(material.getSupplyType());
        dto.setShelfLifeDays(material.getShelfLifeDays());
        if (material.getClient() != null) {
            dto.setClientNo(material.getClient().getClientNo());
            dto.setClientName(material.getClient().getClientName());
        }
        dto.setUnitCost(Math.round(material.getUnitCost() * 100) / 100.0);
        return dto;
    }
}
