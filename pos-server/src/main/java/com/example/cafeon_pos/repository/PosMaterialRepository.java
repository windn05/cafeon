package com.example.cafeon_pos.repository;

import com.example.cafeon_pos.entity.PosMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface PosMaterialRepository extends JpaRepository<PosMaterial, Long> {
    List<PosMaterial> findByStoreCodeOrderByMaterialCodeAsc(String storeCode);
    Optional<PosMaterial> findByStoreCodeAndMaterialCode(String storeCode, String materialCode);
    @Transactional
    void deleteByStoreCode(String storeCode);
}
