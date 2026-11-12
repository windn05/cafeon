package com.example.docksystem_erp.entity.Material;

import com.example.docksystem_erp.entity.Client.Client;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// 식자재 (원두, 우유, 시럽, 포장재 …)
// 구매는 포장 단위(예: 1kg 1봉)로, 레시피는 사용 단위(예: g)로 계산한다.
@Entity
@Getter
@Setter
@Table(name = "materials")
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long materialNo;
    @Column(nullable = false, unique = true, length = 100)
    private String materialCode;
    @Column(nullable = false, length = 100)
    private String materialName;
    // 분류 (원두·커피, 유제품, 시럽·소스 …)
    @Column(nullable = false, length = 100)
    private String materialType;
    // 구매 포장 단위 설명 (예: 1kg 봉, 1L 팩)
    @Column(nullable = false, length = 50)
    private String materialSize;
    // 포장 1개 구매 단가 (원)
    @Column(nullable = false)
    private Long materialPrice;
    // 레시피에 쓰는 사용 단위 (g, ml, EA)
    @Column(nullable = false, length = 50)
    private String materialUnit;
    // 포장 1개에 들어 있는 사용 단위 수량 (예: 1kg 봉 → 1000 g)
    @Column(nullable = false)
    private Double packQty;
    // 보관 방법: 상온 · 냉장 · 냉동
    @Column(nullable = false, length = 20)
    private String storageType;
    // 개봉 전 유통기한 (일)
    private Integer shelfLifeDays;
    // 공급 방식: 물류센터(본사 재고에서 출고) · 직송(공급업체가 매장으로 바로 배송)
    public static final String SUPPLY_CENTER = "물류센터";
    public static final String SUPPLY_DIRECT = "직송";
    @Column(nullable = false, length = 10)
    private String supplyType = SUPPLY_CENTER;
    // 주 공급업체
    @ManyToOne
    @JoinColumn(name = "client_no")
    private Client client;

    public boolean isDirect() {
        return SUPPLY_DIRECT.equals(supplyType);
    }

    // 사용 단위 1개당 원가 (원)
    public double getUnitCost() {
        if (packQty == null || packQty <= 0 || materialPrice == null) return 0;
        return materialPrice / packQty;
    }
}
