package com.example.docksystem_erp.repository.Closing;

import com.example.docksystem_erp.entity.Closing.DailyClosing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyClosingRepository extends JpaRepository<DailyClosing, Long> {
    Optional<DailyClosing> findByStore_StoreNoAndClosingDate(Long storeNo, LocalDate closingDate);
    boolean existsByStore_StoreNoAndClosingDate(Long storeNo, LocalDate closingDate);
    List<DailyClosing> findByStore_StoreNoAndClosingDateBetweenOrderByClosingDateDesc(Long storeNo, LocalDate from, LocalDate to);
}
