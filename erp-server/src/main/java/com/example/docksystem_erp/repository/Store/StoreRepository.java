package com.example.docksystem_erp.repository.Store;

import com.example.docksystem_erp.entity.Store.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {
    boolean existsByStoreCode(String storeCode);
    Optional<Store> findByIntegrationKey(String integrationKey);
}
