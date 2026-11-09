package com.example.docksystem_erp.entity.Schedule;

import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.entity.User.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

// 근무표 한 칸: 직원 1명의 하루 근무 (하루에 1건)
@Entity
@Getter
@Setter
@Table(name = "shifts",
        uniqueConstraints = @UniqueConstraint(name = "uk_shift_user_date", columnNames = {"user_no", "work_date"}),
        indexes = @Index(name = "idx_shift_store_date", columnList = "store_no,work_date"))
public class Shift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shiftNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_no")
    private Store store;
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_no")
    private User user;
    @Column(nullable = false)
    private LocalDate workDate;
    @Column(nullable = false)
    private LocalTime startTime;
    @Column(nullable = false)
    private LocalTime endTime;
    // 휴게 시간(분): 4시간 이상 30분, 8시간 이상 60분이 법정 최소
    @Column(nullable = false)
    private int breakMinutes;
    // 오픈 · 미들 · 마감 · 기타
    @Column(length = 20)
    private String position;
    @Column(length = 100)
    private String note;

    // 실제 근무 시간 (휴게 제외, 시간 단위)
    public double getWorkHours() {
        long minutes = Duration.between(startTime, endTime).toMinutes() - breakMinutes;
        return Math.max(0, minutes) / 60.0;
    }
}
