package com.example.docksystem_erp.controller.Home;

import com.example.docksystem_erp.service.Home.NaverApiService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/erp/v1")
public class HomeController {

    private final NaverApiService naverApiService;

    public HomeController(NaverApiService naverApiService) {
        this.naverApiService = naverApiService;
    }

    @GetMapping("/news")
    public List<Map<String, String>> getNews() {
        // 필터링 로직을 모두 제거하고, 서비스가 가져온 데이터를 그대로 반환합니다.
        return naverApiService.searchNewsTop5("카페 프랜차이즈");
    }
}