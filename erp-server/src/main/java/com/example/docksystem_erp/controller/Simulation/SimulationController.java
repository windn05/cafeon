package com.example.docksystem_erp.controller.Simulation;

import com.example.docksystem_erp.service.Simulation.PosRunService;
import com.example.docksystem_erp.service.Simulation.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// 시연·분석용 가상 운영 데이터 (기존 판매·마감·재고 기록을 지우고 다시 만든다)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/simulation")
public class SimulationController {
    private final SimulationService simulationService;
    private final PosRunService posRun;

    @PostMapping("/regenerate")
    public Map<String, Object> regenerate(@RequestParam(defaultValue = "70") int days) {
        return simulationService.regenerate(days);
    }

    // 매장 영업 시뮬레이터: POS API로 하루 영업을 돌려 실제 경로로 데이터를 쌓는다
    // 빠른 재생: 기존 운영 기록을 지우고 오늘 전까지 N일 치를 돌린다 (뒤에서 돌고, 진행은 status로 본다)
    @PostMapping("/pos-run/replay")
    public Map<String, Object> replay(@RequestParam(defaultValue = "70") int days) {
        return posRun.startReplay(days);
    }

    // 날짜별 영업: 달력 (매장별 마지막 마감일 · 이 달 마감한 날)
    @GetMapping("/pos-run/calendar")
    public Map<String, Object> calendar(@RequestParam String month) {
        return posRun.calendar(java.time.YearMonth.parse(month));
    }

    // 날짜별 영업: 설정대로 첫날 손님 · 매출 · 시간대별 손님 미리보기 (실행하면 같은 손님이 온다)
    @PostMapping("/pos-run/preview")
    public Map<String, Object> preview(@RequestBody PosRunService.DayRun req) {
        return posRun.preview(req);
    }

    // 날짜별 영업 실행 (뒤에서 돌고, 진행은 status로 본다)
    @PostMapping("/pos-run/days")
    public Map<String, Object> days(@RequestBody PosRunService.DayRun req) {
        return posRun.startDays(req);
    }

    @PostMapping("/pos-run/stop")
    public Map<String, Object> stop() {
        return posRun.stop();
    }

    @GetMapping("/pos-run/status")
    public Map<String, Object> status() {
        return posRun.status();
    }
}
