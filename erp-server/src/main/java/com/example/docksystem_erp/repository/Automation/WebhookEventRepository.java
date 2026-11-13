package com.example.docksystem_erp.repository.Automation;

import com.example.docksystem_erp.entity.Automation.WebhookEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    List<WebhookEvent> findByStatusAndNextAttemptAtLessThanEqualOrderByEventNoAsc(String status, LocalDateTime at, Pageable page);

    List<WebhookEvent> findAllByOrderByEventNoDesc(Pageable page);

    long countByStatus(String status);
}
