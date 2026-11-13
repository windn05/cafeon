package com.example.docksystem_erp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 웹훅 보관함 전송(WebhookSender)을 주기적으로 돌린다
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
