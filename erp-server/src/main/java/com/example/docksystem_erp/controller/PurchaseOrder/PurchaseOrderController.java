package com.example.docksystem_erp.controller.PurchaseOrder;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.service.PurchaseOrder.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/purchase-orders")
public class PurchaseOrderController {
    private final PurchaseOrderService poService;

    // 발주 목록 (발주일 기준 기간, 상태 선택)
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam Long storeNo,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) String status) {
        return poService.list(storeNo, from, to, status);
    }

    // 매장으로 가는 배송 (물류센터 출고 · 공급업체 직송)
    @GetMapping("/deliveries")
    public List<Map<String, Object>> deliveries(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return poService.deliveries(from, to);
    }

    // 입고 대기 중인 발주
    @GetMapping("/open")
    public List<Map<String, Object>> open(@RequestParam Long storeNo) {
        return poService.open(storeNo);
    }

    // 주간 발주 제안 (cycleDays: 다음 발주까지 일수)
    @GetMapping("/suggestion")
    public List<Map<String, Object>> suggestion(@RequestParam Long storeNo, @RequestParam(defaultValue = "7") int cycleDays) {
        return poService.suggestion(storeNo, cycleDays);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody OpsRequests.PoCreate req) {
        return poService.create(req);
    }

    @PostMapping("/{poNo}/receive")
    public Map<String, Object> receive(@PathVariable("poNo") Long poNo, @RequestBody OpsRequests.PoReceive req) {
        return poService.receive(poNo, req);
    }

    @PostMapping("/{poNo}/cancel")
    public Map<String, Object> cancel(@PathVariable("poNo") Long poNo) {
        return poService.cancel(poNo);
    }
}
