package com.example.cafeon_pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


// 본사에서 받은 메뉴 · 가격 (본사가 바꾸면 메시지와 함께 다시 받는다)
@Entity
@Getter
@Setter
@Table(name = "pos_menus", uniqueConstraints = @UniqueConstraint(columnNames = {"store_code", "menu_code"}))
public class PosMenu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_code", nullable = false, length = 20)
    private String storeCode;
    @Column(name = "menu_code", nullable = false, length = 30)
    private String menuCode;
    @Column(nullable = false, length = 50)
    private String menuName;
    @Column(length = 20)
    private String category;
    private Long price;
    @Column(length = 10)
    private String status;
    // 매장 제조 · 완제품 (완제품은 레시피 대신 납품 품목 1개)
    @Column(length = 10)
    private String productType;
    @Column(columnDefinition = "TEXT")
    private String recipeJson;
    private int sortOrder;
}
