package com.example.cafeon_pos.repository;

import com.example.cafeon_pos.entity.PosMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface PosMenuRepository extends JpaRepository<PosMenu, Long> {
    List<PosMenu> findByStoreCodeOrderBySortOrderAsc(String storeCode);
    Optional<PosMenu> findByStoreCodeAndMenuCode(String storeCode, String menuCode);
    @Transactional
    void deleteByStoreCode(String storeCode);
}
