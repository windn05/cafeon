package com.example.cafeon_pos.repository;

import com.example.cafeon_pos.entity.PosMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PosMessageRepository extends JpaRepository<PosMessage, Long> {
    List<PosMessage> findTop200ByStoreCodeOrderByHqIdDesc(String storeCode);
    boolean existsByStoreCodeAndHqId(String storeCode, Long hqId);
    long countByStoreCodeAndReadAtIsNull(String storeCode);
}
