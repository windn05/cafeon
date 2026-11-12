package com.example.docksystem_erp.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// 업무 시계: 평소에는 실제 시각, 매장 영업 시뮬레이션(빠른 재생) 중에는 시뮬레이터가 정한 시각.
// 업무 기록의 날짜 · 시각은 모두 여기서 가져온다 (로그인 토큰 만료 같은 보안 시각은 실제 시계 그대로)
public final class BizClock {
    private static volatile LocalDateTime fixed;

    private BizClock() {}

    public static LocalDateTime now() {
        LocalDateTime f = fixed;
        return f != null ? f : LocalDateTime.now();
    }

    public static LocalDate today() {
        return now().toLocalDate();
    }

    public static LocalTime time() {
        return now().toLocalTime();
    }

    // null이면 실제 시계로 돌아간다
    public static void set(LocalDateTime at) {
        fixed = at;
    }

    public static boolean simulated() {
        return fixed != null;
    }
}
