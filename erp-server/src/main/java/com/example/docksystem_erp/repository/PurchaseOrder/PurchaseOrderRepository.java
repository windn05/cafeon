package com.example.docksystem_erp.repository.PurchaseOrder;

import com.example.docksystem_erp.entity.PurchaseOrder.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByStore_StoreNoAndOrderDateBetweenOrderByOrderDateDescPoNoDesc(Long storeNo, LocalDate from, LocalDate to);
    List<PurchaseOrder> findByStore_StoreNoAndStatus(Long storeNo, String status);
    List<PurchaseOrder> findByOrderDateBetweenOrderByOrderDateDescPoNoDesc(LocalDate from, LocalDate to);
    List<PurchaseOrder> findByStatus(String status);
    java.util.Optional<PurchaseOrder> findTopByPoNumberStartingWithOrderByPoNumberDesc(String prefix);
    boolean existsByClient_ClientNo(Long clientNo);
    java.util.Optional<PurchaseOrder> findByPoNumber(String poNumber);
}
