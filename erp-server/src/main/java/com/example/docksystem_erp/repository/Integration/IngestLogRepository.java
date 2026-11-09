package com.example.docksystem_erp.repository.Integration;

import com.example.docksystem_erp.entity.Integration.IngestLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestLogRepository extends JpaRepository<IngestLog, Long> {
}
