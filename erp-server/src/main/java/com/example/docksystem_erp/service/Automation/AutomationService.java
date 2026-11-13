package com.example.docksystem_erp.service.Automation;

import com.example.docksystem_erp.entity.Automation.AutomationLog;
import com.example.docksystem_erp.entity.Automation.WebhookEvent;
import com.example.docksystem_erp.repository.Automation.AutomationLogRepository;
import com.example.docksystem_erp.repository.Automation.WebhookEventRepository;
import com.example.docksystem_erp.util.BizClock;
import com.example.docksystem_erp.util.CurrentUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 자동화 기록(n8n이 남김) · 웹훅 전송 현황
@Service
@Transactional
@RequiredArgsConstructor
public class AutomationService {
    private static final Set<String> LEVELS = Set.of(AutomationLog.INFO, AutomationLog.WARN, AutomationLog.ACTION);

    private final AutomationLogRepository logRepository;
    private final WebhookEventRepository eventRepository;
    private final WebhookPublisher publisher;

    public record LogReq(String workflow, String title, String detail, String level, String actionUrl,
                         String refNumber, String executionId) {}

    public record ResolveReq(String result) {}

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("webhookEnabled", publisher.enabled());
        m.put("webhookUrl", publisher.enabled() ? publisher.url() : null);
        m.put("pendingEvents", eventRepository.countByStatus(WebhookEvent.PENDING));
        m.put("failedEvents", eventRepository.countByStatus(WebhookEvent.FAILED));
        m.put("openActions", logRepository.countByLevelAndStatus(AutomationLog.ACTION, AutomationLog.OPEN));
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> logs(int limit) {
        return logRepository.findAllByOrderByLogNoDesc(PageRequest.of(0, clamp(limit))).stream().map(this::toMap).toList();
    }

    public Map<String, Object> createLog(LogReq req) {
        if (req.workflow() == null || req.workflow().isBlank()) throw new IllegalArgumentException("워크플로 이름이 없습니다.");
        if (req.title() == null || req.title().isBlank()) throw new IllegalArgumentException("제목이 없습니다.");
        String level = req.level() == null || req.level().isBlank() ? AutomationLog.INFO : req.level();
        if (!LEVELS.contains(level)) throw new IllegalArgumentException("구분은 정보 · 주의 · 승인 필요 중 하나여야 합니다.");
        AutomationLog l = new AutomationLog();
        l.setWorkflow(cut(req.workflow(), 50));
        l.setTitle(cut(req.title(), 200));
        l.setDetail(req.detail());
        l.setLevel(level);
        l.setActionUrl(blankToNull(cut(req.actionUrl(), 500)));
        l.setRefNumber(blankToNull(cut(req.refNumber(), 50)));
        l.setExecutionId(blankToNull(cut(req.executionId(), 30)));
        // 정보 · 주의는 남기는 것으로 끝, 승인 필요만 처리 전까지 열어 둔다
        l.setStatus(AutomationLog.ACTION.equals(level) ? AutomationLog.OPEN : AutomationLog.DONE);
        l.setCreatedAt(BizClock.now());
        logRepository.save(l);
        return toMap(l);
    }

    public Map<String, Object> resolve(Long logNo, ResolveReq req) {
        AutomationLog l = logRepository.findById(logNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 자동화 기록을 찾을 수 없습니다: " + logNo));
        l.setStatus(AutomationLog.DONE);
        l.setResult(req == null ? null : cut(req.result(), 300));
        l.setResolvedAt(BizClock.now());
        l.setResolvedBy(CurrentUser.id());
        return toMap(l);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> events(int limit) {
        return eventRepository.findAllByOrderByEventNoDesc(PageRequest.of(0, clamp(limit))).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventNo", e.getEventNo());
            m.put("eventId", e.getEventId());
            m.put("eventType", e.getEventType());
            m.put("status", e.getStatus());
            m.put("attempts", e.getAttempts());
            m.put("maxAttempts", WebhookSender.MAX_ATTEMPTS);
            m.put("nextAttemptAt", e.getNextAttemptAt());
            m.put("lastError", e.getLastError());
            m.put("createdAt", e.getCreatedAt());
            m.put("sentAt", e.getSentAt());
            m.put("payload", e.getPayload());
            return m;
        }).toList();
    }

    // 실패한 이벤트를 다시 보내기 (재시도 횟수는 처음부터)
    public Map<String, Object> retry(Long eventNo) {
        WebhookEvent e = eventRepository.findById(eventNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 이벤트를 찾을 수 없습니다: " + eventNo));
        if (WebhookEvent.SENT.equals(e.getStatus())) throw new IllegalStateException("이미 전송한 이벤트입니다.");
        e.setStatus(WebhookEvent.PENDING);
        e.setAttempts(0);
        e.setNextAttemptAt(LocalDateTime.now());
        return Map.of("eventNo", e.getEventNo(), "status", e.getStatus());
    }

    // 연결 확인용 이벤트
    public Map<String, Object> ping() {
        if (!publisher.enabled()) throw new IllegalStateException("웹훅이 꺼져 있습니다. automation.webhook-url · webhook-secret을 설정해 주세요.");
        publisher.publish(WebhookPublisher.PING, Map.of("by", CurrentUser.id()));
        return Map.of("queued", true);
    }

    private Map<String, Object> toMap(AutomationLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("logNo", l.getLogNo());
        m.put("workflow", l.getWorkflow());
        m.put("title", l.getTitle());
        m.put("detail", l.getDetail());
        m.put("level", l.getLevel());
        m.put("actionUrl", l.getActionUrl());
        m.put("refNumber", l.getRefNumber());
        m.put("executionId", l.getExecutionId());
        m.put("status", l.getStatus());
        m.put("result", l.getResult());
        m.put("createdAt", l.getCreatedAt());
        m.put("resolvedAt", l.getResolvedAt());
        m.put("resolvedBy", l.getResolvedBy());
        return m;
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
