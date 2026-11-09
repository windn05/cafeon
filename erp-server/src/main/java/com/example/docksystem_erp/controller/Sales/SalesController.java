package com.example.docksystem_erp.controller.Sales;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.service.Sales.SalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/sales")
public class SalesController {
    private final SalesService salesService;

    // 하루 영수증 목록
    @GetMapping("/receipts")
    public List<Map<String, Object>> receipts(@RequestParam Long storeNo,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return salesService.receipts(storeNo, date);
    }

    // 하루 판매 요약
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam Long storeNo,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return salesService.summary(storeNo, date);
    }

    // POS 판매 입력
    @PostMapping("/receipts")
    public Map<String, Object> create(@RequestBody OpsRequests.Receipt req) {
        return salesService.createReceipt(req);
    }

    // 영수증 취소
    @DeleteMapping("/receipts/{receiptNo}")
    public ResponseEntity<Void> delete(@PathVariable("receiptNo") String receiptNo) {
        salesService.deleteReceipt(receiptNo);
        return ResponseEntity.noContent().build();
    }
}
