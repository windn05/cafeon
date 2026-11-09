package com.example.docksystem_erp.repository.Material;

import com.example.docksystem_erp.entity.Material.Material;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material,Long> {
    boolean existsByMaterialCode(String materialCode);
    java.util.Optional<Material> findByMaterialCode(String materialCode);
    long countByClient_ClientNo(Long clientNo);
}
