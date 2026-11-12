package com.example.cafeon_pos.controller;

import com.example.cafeon_pos.config.BizClock;
import com.example.cafeon_pos.service.HqClient;
import com.example.cafeon_pos.service.SyncService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 개발 · 시험용: 본사의 매장 영업 시뮬레이터가 POS 업무 시계를 맞추고 POS 기록을 비운다.
// pos.dev.enabled=true(local.properties)이고 같은 PC에서 온 요청만 받는다
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pos/v1/dev")
public class DevController {
    private final JdbcTemplate jdbc;
    private final SyncService sync;

    @Value("${pos.dev.enabled:false}")
    private boolean enabled;

    public record ClockReq(LocalDateTime at) {}

    // 업무 시계 맞추기 (at이 없으면 실제 시계)
    @PostMapping("/clock")
    public Map<String, Object> clock(HttpServletRequest request, @RequestBody ClockReq req) {
        guard(request);
        BizClock.set(req.at());
        return Map.of("now", BizClock.now(), "simulated", BizClock.simulated());
    }

    // 판매 · 마감 · 전송 기록 · 본사 메시지를 비운다 (기기 등록 · 메뉴는 유지)
    @PostMapping("/reset")
    public Map<String, Object> reset(HttpServletRequest request) {
        guard(request);
        Map<String, Object> r = new LinkedHashMap<>();
        for (String t : List.of("pos_receipt_lines", "pos_receipts", "pos_closings", "pos_outbox", "pos_messages")) {
            r.put(t, jdbc.update("delete from " + t));
        }
        jdbc.update("update pos_stores set inbox_cursor = 0, snapshot_json = null, snapshot_at = null, last_error = null");
        return r;
    }

    public record OfflineReq(boolean on) {}

    // 본사 연결 끊김 흉내: 켜 두는 동안 POS는 판매를 받고 전송은 대기열에 쌓는다
    @PostMapping("/offline")
    public Map<String, Object> offline(HttpServletRequest request, @RequestBody OfflineReq req) {
        guard(request);
        HqClient.setSimulatedOffline(req.on());
        return Map.of("offline", req.on());
    }

    public record RewindReq(java.time.LocalDate from) {}

    // 이 날부터 이후의 판매 · 마감 · 전송 기록을 지운다 (본사도 같은 날부터 지운 뒤 다시 쌓을 때)
    @PostMapping("/rewind")
    public Map<String, Object> rewind(HttpServletRequest request, @RequestBody RewindReq req) {
        guard(request);
        java.sql.Date d = java.sql.Date.valueOf(req.from());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("pos_receipt_lines", jdbc.update("delete l from pos_receipt_lines l join pos_receipts p on p.id = l.receipt_id where p.sale_date >= ?", d));
        r.put("pos_receipts", jdbc.update("delete from pos_receipts where sale_date >= ?", d));
        r.put("pos_closings", jdbc.update("delete from pos_closings where closing_date >= ?", d));
        r.put("pos_outbox", jdbc.update("delete from pos_outbox where created_at >= ?", d));
        // 본사도 이 날부터 보낸 메시지를 지우므로, POS에 받아 둔 사본도 지운다 (남겨 두면 확인 · 회신이 실패한다)
        r.put("pos_messages", jdbc.update("delete from pos_messages where sent_at >= ?", d));
        jdbc.update("update pos_stores set snapshot_json = null, snapshot_at = null");
        return r;
    }

    // 모든 매장: 쌓인 전송을 바로 보내고 본사 데이터를 받는다
    @PostMapping("/sync-all")
    public Map<String, Object> syncAll(HttpServletRequest request) {
        guard(request);
        List<String> codes = jdbc.queryForList("select store_code from pos_stores where key_rejected = false", String.class);
        codes.forEach(sync::syncNow);
        return Map.of("stores", codes);
    }

    private void guard(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        boolean local = "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
        if (!enabled || !local) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
