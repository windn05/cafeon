package com.example.docksystem_erp.repository.Equipment;

import com.example.docksystem_erp.entity.Equipment.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment,Long> {
    long countByStore_StoreNo(Long storeNo);
}
