package com.example.docksystem_erp.repository.Sales;

import com.example.docksystem_erp.entity.Sales.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    List<Sale> findByReceiptNo(String receiptNo);
    boolean existsByReceiptNo(String receiptNo);
    boolean existsByStore_StoreNo(Long storeNo);
    boolean existsByMenu_MenuNo(Long menuNo);
}
