package com.example.docksystem_erp.repository.Automation;

import com.example.docksystem_erp.entity.Automation.AutomationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutomationLogRepository extends JpaRepository<AutomationLog, Long> {
    List<AutomationLog> findAllByOrderByLogNoDesc(Pageable page);

    long countByLevelAndStatus(String level, String status);
}
