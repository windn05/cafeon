package com.example.docksystem_erp.repository.Message;

import com.example.docksystem_erp.entity.Message.StoreMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface StoreMessageRepository extends JpaRepository<StoreMessage, Long> {
    List<StoreMessage> findBySentAtBetweenOrderBySentAtDesc(LocalDateTime from, LocalDateTime to);
    List<StoreMessage> findByRefKeyOrderBySentAtDesc(String refKey);
}
