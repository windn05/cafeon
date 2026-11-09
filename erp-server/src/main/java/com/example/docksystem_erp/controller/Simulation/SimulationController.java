package com.example.docksystem_erp.controller.Simulation;

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

    @PostMapping("/regenerate")
    public Map<String, Object> regenerate(@RequestParam(defaultValue = "70") int days) {
        return simulationService.regenerate(days);
    }
}
