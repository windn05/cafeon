package com.example.docksystem_erp.controller.OrderRequest;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.service.OrderRequest.OrderRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 본사: 매장 발주 요청 처리
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/order-requests")
public class OrderRequestController {
    private final OrderRequestService service;

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) Long storeNo) {
        return service.list(from, to, status, storeNo);
    }

    @GetMapping("/pending")
    public List<Map<String, Object>> pending() {
        return service.pending();
    }

    public record ApproveReq(List<OpsRequests.ReceiveLine> lines) {}

    public record RejectReq(String reason) {}

    // lines: [{lineNo, receivedPacks(=승인 수량)}] — 비우면 요청 수량 그대로 승인
    @PostMapping("/{requestNo}/approve")
    public Map<String, Object> approve(@PathVariable("requestNo") Long requestNo, @RequestBody(required = false) ApproveReq req) {
        return service.approve(requestNo, req == null ? null : req.lines());
    }

    @PostMapping("/{requestNo}/reject")
    public Map<String, Object> reject(@PathVariable("requestNo") Long requestNo, @RequestBody RejectReq req) {
        return service.reject(requestNo, req.reason());
    }
}
