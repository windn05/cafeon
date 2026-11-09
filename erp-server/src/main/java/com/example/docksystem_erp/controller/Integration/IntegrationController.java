package com.example.docksystem_erp.controller.Integration;

import com.example.docksystem_erp.service.Integration.IntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 본사: 데이터 수집 현황 · 매장 연동 키
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/integration")
public class IntegrationController {
    private final IntegrationService service;

    @GetMapping("/status")
    public List<Map<String, Object>> status() {
        return service.status();
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@RequestParam(required = false) Long storeNo,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "100") int limit) {
        return service.logs(storeNo, status, limit);
    }

    // 연동 키 새로 발급 (이전 키는 바로 못 쓰게 됨)
    @PostMapping("/stores/{storeNo}/key")
    public Map<String, Object> reissue(@PathVariable("storeNo") Long storeNo) {
        return service.reissue(storeNo);
    }
}
