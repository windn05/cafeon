package com.example.cafeon_pos.repository;

import com.example.cafeon_pos.entity.PosClosing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface PosClosingRepository extends JpaRepository<PosClosing, Long> {
    Optional<PosClosing> findByStoreCodeAndClosingDate(String storeCode, LocalDate closingDate);
}
