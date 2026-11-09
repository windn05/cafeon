package com.example.docksystem_erp.repository.PurchaseOrder;

import com.example.docksystem_erp.entity.PurchaseOrder.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByStore_StoreNoAndOrderDateBetweenOrderByOrderDateDescPoNoDesc(Long storeNo, LocalDate from, LocalDate to);
    List<PurchaseOrder> findByStore_StoreNoAndStatus(Long storeNo, String status);
    long countByStore_StoreNoAndOrderDate(Long storeNo, LocalDate orderDate);
    boolean existsByClient_ClientNo(Long clientNo);
    java.util.Optional<PurchaseOrder> findByPoNumber(String poNumber);
}
