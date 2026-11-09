package com.example.docksystem_erp.controller.Monthly;

import com.example.docksystem_erp.service.Monthly.MonthlyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// month: 2026-10 형식 (없으면 이번 달)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/monthly")
public class MonthlyController {
    private final MonthlyService monthlyService;

    // 매장 손익계산서 (+ 전월)
    @GetMapping("/pnl")
    public Map<String, Object> pnl(@RequestParam Long storeNo, @RequestParam(required = false) String month) {
        return monthlyService.pnl(storeNo, month);
    }

    // 전 매장 손익 비교
    @GetMapping("/compare")
    public Map<String, Object> compare(@RequestParam(required = false) String month) {
        return monthlyService.compare(month);
    }

    // 한 달 일별 매출
    @GetMapping("/daily")
    public List<Map<String, Object>> daily(@RequestParam Long storeNo, @RequestParam(required = false) String month) {
        return monthlyService.daily(storeNo, month);
    }

    // 메뉴 분석 (storeNo 없으면 전체 매장)
    @GetMapping("/menus")
    public Map<String, Object> menus(@RequestParam(required = false) Long storeNo, @RequestParam(required = false) String month) {
        return monthlyService.menus(storeNo, month);
    }

    public record CloseReq(Long storeNo, String month, String note) {}

    @PostMapping("/closings")
    public Map<String, Object> close(@RequestBody CloseReq req) {
        return monthlyService.close(req.storeNo(), req.month(), req.note());
    }

    @DeleteMapping("/closings/{monthlyNo}")
    public ResponseEntity<Void> cancel(@PathVariable("monthlyNo") Long monthlyNo) {
        monthlyService.cancel(monthlyNo);
        return ResponseEntity.noContent().build();
    }
}
