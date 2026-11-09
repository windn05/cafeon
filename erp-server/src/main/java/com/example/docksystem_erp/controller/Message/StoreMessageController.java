package com.example.docksystem_erp.controller.Message;

import com.example.docksystem_erp.service.Message.StoreMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 본사 → 매장 공지 · 지시
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/store-messages")
public class StoreMessageController {
    private final StoreMessageService service;

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) String kind) {
        return service.list(from, to, kind);
    }

    @GetMapping("/{no}")
    public Map<String, Object> detail(@PathVariable Long no) {
        return service.detail(no);
    }

    // 어떤 일로 보낸 메시지 (예: ?ref=closing:3:2026-11-03)
    @GetMapping("/by-ref")
    public List<Map<String, Object>> byRef(@RequestParam String ref) {
        return service.byRef(ref);
    }

    @PostMapping
    public Map<String, Object> send(@RequestBody StoreMessageService.SendReq req) {
        return service.send(req);
    }
}
