package com.example.cafeon_pos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// CafeOn 매장 POS 서버: 매장에서 판매 · 마감 · 폐기 · 실사 · 발주 요청을 받고 본사 ERP와 주고받는다.
@SpringBootApplication
@EnableScheduling
public class PosApplication {
    public static void main(String[] args) {
        SpringApplication.run(PosApplication.class, args);
    }
}
