package com.example.cafeon_pos.repository;

import com.example.cafeon_pos.entity.PosStore;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PosStoreRepository extends JpaRepository<PosStore, Long> {
    Optional<PosStore> findByStoreCode(String storeCode);
}
