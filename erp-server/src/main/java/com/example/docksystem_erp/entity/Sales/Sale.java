package com.example.docksystem_erp.entity.Sales;

import com.example.docksystem_erp.entity.Menu.Menu;
import com.example.docksystem_erp.entity.Store.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

// POS 판매 한 줄 (영수증 1장 = 같은 receiptNo를 가진 여러 줄)
// 메뉴 이름·가격·원가는 판매 시점 값을 그대로 남겨 두어, 나중에 메뉴가 바뀌어도 매출 기록은 변하지 않는다.
@Entity
@Getter
@Setter
@Table(name = "sales", indexes = {
        @Index(name = "idx_sales_store_date", columnList = "store_no,sale_date"),
        @Index(name = "idx_sales_receipt", columnList = "receipt_no"),
        @Index(name = "idx_sales_menu", columnList = "menu_no")
})
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long saleNo;
    @Column(nullable = false, length = 40)
    private String receiptNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_no")
    private Store store;
    @Column(nullable = false)
    private LocalDate saleDate;
    @Column(nullable = false)
    private LocalTime saleTime;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_no")
    private Menu menu;
    @Column(nullable = false, length = 50)
    private String menuName;
    @Column(nullable = false, length = 30)
    private String category;
    @Column(nullable = false)
    private int quantity;
    // 1개 판매가 · 1개 원가 (판매 시점)
    @Column(nullable = false)
    private long unitPrice;
    @Column(nullable = false)
    private double unitCost;
    @Column(nullable = false)
    private long amount;
    // 카드 · 현금 · 간편결제
    @Column(nullable = false, length = 20)
    private String paymentMethod;
    // 매장 · 포장 · 배달
    @Column(nullable = false, length = 20)
    private String channel;
}
