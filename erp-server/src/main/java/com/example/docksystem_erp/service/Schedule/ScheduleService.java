package com.example.docksystem_erp.service.Schedule;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.entity.Schedule.Shift;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.entity.User.User;
import com.example.docksystem_erp.repository.Schedule.ShiftRepository;
import com.example.docksystem_erp.repository.User.UserRepository;
import com.example.docksystem_erp.service.Sales.OpsGuard;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class ScheduleService {
    // 주 최대 근로 52시간(기본 40 + 연장 12), 주 15시간 이상이면 주휴수당 대상
    public static final double WEEK_LIMIT = 52;
    public static final double HOLIDAY_PAY_MIN = 15;
    public static final String PART_TIME = "파트타임";

    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final OpsGuard guard;

    public static LocalDate weekStart(LocalDate any) {
        return any.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // 주휴수당: 주 15시간 이상 일한 파트타임에게 (주 근무시간 ÷ 40 × 8시간) × 시급, 40시간 상한
    public static long holidayPay(double weekHours, long hourly) {
        if (weekHours < HOLIDAY_PAY_MIN) return 0;
        return Math.round(Math.min(weekHours, 40) / 40 * 8 * hourly);
    }

    // 한 주 근무표: 매장 소속 직원별 7일 근무 + 주간 합계
    @Transactional(readOnly = true)
    public Map<String, Object> week(Long storeNo, LocalDate anyDay) {
        Store store = guard.store(storeNo);
        LocalDate from = weekStart(anyDay);
        LocalDate to = from.plusDays(6);
        List<User> staff = userRepository.findAll().stream()
                .filter(u -> u.getStore() != null && u.getStore().getStoreNo().equals(storeNo))
                .sorted(Comparator.comparing((User u) -> PART_TIME.equals(u.getEmploymentType())).thenComparing(User::getUserName))
                .toList();
        Map<Long, List<Shift>> byUser = new HashMap<>();
        for (Shift s : shiftRepository.findByStore_StoreNoAndWorkDateBetween(storeNo, from, to)) {
            byUser.computeIfAbsent(s.getUser().getUserNo(), k -> new ArrayList<>()).add(s);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        double totalHours = 0;
        long partTimeCost = 0;
        double[] dayHours = new double[7];
        int[] dayPeople = new int[7];
        for (User u : staff) {
            List<Shift> shifts = byUser.getOrDefault(u.getUserNo(), List.of());
            double hours = shifts.stream().mapToDouble(Shift::getWorkHours).sum();
            boolean partTime = PART_TIME.equals(u.getEmploymentType());
            long hourly = partTime && u.getUserSalary() != null ? u.getUserSalary() : 0;
            long pay = partTime ? Math.round(hours * hourly) : 0;
            long holiday = partTime ? holidayPay(hours, hourly) : 0;
            for (Shift s : shifts) {
                int i = (int) (s.getWorkDate().toEpochDay() - from.toEpochDay());
                dayHours[i] += s.getWorkHours();
                dayPeople[i]++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userNo", u.getUserNo());
            row.put("userName", u.getUserName());
            row.put("roleName", u.getRole() == null ? null : u.getRole().getRoleName());
            row.put("userWork", u.getUserWork());
            row.put("employmentType", u.getEmploymentType());
            row.put("hourlyWage", partTime ? hourly : null);
            row.put("shifts", shifts.stream().sorted(Comparator.comparing(Shift::getWorkDate)).map(this::toMap).toList());
            row.put("hours", round1(hours));
            row.put("days", shifts.size());
            row.put("wage", pay);
            row.put("holidayPay", holiday);
            List<String> warnings = new ArrayList<>();
            if (hours > WEEK_LIMIT) warnings.add("주 52시간 초과");
            if (shifts.size() >= 7) warnings.add("주 1회 휴일 없음");
            if (partTime && hours >= HOLIDAY_PAY_MIN) warnings.add("주휴수당 대상");
            row.put("warnings", warnings);
            rows.add(row);
            totalHours += hours;
            partTimeCost += pay + holiday;
        }

        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(Map.of("date", from.plusDays(i), "hours", round1(dayHours[i]), "people", dayPeople[i]));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storeNo", storeNo);
        result.put("storeName", store.getStoreName());
        result.put("weekStart", from);
        result.put("weekEnd", to);
        result.put("days", days);
        result.put("staff", rows);
        result.put("totalHours", round1(totalHours));
        result.put("partTimeCost", partTimeCost);
        return result;
    }

    // 근무 등록 · 수정 (같은 직원 · 같은 날이면 덮어쓴다)
    public Map<String, Object> save(OpsRequests.ShiftReq req) {
        Store store = guard.store(req.storeNo());
        User user = userRepository.findById(req.userNo())
                .orElseThrow(() -> new EntityNotFoundException("해당 직원을 찾을 수 없습니다: " + req.userNo()));
        if (user.getStore() == null || !user.getStore().getStoreNo().equals(store.getStoreNo())) {
            throw new IllegalArgumentException(user.getUserName() + "은(는) " + store.getStoreName() + " 소속이 아닙니다.");
        }
        if (req.workDate() == null || req.startTime() == null || req.endTime() == null) {
            throw new IllegalArgumentException("근무일과 시작·종료 시각을 입력해 주세요.");
        }
        if (!req.endTime().isAfter(req.startTime())) throw new IllegalArgumentException("종료 시각은 시작 시각보다 늦어야 합니다.");

        Shift shift = shiftRepository.findByUser_UserNoAndWorkDate(user.getUserNo(), req.workDate()).orElseGet(Shift::new);
        shift.setStore(store);
        shift.setUser(user);
        shift.setWorkDate(req.workDate());
        shift.setStartTime(req.startTime());
        shift.setEndTime(req.endTime());
        long span = java.time.Duration.between(req.startTime(), req.endTime()).toMinutes();
        int minBreak = span >= 8 * 60 ? 60 : span >= 4 * 60 ? 30 : 0;
        int breakMin = req.breakMinutes() == null ? minBreak : req.breakMinutes();
        if (breakMin < minBreak) {
            throw new IllegalArgumentException("근무 " + (span / 60) + "시간이면 휴게 시간이 최소 " + minBreak + "분 필요합니다.");
        }
        shift.setBreakMinutes(breakMin);
        shift.setPosition(req.position());
        shift.setNote(req.note());
        shiftRepository.save(shift);
        return toMap(shift);
    }

    public void delete(Long shiftNo) {
        if (!shiftRepository.existsById(shiftNo)) throw new EntityNotFoundException("해당 근무를 찾을 수 없습니다: " + shiftNo);
        shiftRepository.deleteById(shiftNo);
    }

    // 한 주 근무를 다른 주로 복사 (이미 근무가 있는 칸은 건너뜀)
    public Map<String, Object> copyWeek(OpsRequests.CopyWeek req) {
        Store store = guard.store(req.storeNo());
        LocalDate from = weekStart(req.fromWeek());
        LocalDate to = weekStart(req.toWeek());
        if (from.equals(to)) throw new IllegalArgumentException("같은 주로는 복사할 수 없습니다.");
        long offset = to.toEpochDay() - from.toEpochDay();
        int copied = 0, skipped = 0;
        for (Shift s : shiftRepository.findByStore_StoreNoAndWorkDateBetween(store.getStoreNo(), from, from.plusDays(6))) {
            LocalDate day = s.getWorkDate().plusDays(offset);
            boolean stillHere = s.getUser().getStore() != null && s.getUser().getStore().getStoreNo().equals(store.getStoreNo());
            if (!stillHere || shiftRepository.findByUser_UserNoAndWorkDate(s.getUser().getUserNo(), day).isPresent()) {
                skipped++;
                continue;
            }
            Shift c = new Shift();
            c.setStore(store);
            c.setUser(s.getUser());
            c.setWorkDate(day);
            c.setStartTime(s.getStartTime());
            c.setEndTime(s.getEndTime());
            c.setBreakMinutes(s.getBreakMinutes());
            c.setPosition(s.getPosition());
            // 메모(연장·교육 등)는 그 주에만 해당해서 복사하지 않는다
            shiftRepository.save(c);
            copied++;
        }
        return Map.of("copied", copied, "skipped", skipped);
    }

    private Map<String, Object> toMap(Shift s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shiftNo", s.getShiftNo());
        m.put("userNo", s.getUser().getUserNo());
        m.put("workDate", s.getWorkDate());
        m.put("startTime", s.getStartTime().toString());
        m.put("endTime", s.getEndTime().toString());
        m.put("breakMinutes", s.getBreakMinutes());
        m.put("position", s.getPosition());
        m.put("note", s.getNote());
        m.put("hours", round1(s.getWorkHours()));
        return m;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
