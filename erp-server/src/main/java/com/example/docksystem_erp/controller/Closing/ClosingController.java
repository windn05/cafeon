package com.example.docksystem_erp.controller.Closing;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.service.Closing.ClosingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/closings")
public class ClosingController {
    private final ClosingService closingService;

    // 날짜별 매출 · 마감 여부
    @GetMapping("/status")
    public List<Map<String, Object>> status(@RequestParam Long storeNo,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return closingService.status(storeNo, from, to);
    }

    // 마감 화면 숫자
    @GetMapping("/preview")
    public Map<String, Object> preview(@RequestParam Long storeNo,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return closingService.preview(storeNo, date);
    }

    @PostMapping
    public Map<String, Object> close(@RequestBody OpsRequests.Closing req) {
        return closingService.close(req);
    }

    @DeleteMapping("/{closingNo}")
    public ResponseEntity<Void> cancel(@PathVariable("closingNo") Long closingNo) {
        closingService.cancel(closingNo);
        return ResponseEntity.noContent().build();
    }
}
