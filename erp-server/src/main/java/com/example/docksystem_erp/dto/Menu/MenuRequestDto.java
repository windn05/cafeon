package com.example.docksystem_erp.dto.Menu;

import lombok.Data;

import java.util.List;

// 메뉴 등록 · 수정 공용. recipe를 보내지 않으면(null) 레시피는 그대로 둔다 (레시피는 레시피 화면에서 따로 관리)
@Data
public class MenuRequestDto {
    private String menuCode;
    private String menuName;
    private String category;
    private Long price;
    private String status;
    private String description;
    // 매장 제조 · 완제품. 완제품이면 납품 품목(식자재, 개 단위)을 고른다
    private String productType;
    private Long productMaterialNo;
    private List<Line> recipe;

    @Data
    public static class Line {
        private Long materialNo;
        private Double quantity;
    }
}
