package com.example.docksystem_erp.repository.Schedule;

import com.example.docksystem_erp.entity.Schedule.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    List<Shift> findByStore_StoreNoAndWorkDateBetween(Long storeNo, LocalDate from, LocalDate to);
    Optional<Shift> findByUser_UserNoAndWorkDate(Long userNo, LocalDate workDate);
    boolean existsByUser_UserNo(Long userNo);
}
