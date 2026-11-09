package com.example.docksystem_erp.dto.Equipment;

import com.example.docksystem_erp.entity.Equipment.EquipmentType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class EquipmentUpdateRequestDto {
    private String equipCode;
    private String equipName;
    private EquipmentType type;
    private Long equipPrice;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date equipPurchaseDate;
    private Integer usefulLifeYears;
    private String equipCategory;
    private Long storeNo;
}
