package com.example.docksystem_erp.entity.Inventory;

import com.example.docksystem_erp.entity.Store.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 재고 실사: 장부 재고와 실제로 센 수량을 비교하고, 차이만큼 재고를 맞춘다.
@Entity
@Getter
@Setter
@Table(name = "stocktakes", indexes = @Index(name = "idx_stocktake_store_date", columnList = "store_no,count_date"))
public class Stocktake {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stocktakeNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_no")
    private Store store;
    @Column(nullable = false)
    private LocalDate countDate;
    @Column(length = 300)
    private String note;
    @Column(length = 50)
    private String createdBy;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "stocktake", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<StocktakeLine> lines = new ArrayList<>();
}
