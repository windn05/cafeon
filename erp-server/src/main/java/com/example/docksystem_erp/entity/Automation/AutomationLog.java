package com.example.docksystem_erp.entity.Automation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 자동화(n8n)가 한 일 · 사람이 확인할 일 기록. 본사 담당자는 ERP 화면에서 이 목록만 보면 된다.
@Entity
@Getter
@Setter
@Table(name = "automation_logs")
public class AutomationLog {
    public static final String INFO = "정보";
    public static final String WARN = "주의";
    public static final String ACTION = "승인 필요";

    public static final String OPEN = "열림";
    public static final String DONE = "처리됨";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logNo;

    // 어느 워크플로가 남긴 기록인지 (예: 아침 점검, 긴급 발주 처리)
    @Column(nullable = false, length = 50)
    private String workflow;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(nullable = false, length = 10)
    private String level = INFO;

    // 승인 필요: 사람이 결정하는 화면 주소 (n8n 승인 폼)
    @Column(length = 500)
    private String actionUrl;

    // 관련 문서 (예: RQ-261112-001)
    @Column(length = 50)
    private String refNumber;

    // n8n 실행 번호 (실행 기록과 맞춰 볼 때)
    @Column(length = 30)
    private String executionId;

    @Column(nullable = false, length = 10)
    private String status = OPEN;

    @Column(length = 300)
    private String result;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    @Column(length = 50)
    private String resolvedBy;
}
