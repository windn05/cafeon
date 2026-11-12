package com.example.docksystem_erp.dto.Equipment;

import com.example.docksystem_erp.util.BizClock;

import com.example.docksystem_erp.entity.Equipment.Equipment;
import com.example.docksystem_erp.entity.Equipment.EquipmentType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Data
public class EquipmentResponseDto {
    private Long equipNo;
    private String equipCode;
    private String equipName;
    private EquipmentType type;
    private String typeLabel; // type 한글 값 저장
    private Long equipPrice;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date equipPurchaseDate;
    // 감가상각 (정액법 · 월할 · 잔존가치 0). 오늘 기준으로 계산한다
    private Integer usefulLifeYears;
    private long elapsedMonths;          // 취득 후 지난 달 수 (내용연수를 넘으면 내용연수까지만)
    private long monthlyDepreciation;    // 월 상각액
    private long accumulatedDepreciation; // 상각 누계
    private long bookValue;              // 장부가 = 취득가 - 상각 누계
    private boolean fullyDepreciated;    // 상각 끝남
    private String equipCategory;
    private Long storeNo;
    private String storeName;

    public static EquipmentResponseDto fromEntity(Equipment equipment){
        EquipmentResponseDto dto = new EquipmentResponseDto();
        dto.setEquipNo(equipment.getEquipNo());
        dto.setEquipCode(equipment.getEquipCode());
        dto.setEquipName(equipment.getEquipName());
        dto.setType(equipment.getType());
        dto.setTypeLabel(equipment.getType().getLabel()); // type 한글값 저장 ex) 가동
        dto.setEquipPrice(equipment.getEquipPrice());
        dto.setEquipPurchaseDate(equipment.getEquipPurchaseDate());
        dto.applyDepreciation(equipment, BizClock.today());
        dto.setEquipCategory(equipment.getEquipCategory());
        if (equipment.getStore() != null) {
            dto.setStoreNo(equipment.getStore().getStoreNo());
            dto.setStoreName(equipment.getStore().getStoreName());
        }
        return dto;
    }

    // 정액법 월할 상각: 취득가 ÷ (내용연수 × 12) 를 지난 달 수만큼. 폐기한 자산은 장부가 0
    private void applyDepreciation(Equipment e, LocalDate today) {
        long price = e.getEquipPrice() == null ? 0 : e.getEquipPrice();
        int years = e.getUsefulLifeYears() == null || e.getUsefulLifeYears() < 1 ? 5 : e.getUsefulLifeYears();
        long totalMonths = years * 12L;
        long months = 0;
        if (e.getEquipPurchaseDate() != null) {
            LocalDate bought = e.getEquipPurchaseDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            months = Math.max(0, ChronoUnit.MONTHS.between(bought.withDayOfMonth(1), today.withDayOfMonth(1)));
        }
        months = Math.min(months, totalMonths);
        long accumulated = Math.round(price * (double) months / totalMonths);
        if (e.getType() == EquipmentType.Disposed) accumulated = price;
        setUsefulLifeYears(years);
        setElapsedMonths(months);
        setMonthlyDepreciation(Math.round(price / (double) totalMonths));
        setAccumulatedDepreciation(accumulated);
        setBookValue(price - accumulated);
        setFullyDepreciated(accumulated >= price);
    }

}
