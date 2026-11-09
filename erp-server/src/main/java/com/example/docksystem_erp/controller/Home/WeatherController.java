package com.example.docksystem_erp.controller.Home;

import com.example.docksystem_erp.service.Home.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/weather")
public class WeatherController {
    private final WeatherService weatherService;

    @GetMapping
    public Map<String,Object> getWeather(
            @RequestParam double lat,
            @RequestParam double lon
    ){
        return weatherService.getWeather(lat,lon);
    }

}
