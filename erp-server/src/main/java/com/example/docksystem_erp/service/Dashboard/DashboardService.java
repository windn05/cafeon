package com.example.docksystem_erp.service.Dashboard;

import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.entity.User.User;
import com.example.docksystem_erp.repository.Monthly.MonthlyClosingRepository;
import com.example.docksystem_erp.repository.Store.StoreRepository;
import com.example.docksystem_erp.repository.User.UserRepository;
import com.example.docksystem_erp.service.Inventory.InventoryService;
import com.example.docksystem_erp.service.Message.StoreMessageService;
import com.example.docksystem_erp.service.Monthly.MonthlyService;
import com.example.docksystem_erp.service.Sales.OpsGuard;
import com.example.docksystem_erp.service.Schedule.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * 본사 ERP 홈: 어제 · 이번 달 숫자, 최근 2주 매출 추이, 매장별 어제 실적, 오늘 할 일.
 * 오늘 할 일은 매일·매주·매월 챙겨야 하는 업무를 데이터로 판단해 알려 준다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    private final JdbcTemplate jdbc;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final MonthlyClosingRepository monthlyRepository;
    private final MonthlyService monthlyService;
    private final InventoryService inventoryService;
    private final StoreMessageService messageService;

    public Map<String, Object> home() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        YearMonth month = YearMonth.from(today);
        List<Store> shops = storeRepository.findAll().stream()
                .filter(s -> OpsGuard.SHOP_TYPES.contains(s.getStoreType()) && !"폐점".equals(s.getStatus()))
                .sorted(Comparator.comparing(Store::getStoreCode)).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", today);
        result.put("yesterday", yesterday);

        // ----- 어제 (전 매장) + 지난주 같은 요일 -----
        result.put("yesterdayTotal", totals(yesterday, yesterday));
        result.put("lastWeekTotal", totals(yesterday.minusDays(7), yesterday.minusDays(7)));

        // ----- 이번 달 누적 (어제까지) vs 지난달 같은 기간 -----
        LocalDate monthStart = month.atDay(1);
        boolean hasMonthDays = !yesterday.isBefore(monthStart);
        Map<String, Object> mtd = hasMonthDays ? totals(monthStart, yesterday) : totals(today, today.minusDays(1));
        YearMonth prev = month.minusMonths(1);
        LocalDate prevEnd = prev.atDay(Math.min(prev.lengthOfMonth(), Math.max(1, yesterday.getDayOfMonth())));
        Map<String, Object> prevMtd = hasMonthDays ? totals(prev.atDay(1), prevEnd) : totals(today, today.minusDays(1));
        result.put("monthToDate", mtd);
        result.put("prevMonthToDate", prevMtd);
        long elapsed = hasMonthDays ? yesterday.getDayOfMonth() : 0;
        result.put("monthElapsedDays", elapsed);
        result.put("monthForecast", elapsed == 0 ? 0 : Math.round(((Number) mtd.get("amount")).doubleValue() / elapsed * month.lengthOfMonth()));

        // ----- 매장별: 어제 실적 + 이번 달 손익 -----
        List<Map<String, Object>> storeRows = new ArrayList<>();
        long opSum = 0, directNet = 0, netSum = 0, foodSum = 0, theoSum = 0;
        for (Store s : shops) {
            Map<String, Object> y = storeTotals(s.getStoreNo(), yesterday);
            Map<String, Object> lw = storeTotals(s.getStoreNo(), yesterday.minusDays(7));
            Map<String, Object> st = monthlyService.statement(s, month);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("storeNo", s.getStoreNo());
            r.put("storeName", s.getStoreName());
            r.put("storeType", s.getStoreType());
            r.put("status", s.getStatus());
            r.put("yesterdayAmount", y.get("amount"));
            r.put("yesterdayReceipts", y.get("receipts"));
            r.put("yesterdayAvgTicket", y.get("avgTicket"));
            r.put("yesterdayCostRate", y.get("costRate"));
            r.put("lastWeekAmount", lw.get("amount"));
            r.put("yesterdayClosed", jdbc.queryForObject(
                    "select count(*) from daily_closings where store_no = ? and closing_date = ?", Long.class, s.getStoreNo(), Date.valueOf(yesterday)) > 0);
            r.put("cashDiff", jdbc.queryForList(
                    "select cash_diff from daily_closings where store_no = ? and closing_date = ?", Long.class, s.getStoreNo(), Date.valueOf(yesterday))
                    .stream().findFirst().orElse(null));
            r.put("monthSales", st.get("salesAmount"));
            r.put("monthOperatingProfit", st.get("operatingProfit"));
            r.put("monthOperatingMargin", st.get("operatingMargin"));
            r.put("monthFoodCostRate", st.get("foodCostRate"));
            r.put("franchise", st.get("franchise"));
            storeRows.add(r);
            // 영업이익은 직영점만 (가맹점 비용은 본사가 모름)
            if (!Boolean.TRUE.equals(st.get("franchise"))) {
                opSum += ((Number) st.get("operatingProfit")).longValue();
                directNet += ((Number) st.get("netSales")).longValue();
            }
            netSum += ((Number) st.get("netSales")).longValue();
            foodSum += ((Number) st.get("foodCost")).longValue();
            theoSum += ((Number) st.get("theoreticalCost")).longValue();
        }
        result.put("stores", storeRows);
        Map<String, Object> monthPnl = new LinkedHashMap<>();
        monthPnl.put("operatingProfit", opSum);
        monthPnl.put("operatingMargin", directNet == 0 ? 0 : round1(opSum * 100.0 / directNet));
        monthPnl.put("foodCostRate", netSum == 0 ? 0 : round1(foodSum * 100.0 / netSum));
        monthPnl.put("theoreticalRate", netSum == 0 ? 0 : round1(theoSum * 100.0 / netSum));
        result.put("monthPnl", monthPnl);

        // ----- 최근 14일 전 매장 일별 매출 (+ 지난주 같은 요일) -----
        LocalDate from = today.minusDays(14);
        Map<LocalDate, Long> daily = new HashMap<>();
        jdbc.queryForList("select sale_date, sum(amount) as amount from sales where sale_date between ? and ? group by sale_date",
                        Date.valueOf(from.minusDays(7)), Date.valueOf(today))
                .forEach(r -> daily.put(((Date) r.get("sale_date")).toLocalDate(), ((Number) r.get("amount")).longValue()));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(yesterday); d = d.plusDays(1)) {
            trend.add(Map.of("date", d, "amount", daily.getOrDefault(d, 0L), "lastWeek", daily.getOrDefault(d.minusDays(7), 0L)));
        }
        result.put("trend", trend);

        result.put("todos", todos(today, shops));
        return result;
    }

    // 오늘 할 일 (danger → warn → info 순)
    private List<Map<String, Object>> todos(LocalDate today, List<Store> shops) {
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDate yesterday = today.minusDays(1);

        for (Store s : shops) {
            // 일 마감 누락 (어제 이전에 판매가 있는데 마감 안 한 날)
            Map<String, Object> u = jdbc.queryForMap(
                    "select count(distinct s.sale_date) as days, min(s.sale_date) as first from sales s where s.store_no = ? and s.sale_date < ? " +
                    "and not exists (select 1 from daily_closings c where c.store_no = s.store_no and c.closing_date = s.sale_date)",
                    s.getStoreNo(), Date.valueOf(today));
            long days = ((Number) u.get("days")).longValue();
            if (days > 0) {
                add(list, "danger", "데이터 수집", s.getStoreName() + " 일 마감 데이터 " + days + "일 미수신",
                        ((Date) u.get("first")).toLocalDate() + "부터 매장 마감이 들어오지 않았습니다. 매장에 확인하세요.", "/erp/monitor/closing", s.getStoreNo());
            }
            // 어제 시재 차이
            jdbc.queryForList("select cash_diff, note from daily_closings where store_no = ? and closing_date = ? and cash_diff <> 0",
                    s.getStoreNo(), Date.valueOf(yesterday)).forEach(r -> {
                long diff = ((Number) r.get("cash_diff")).longValue();
                add(list, "warn", "일 마감", s.getStoreName() + " 어제 현금 시재 " + (diff > 0 ? "+" : "") + String.format("%,d", diff) + "원",
                        r.get("note") == null ? "차이 사유를 확인하세요." : "메모: " + r.get("note"), "/erp/monitor/closing", s.getStoreNo());
            });
            // 재고 부족
            long shortCount = inventoryService.stock(s.getStoreNo()).stream().filter(x -> "부족".equals(x.get("status"))).count();
            if (shortCount > 0) {
                add(list, "warn", "재고", s.getStoreName() + " 재고 부족 " + shortCount + "개 품목", "남은 양이 2일 치도 안 됩니다. 발주 요청·입고 대기를 확인하세요.",
                        "/erp/monitor/inventory", s.getStoreNo());
            }
        }

        // 입고 예정 · 지연 (매장 무관)
        for (Map<String, Object> r : jdbc.queryForList(
                "select p.store_no, s.store_name, sum(case when p.expected_date = ? then 1 else 0 end) as today_cnt, " +
                "sum(case when p.expected_date < ? then 1 else 0 end) as late_cnt from purchase_orders p join stores s on s.store_no = p.store_no " +
                "where p.status = '발주완료' group by p.store_no, s.store_name", Date.valueOf(today), Date.valueOf(today))) {
            long late = ((Number) r.get("late_cnt")).longValue();
            long due = ((Number) r.get("today_cnt")).longValue();
            Long storeNo = ((Number) r.get("store_no")).longValue();
            if (late > 0) add(list, "danger", "구매", r.get("store_name") + " 입고 지연 " + late + "건", "도착 예정일이 지났습니다. 공급업체에 확인하세요.", "/erp/purchase/orders", storeNo);
            if (due > 0) add(list, "info", "구매", r.get("store_name") + " 오늘 입고 예정 " + due + "건", "매장에서 입고 확인을 보내면 재고에 반영됩니다.", "/erp/purchase/orders", storeNo);
        }

        // 매장 발주 요청 처리 대기 (하루 넘게 기다린 요청이 있으면 긴급)
        Map<String, Object> req = jdbc.queryForMap("select count(*) as cnt, min(request_date) as first from order_requests where status = '요청'");
        long reqCount = ((Number) req.get("cnt")).longValue();
        if (reqCount > 0) {
            boolean old = ((Date) req.get("first")).toLocalDate().isBefore(today);
            add(list, old ? "danger" : "warn", "구매", "매장 발주 요청 " + reqCount + "건 처리 대기",
                    "승인하면 공급업체별 발주서가 만들어집니다." + (old ? " 어제 이전 요청이 남아 있습니다." : ""), "/erp/purchase/requests", null);
        }
        // 지시 회신 기한 지남
        List<String> late = messageService.overdueReplies();
        if (!late.isEmpty()) {
            add(list, "warn", "매장 소통", "지시 회신 기한 지남 " + late.size() + "건", String.join(", ", late.subList(0, Math.min(3, late.size())))
                    + (late.size() > 3 ? " 외 " + (late.size() - 3) + "건" : ""), "/erp/messages", null);
        }
        // 정기 발주일 (월·목): 직영점은 본사가 직접 발주
        if (today.getDayOfWeek() == DayOfWeek.MONDAY || today.getDayOfWeek() == DayOfWeek.THURSDAY) {
            add(list, "info", "구매", "오늘은 정기 발주일입니다", "매장 요청을 처리하고, 직영점은 발주 제안을 확인하세요.", "/erp/purchase/orders", null);
        }

        // 지난달 월 마감 대기
        YearMonth last = YearMonth.from(today).minusMonths(1);
        List<String> notClosed = new ArrayList<>();
        for (Store s : shops) {
            Long sales = jdbc.queryForObject("select count(*) from sales where store_no = ? and sale_date between ? and ?",
                    Long.class, s.getStoreNo(), Date.valueOf(last.atDay(1)), Date.valueOf(last.atEndOfMonth()));
            if (sales != null && sales > 0 && !monthlyRepository.existsByStore_StoreNoAndYearMonth(s.getStoreNo(), last.toString())) {
                notClosed.add(s.getStoreName());
            }
        }
        if (!notClosed.isEmpty()) {
            add(list, today.getDayOfMonth() > 10 ? "danger" : "warn", "경영 분석", last.getMonthValue() + "월 월 마감 대기 " + notClosed.size() + "개 매장",
                    String.join(", ", notClosed), "/erp/analysis/pnl", null);
        }

        // 다음 주 근무표 (목요일부터 안내)
        if (today.getDayOfWeek().getValue() >= DayOfWeek.THURSDAY.getValue()) {
            LocalDate nextMon = ScheduleService.weekStart(today).plusDays(7);
            Map<Long, Long> staff = new HashMap<>();
            for (User u : userRepository.findAll()) {
                if (u.getStore() != null) staff.merge(u.getStore().getStoreNo(), 1L, Long::sum);
            }
            List<String> empty = new ArrayList<>();
            for (Store s : shops) {
                if (!"직영점".equals(s.getStoreType()) || !"영업중".equals(s.getStatus()) || staff.getOrDefault(s.getStoreNo(), 0L) == 0) continue;
                Long shifts = jdbc.queryForObject("select count(*) from shifts where store_no = ? and work_date between ? and ?",
                        Long.class, s.getStoreNo(), Date.valueOf(nextMon), Date.valueOf(nextMon.plusDays(6)));
                if (shifts == null || shifts == 0) empty.add(s.getStoreName());
            }
            if (!empty.isEmpty()) {
                add(list, "info", "인사", "다음 주 근무표 미수신 " + empty.size() + "개 직영점", String.join(", ", empty) + " · 매장에 근무표 등록을 요청하세요.",
                        "/erp/monitor/labor", null);
            }
        }

        // 레시피 없는 판매 메뉴
        Long noRecipe = jdbc.queryForObject("select count(*) from menus m where m.status = '판매중' and not exists " +
                "(select 1 from recipe_items r where r.menu_no = m.menu_no)", Long.class);
        if (noRecipe != null && noRecipe > 0) {
            add(list, "info", "기준 정보", "레시피 없는 판매 메뉴 " + noRecipe + "개", "원가가 0원으로 계산되어 손익이 부풀려집니다.", "/erp/standard/menu", null);
        }

        List<String> order = List.of("danger", "warn", "info");
        list.sort(Comparator.comparingInt(m -> order.indexOf((String) m.get("level"))));
        return list;
    }

    private static void add(List<Map<String, Object>> list, String level, String area, String title, String detail, String link, Long storeNo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("area", area);
        m.put("title", title);
        m.put("detail", detail);
        m.put("link", link);
        m.put("storeNo", storeNo);
        list.add(m);
    }

    private Map<String, Object> totals(LocalDate from, LocalDate to) {
        return summarize(jdbc.queryForMap(
                "select coalesce(sum(amount), 0) as amount, count(distinct receipt_no) as receipts, coalesce(sum(unit_cost * quantity), 0) as cost " +
                "from sales where sale_date between ? and ?", Date.valueOf(from), Date.valueOf(to)));
    }

    private Map<String, Object> storeTotals(Long storeNo, LocalDate day) {
        return summarize(jdbc.queryForMap(
                "select coalesce(sum(amount), 0) as amount, count(distinct receipt_no) as receipts, coalesce(sum(unit_cost * quantity), 0) as cost " +
                "from sales where store_no = ? and sale_date = ?", storeNo, Date.valueOf(day)));
    }

    private static Map<String, Object> summarize(Map<String, Object> t) {
        long amount = ((Number) t.get("amount")).longValue();
        long receipts = ((Number) t.get("receipts")).longValue();
        double cost = ((Number) t.get("cost")).doubleValue();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", amount);
        m.put("receipts", receipts);
        m.put("avgTicket", receipts == 0 ? 0 : Math.round((double) amount / receipts));
        // 원가율은 부가세를 뺀 순매출 기준
        m.put("costRate", amount == 0 ? 0 : round1(cost / (amount / 1.1) * 100));
        return m;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
