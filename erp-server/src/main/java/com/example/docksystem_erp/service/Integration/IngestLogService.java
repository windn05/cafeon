package com.example.docksystem_erp.service.Integration;

import com.example.docksystem_erp.entity.Integration.IngestLog;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Integration.IngestLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 수집 기록은 본 작업이 실패해 되돌려져도 남아야 해서 별도 트랜잭션으로 저장한다
@Service
@RequiredArgsConstructor
public class IngestLogService {
    private final IngestLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Store store, String type, LocalDate businessDate, int count, boolean ok, String message) {
        IngestLog log = new IngestLog();
        log.setStore(store);
        log.setDataType(type);
        log.setBusinessDate(businessDate);
        log.setReceivedAt(LocalDateTime.now());
        log.setRecordCount(count);
        log.setStatus(ok ? "성공" : "실패");
        log.setMessage(message == null ? null : message.length() > 300 ? message.substring(0, 300) : message);
        repository.save(log);
    }
}
