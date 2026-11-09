package com.example.docksystem_erp.entity.Menu;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// 판매 메뉴. 레시피(식자재 × 사용량)로 원가를 계산한다.
@Entity
@Getter
@Setter
@Table(name = "menus")
public class Menu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long menuNo;
    @Column(nullable = false, unique = true, length = 30)
    private String menuCode;
    @Column(nullable = false, length = 50)
    private String menuName;
    // 커피 · 논커피 · 티 · 에이드·주스 · 베이커리 · 디저트
    @Column(nullable = false, length = 30)
    private String category;
    // 판매가 (원, 부가세 포함)
    @Column(nullable = false)
    private Long price;
    // 판매중 · 일시품절 · 단종
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 500)
    private String description;

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("recipeNo ASC")
    private List<RecipeItem> recipeItems = new ArrayList<>();

    // 1잔(1개) 원가
    public double getCost() {
        return recipeItems.stream().mapToDouble(RecipeItem::getCost).sum();
    }
}
