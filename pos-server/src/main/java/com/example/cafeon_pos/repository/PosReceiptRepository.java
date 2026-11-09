package com.example.cafeon_pos.repository;

import com.example.cafeon_pos.entity.PosReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PosReceiptRepository extends JpaRepository<PosReceipt, Long> {
    List<PosReceipt> findByStoreCodeAndSaleDateOrderByIdDesc(String storeCode, LocalDate saleDate);
    long countByStoreCodeAndSaleDate(String storeCode, LocalDate saleDate);
    Optional<PosReceipt> findByReceiptNo(String receiptNo);
}
