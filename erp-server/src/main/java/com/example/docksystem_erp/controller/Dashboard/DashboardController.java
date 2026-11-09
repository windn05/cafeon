package com.example.docksystem_erp.controller.Dashboard;

import com.example.docksystem_erp.service.Dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    // ERP 홈 화면 데이터 한 번에
    @GetMapping("/home")
    public Map<String, Object> home() {
        return dashboardService.home();
    }
}
