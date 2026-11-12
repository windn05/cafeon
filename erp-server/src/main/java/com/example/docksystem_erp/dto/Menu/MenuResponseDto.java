package com.example.docksystem_erp.dto.Menu;

import com.example.docksystem_erp.entity.Menu.Menu;
import com.example.docksystem_erp.entity.Menu.RecipeItem;
import lombok.Data;

import java.util.List;

@Data
public class MenuResponseDto {
    private Long menuNo;
    private String menuCode;
    private String menuName;
    private String category;
    private Long price;
    private String status;
    private String description;
    // 매장 제조 · 완제품. 완제품은 recipe에 납품 품목 1줄(1개)만 있다
    private String productType;
    private Long productMaterialNo;
    private String productMaterialName;
    private List<Line> recipe;
    // 1개 원가 (원)
    private double cost;
    // 원가율 (%) = 원가 ÷ 판매가 × 100
    private double costRate;

    @Data
    public static class Line {
        private Long recipeNo;
        private Long materialNo;
        private String materialCode;
        private String materialName;
        private String materialUnit;
        private Double quantity;
        private double cost;

        static Line of(RecipeItem item) {
            Line line = new Line();
            line.setRecipeNo(item.getRecipeNo());
            line.setMaterialNo(item.getMaterial().getMaterialNo());
            line.setMaterialCode(item.getMaterial().getMaterialCode());
            line.setMaterialName(item.getMaterial().getMaterialName());
            line.setMaterialUnit(item.getMaterial().getMaterialUnit());
            line.setQuantity(item.getQuantity());
            line.setCost(round(item.getCost()));
            return line;
        }
    }

    public static MenuResponseDto fromEntity(Menu menu) {
        MenuResponseDto dto = new MenuResponseDto();
        dto.setMenuNo(menu.getMenuNo());
        dto.setMenuCode(menu.getMenuCode());
        dto.setMenuName(menu.getMenuName());
        dto.setCategory(menu.getCategory());
        dto.setPrice(menu.getPrice());
        dto.setStatus(menu.getStatus());
        dto.setDescription(menu.getDescription());
        dto.setProductType(menu.productTypeOrDefault());
        if (menu.isFinished() && !menu.getRecipeItems().isEmpty()) {
            dto.setProductMaterialNo(menu.getRecipeItems().get(0).getMaterial().getMaterialNo());
            dto.setProductMaterialName(menu.getRecipeItems().get(0).getMaterial().getMaterialName());
        }
        dto.setRecipe(menu.getRecipeItems().stream().map(Line::of).toList());
        double cost = menu.getCost();
        dto.setCost(round(cost));
        dto.setCostRate(menu.getPrice() == null || menu.getPrice() == 0 ? 0 : round(cost / menu.getPrice() * 100));
        return dto;
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
