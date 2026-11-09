package com.example.cafeon_pos.repository;

import com.example.cafeon_pos.entity.PosOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface PosOutboxRepository extends JpaRepository<PosOutbox, Long> {
    List<PosOutbox> findByStoreCodeAndStatusOrderByIdAsc(String storeCode, String status);
    List<PosOutbox> findTop100ByStoreCodeOrderByIdDesc(String storeCode);
    long countByStoreCodeAndStatus(String storeCode, String status);
    boolean existsByStoreCodeAndDataTypeAndRefNoAndStatusIn(String storeCode, String dataType, String refNo, Collection<String> statuses);
}
