package com.example.docksystem_erp.repository.Monthly;

import com.example.docksystem_erp.entity.Monthly.MonthlyClosing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonthlyClosingRepository extends JpaRepository<MonthlyClosing, Long> {
    Optional<MonthlyClosing> findByStore_StoreNoAndYearMonth(Long storeNo, String yearMonth);
    boolean existsByStore_StoreNoAndYearMonth(Long storeNo, String yearMonth);
}
