package com.example.docksystem_erp.repository.Inventory;

import com.example.docksystem_erp.entity.Inventory.InventoryTxn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTxnRepository extends JpaRepository<InventoryTxn, Long> {
    boolean existsByMaterial_MaterialNo(Long materialNo);
    boolean existsByStore_StoreNo(Long storeNo);
    void deleteByRefTypeAndRefNo(String refType, Long refNo);
}
