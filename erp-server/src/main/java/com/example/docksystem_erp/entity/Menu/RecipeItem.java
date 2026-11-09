package com.example.docksystem_erp.entity.Menu;

import com.example.docksystem_erp.entity.Material.Material;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// 레시피 한 줄: 메뉴 1개에 들어가는 식자재와 사용량
@Entity
@Getter
@Setter
@Table(name = "recipe_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"menu_no", "material_no"}))
public class RecipeItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recipeNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_no")
    private Menu menu;
    @ManyToOne(optional = false)
    @JoinColumn(name = "material_no")
    private Material material;
    // 사용량 (식자재의 사용 단위 기준)
    @Column(nullable = false)
    private Double quantity;

    public double getCost() {
        return quantity * material.getUnitCost();
    }
}
