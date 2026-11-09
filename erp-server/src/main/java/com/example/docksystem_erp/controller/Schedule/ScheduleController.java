package com.example.docksystem_erp.controller.Schedule;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.service.Schedule.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/shifts")
public class ScheduleController {
    private final ScheduleService scheduleService;

    // 한 주 근무표 (week: 그 주의 아무 날짜)
    @GetMapping("/week")
    public Map<String, Object> week(@RequestParam Long storeNo,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return scheduleService.week(storeNo, week);
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody OpsRequests.ShiftReq req) {
        return scheduleService.save(req);
    }

    @DeleteMapping("/{shiftNo}")
    public ResponseEntity<Void> delete(@PathVariable("shiftNo") Long shiftNo) {
        scheduleService.delete(shiftNo);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/copy-week")
    public Map<String, Object> copyWeek(@RequestBody OpsRequests.CopyWeek req) {
        return scheduleService.copyWeek(req);
    }
}
