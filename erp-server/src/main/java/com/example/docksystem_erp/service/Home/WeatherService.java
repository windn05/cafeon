package com.example.docksystem_erp.service.Home;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WeatherService {

    private final RestTemplate restTemplate = new RestTemplate();
    // 키는 설정(weather.api-key)에서 받는다. 비어 있으면 빈 결과를 돌려준다 (홈 화면 날씨 칸만 빈다)
    @Value("${weather.api-key:}")
    private String apiKey;

    public Map<String, Object> getWeather(double lat, double lon){
        if (apiKey == null || apiKey.isBlank()) return new HashMap<>();
        String currentUrl = String.format(
        "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s&units=metric&lang=kr",
        lat, lon, apiKey
        );

        String forecastUrl = String.format(
        "https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f&appid=%s&units=metric&lang=kr",
        lat, lon, apiKey
        );

        Map<String,Object> current = restTemplate.getForObject(currentUrl,Map.class);
        Map<String,Object> forecast = restTemplate.getForObject(forecastUrl,Map.class);

        Map<String,Object> result = new HashMap<>();
        result.put("current",current);
        result.put("forecast",forecast);

        String cityName = (String) current.get("name");
        result.put("city",cityName);

        return result;
    }

}
