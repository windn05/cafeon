package com.example.docksystem_erp.controller.Automation;

import com.example.docksystem_erp.service.Automation.AutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 본사 자동화: 자동화 기록(n8n이 남기고 담당자가 확인) · 웹훅 전송 현황
@RestController
@RequestMapping("/api/erp/v1/automation")
@RequiredArgsConstructor
public class AutomationController {
    private final AutomationService service;

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return service.summary();
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@RequestParam(defaultValue = "200") int limit) {
        return service.logs(limit);
    }

    @PostMapping("/logs")
    public Map<String, Object> createLog(@RequestBody AutomationService.LogReq req) {
        return service.createLog(req);
    }

    @PostMapping("/logs/{logNo}/resolve")
    public Map<String, Object> resolve(@PathVariable("logNo") Long logNo, @RequestBody(required = false) AutomationService.ResolveReq req) {
        return service.resolve(logNo, req);
    }

    @GetMapping("/webhooks")
    public List<Map<String, Object>> webhooks(@RequestParam(defaultValue = "200") int limit) {
        return service.events(limit);
    }

    @PostMapping("/webhooks/{eventNo}/retry")
    public Map<String, Object> retry(@PathVariable("eventNo") Long eventNo) {
        return service.retry(eventNo);
    }

    @PostMapping("/webhooks/test")
    public Map<String, Object> ping() {
        return service.ping();
    }
}
