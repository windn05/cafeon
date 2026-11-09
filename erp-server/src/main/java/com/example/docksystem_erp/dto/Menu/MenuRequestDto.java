package com.example.docksystem_erp.dto.Menu;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 메뉴 등록 · 수정 공용. recipe는 통째로 보내면 서버가 차이만 반영한다.
@Data
public class MenuRequestDto {
    private String menuCode;
    private String menuName;
    private String category;
    private Long price;
    private String status;
    private String description;
    private List<Line> recipe = new ArrayList<>();

    @Data
    public static class Line {
        private Long materialNo;
        private Double quantity;
    }
}
