package com.example.docksystem_erp.repository.Message;

import com.example.docksystem_erp.entity.Message.StoreMessageTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreMessageTargetRepository extends JpaRepository<StoreMessageTarget, Long> {
    List<StoreMessageTarget> findTop100ByStore_StoreNoAndTargetNoGreaterThanOrderByTargetNoAsc(Long storeNo, Long after);
}
