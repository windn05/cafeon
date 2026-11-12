package com.example.docksystem_erp.service.Monthly;

import com.example.docksystem_erp.util.BizClock;

import com.example.docksystem_erp.entity.Inventory.InventoryTxn;
import com.example.docksystem_erp.entity.Monthly.MonthlyClosing;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.entity.User.User;
import com.example.docksystem_erp.repository.Monthly.MonthlyClosingRepository;
import com.example.docksystem_erp.repository.Store.StoreRepository;
import com.example.docksystem_erp.repository.User.UserRepository;
import com.example.docksystem_erp.service.Sales.OpsGuard;
import com.example.docksystem_erp.service.Schedule.ScheduleService;
import com.example.docksystem_erp.util.CurrentUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 월간 손익 (매장 손익계산서)
 *  매출(부가세 포함) − 부가세 = 순매출
 *  순매출 − 식자재 원가(판매소진 + 폐기 + 실사 로스) = 매출총이익
 *  매출총이익 − 인건비 − 임차료 − 기타 고정비 − 결제·배달 수수료 = 영업이익
 * 진행 중인 달은 어제(마감 기준)까지 집계하고, 고정비·월급은 지난 날 수만큼 나눠 반영한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MonthlyService {
    // 카드·간편결제 수수료, 배달앱 수수료 (추정치)
    public static final double CARD_FEE_RATE = 0.015;
    public static final double DELIVERY_FEE_RATE = 0.12;

    private final JdbcTemplate jdbc;
    private final OpsGuard guard;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final MonthlyClosingRepository monthlyRepository;

    public static YearMonth parse(String month) {
        try {
            return month == null || month.isBlank() ? YearMonth.from(BizClock.today()) : YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("월은 2026-10 형식으로 입력해 주세요.");
        }
    }

    // 매장 한 곳의 한 달 손익 + 전월 비교
    @Transactional(readOnly = true)
    public Map<String, Object> pnl(Long storeNo, String month) {
        Store store = guard.store(storeNo);
        YearMonth ym = parse(month);
        Map<String, Object> cur = statement(store, ym);
        Map<String, Object> prev = statement(store, ym.minusMonths(1));
        cur.put("previous", prev);
        return cur;
    }

    // 모든 판매 매장 비교
    @Transactional(readOnly = true)
    public Map<String, Object> compare(String month) {
        YearMonth ym = parse(month);
        List<Map<String, Object>> rows = new ArrayList<>();
        // total: 전 매장 매출 · 원가 / directTotal: 직영점 손익 (가맹점 인건비 · 임차료는 점주 정보라 제외)
        Map<String, Long> totals = new LinkedHashMap<>();
        Map<String, Long> direct = new LinkedHashMap<>();
        for (Store s : storeRepository.findAll()) {
            if (!OpsGuard.SHOP_TYPES.contains(s.getStoreType())) continue;
            Map<String, Object> st = statement(s, ym);
            if ("폐점".equals(s.getStatus()) && ((Number) st.get("salesAmount")).longValue() == 0) continue;
            Map<String, Object> prev = statement(s, ym.minusMonths(1));
            st.put("prevSalesAmount", prev.get("salesAmount"));
            st.put("prevOperatingProfit", prev.get("operatingProfit"));
            rows.add(st);
            for (String k : List.of("salesAmount", "netSales", "foodCost", "theoreticalCost", "wasteAmount", "lossAmount", "receipts", "purchaseAmount")) {
                totals.merge(k, ((Number) st.get(k)).longValue(), Long::sum);
            }
            totals.merge("prevSalesAmount", ((Number) prev.get("salesAmount")).longValue(), Long::sum);
            totals.merge("laborCost", 0L, Long::sum);
            totals.merge("operatingProfit", 0L, Long::sum);
            if (!Boolean.TRUE.equals(st.get("franchise"))) {
                for (String k : List.of("salesAmount", "netSales", "foodCost", "theoreticalCost", "laborCost", "rent", "fixedCost",
                        "feeAmount", "operatingProfit", "receipts")) {
                    direct.merge(k, ((Number) st.get(k)).longValue(), Long::sum);
                }
                direct.merge("prevOperatingProfit", ((Number) prev.get("operatingProfit")).longValue(), Long::sum);
            }
        }
        rows.sort((a, b) -> Long.compare(((Number) b.get("salesAmount")).longValue(), ((Number) a.get("salesAmount")).longValue()));
        Map<String, Object> total = new LinkedHashMap<>(totals);
        addRatios(total);
        for (String k : List.of("salesAmount", "netSales", "foodCost", "theoreticalCost", "laborCost", "operatingProfit")) direct.putIfAbsent(k, 0L);
        Map<String, Object> directTotal = new LinkedHashMap<>(direct);
        addRatios(directTotal);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", ym.toString());
        result.put("stores", rows);
        result.put("total", total);
        result.put("directTotal", directTotal);
        Map<String, Object> basis = basis(ym);
        result.put("from", basis.get("from"));
        result.put("to", basis.get("to"));
        result.put("inProgress", basis.get("inProgress"));
        return result;
    }

    // 한 달 일별 매출 (마감 여부 포함)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> daily(Long storeNo, String month) {
        guard.store(storeNo);
        YearMonth ym = parse(month);
        return jdbc.queryForList(
                "select s.sale_date as date, sum(s.amount) as amount, count(distinct s.receipt_no) as receipts, " +
                "round(sum(s.unit_cost * s.quantity)) as cost, " +
                "max(case when c.closing_no is null then 0 else 1 end) as closed " +
                "from sales s left join daily_closings c on c.store_no = s.store_no and c.closing_date = s.sale_date " +
                "where s.store_no = ? and s.sale_date between ? and ? group by s.sale_date order by s.sale_date",
                storeNo, Date.valueOf(ym.atDay(1)), Date.valueOf(ym.atEndOfMonth()));
    }

    /**
     * 메뉴 분석 (메뉴 엔지니어링): 인기도(판매량 비중)와 수익성(1개당 공헌이익)으로 4가지로 나눈다.
     *  - 인기 기준: 메뉴 수가 N개면 판매량 비중 (1/N) × 70% 이상
     *  - 수익 기준: 판매량 가중 평균 공헌이익 이상
     * storeNo가 없으면 전체 판매 매장 합계.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> menus(Long storeNo, String month) {
        YearMonth ym = parse(month);
        Map<String, Object> basis = basis(ym);
        LocalDate from = (LocalDate) basis.get("from");
        LocalDate to = (LocalDate) basis.get("to");
        String where = storeNo == null ? "" : " and store_no = ?";
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(to)));
        if (storeNo != null) {
            guard.store(storeNo);
            args.add(storeNo);
        }
        List<Map<String, Object>> rows = to.isBefore(from) ? new ArrayList<>() : jdbc.queryForList(
                "select menu_no as menuNo, menu_name as menuName, category, sum(quantity) as quantity, sum(amount) as amount, " +
                "sum(unit_cost * quantity) as cost from sales where sale_date between ? and ?" + where +
                " group by menu_no, menu_name, category", args.toArray());

        long totalQty = rows.stream().mapToLong(r -> ((Number) r.get("quantity")).longValue()).sum();
        double totalMargin = 0;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long qty = ((Number) r.get("quantity")).longValue();
            long amount = ((Number) r.get("amount")).longValue();
            double cost = ((Number) r.get("cost")).doubleValue();
            double net = amount / 1.1;
            double margin = net - cost;
            totalMargin += margin;
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.put("cost", Math.round(cost));
            m.put("netSales", Math.round(net));
            m.put("margin", Math.round(margin));
            m.put("unitPrice", qty == 0 ? 0 : Math.round((double) amount / qty));
            m.put("unitMargin", qty == 0 ? 0 : Math.round(margin / qty));
            m.put("costRate", net == 0 ? 0 : round1(cost / net * 100));
            m.put("qtyShare", totalQty == 0 ? 0 : round1(qty * 100.0 / totalQty));
            out.add(m);
        }
        int n = out.size();
        double popLine = n == 0 ? 0 : 100.0 / n * 0.7;
        double marginLine = totalQty == 0 ? 0 : totalMargin / totalQty;
        for (Map<String, Object> m : out) {
            boolean popular = ((Number) m.get("qtyShare")).doubleValue() >= popLine;
            boolean profitable = ((Number) m.get("unitMargin")).doubleValue() >= marginLine;
            m.put("type", popular && profitable ? "효자 메뉴" : popular ? "박리다매" : profitable ? "숨은 보석" : "정리 검토");
        }
        out.sort((a, b) -> Long.compare(((Number) b.get("margin")).longValue(), ((Number) a.get("margin")).longValue()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", ym.toString());
        result.put("from", from);
        result.put("to", to);
        result.put("storeNo", storeNo);
        result.put("menus", out);
        result.put("totalQuantity", totalQty);
        result.put("totalMargin", Math.round(totalMargin));
        result.put("popularityLine", round1(popLine));
        result.put("marginLine", Math.round(marginLine));
        return result;
    }

    // 월 마감 확정
    public Map<String, Object> close(Long storeNo, String month, String note) {
        Store store = guard.store(storeNo);
        YearMonth ym = parse(month);
        if (!ym.atEndOfMonth().isBefore(BizClock.today())) {
            throw new IllegalStateException(ym + "은(는) 아직 끝나지 않은 달이라 마감할 수 없습니다.");
        }
        if (monthlyRepository.existsByStore_StoreNoAndYearMonth(storeNo, ym.toString())) {
            throw new IllegalStateException(store.getStoreName() + " " + ym + "은(는) 이미 월 마감했습니다.");
        }
        Map<String, Object> st = statement(store, ym);
        long unclosed = ((Number) st.get("unclosedDays")).longValue();
        if (unclosed > 0) {
            throw new IllegalStateException("일 마감하지 않은 날이 " + unclosed + "일 있습니다. 일 마감을 모두 끝낸 뒤 월 마감하세요.");
        }
        MonthlyClosing mc = new MonthlyClosing();
        mc.setStore(store);
        mc.setYearMonth(ym.toString());
        mc.setSalesAmount(num(st, "salesAmount"));
        mc.setNetSales(num(st, "netSales"));
        mc.setFoodCost(num(st, "foodCost"));
        mc.setTheoreticalCost(num(st, "theoreticalCost"));
        mc.setWasteAmount(num(st, "wasteAmount"));
        mc.setLossAmount(num(st, "lossAmount"));
        mc.setLaborCost(num(st, "laborCost"));
        mc.setRent(num(st, "rent"));
        mc.setFixedCost(num(st, "fixedCost"));
        mc.setFeeAmount(num(st, "feeAmount"));
        mc.setOperatingProfit(num(st, "operatingProfit"));
        mc.setReceiptCount((int) num(st, "receipts"));
        mc.setNote(note);
        mc.setClosedBy(CurrentUser.id());
        mc.setClosedAt(BizClock.now());
        monthlyRepository.save(mc);
        return closingMap(mc);
    }

    public void cancel(Long monthlyNo) {
        MonthlyClosing mc = monthlyRepository.findById(monthlyNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 월 마감을 찾을 수 없습니다: " + monthlyNo));
        monthlyRepository.delete(mc);
    }

    // ----- 손익 계산 -----

    // 집계 기간: 지난 달은 1일~말일, 진행 중인 달은 1일~어제
    private Map<String, Object> basis(YearMonth ym) {
        LocalDate today = BizClock.today();
        LocalDate from = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        if (from.isAfter(today)) throw new IllegalArgumentException("아직 오지 않은 달입니다: " + ym);
        boolean inProgress = !end.isBefore(today);
        LocalDate to = inProgress ? today.minusDays(1) : end;
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("from", from);
        b.put("to", to);
        b.put("inProgress", inProgress);
        b.put("days", Math.max(0, to.toEpochDay() - from.toEpochDay() + 1));
        b.put("daysInMonth", ym.lengthOfMonth());
        return b;
    }

    public Map<String, Object> statement(Store store, YearMonth ym) {
        Map<String, Object> b = basis(ym);
        LocalDate from = (LocalDate) b.get("from");
        LocalDate to = (LocalDate) b.get("to");
        long days = (Long) b.get("days");
        int dim = (Integer) b.get("daysInMonth");
        double ratio = days / (double) dim;
        Long storeNo = store.getStoreNo();
        Date f = Date.valueOf(from), t = Date.valueOf(to);

        Map<String, Object> s = days == 0 ? Map.of() : jdbc.queryForMap(
                "select coalesce(sum(amount), 0) as amount, count(distinct receipt_no) as receipts, " +
                "coalesce(sum(unit_cost * quantity), 0) as cost, " +
                "coalesce(sum(case when channel <> '배달' and payment_method in ('카드', '간편결제') then amount end), 0) as cardAmount, " +
                "coalesce(sum(case when channel = '배달' then amount end), 0) as deliveryAmount " +
                "from sales where store_no = ? and sale_date between ? and ?", storeNo, f, t);
        Map<String, Double> txn = new HashMap<>();
        if (days > 0) {
            jdbc.queryForList("select txn_type, sum(-quantity * unit_cost) as amount from inventory_txns " +
                            "where store_no = ? and txn_date between ? and ? group by txn_type", storeNo, f, t)
                    .forEach(r -> txn.put((String) r.get("txn_type"), ((Number) r.get("amount")).doubleValue()));
        }

        long sales = days == 0 ? 0 : ((Number) s.get("amount")).longValue();
        long receipts = days == 0 ? 0 : ((Number) s.get("receipts")).longValue();
        long vat = Math.round(sales / 11.0);
        long net = sales - vat;
        long theoretical = days == 0 ? 0 : Math.round(((Number) s.get("cost")).doubleValue());
        long consume = Math.round(txn.getOrDefault(InventoryTxn.CONSUME, 0.0));
        long waste = Math.round(txn.getOrDefault(InventoryTxn.WASTE, 0.0));
        long loss = Math.round(txn.getOrDefault(InventoryTxn.ADJUST, 0.0)); // 양수 = 모자람
        long purchases = Math.round(-txn.getOrDefault(InventoryTxn.RECEIPT, 0.0));
        long food = consume + waste + loss;
        long gross = net - food;

        // 가맹점: 인건비 · 임차료 · 고정비 · 수수료는 점주의 사업 정보라 본사 손익에 넣지 않는다 (매출 · 원가 실적만)
        boolean franchise = "가맹점".equals(store.getStoreType());
        Map<String, Object> labor = days == 0 || franchise
                ? Map.of("fullTime", 0L, "partTime", 0L, "holidayPay", 0L, "fullTimeCount", 0, "partTimeHours", 0.0)
                : labor(store, from, to, ratio);
        long laborCost = (Long) labor.get("fullTime") + (Long) labor.get("partTime") + (Long) labor.get("holidayPay");
        long rent = franchise ? 0 : Math.round((store.getMonthlyRent() == null ? 0 : store.getMonthlyRent()) * ratio);
        long fixed = franchise ? 0 : Math.round((store.getMonthlyFixedCost() == null ? 0 : store.getMonthlyFixedCost()) * ratio);
        long cardFee = days == 0 || franchise ? 0 : Math.round(((Number) s.get("cardAmount")).doubleValue() * CARD_FEE_RATE);
        long deliveryFee = days == 0 || franchise ? 0 : Math.round(((Number) s.get("deliveryAmount")).doubleValue() * DELIVERY_FEE_RATE);
        long op = franchise ? 0 : gross - laborCost - rent - fixed - cardFee - deliveryFee;

        Long unclosed = days == 0 ? 0L : jdbc.queryForObject(
                "select count(distinct s.sale_date) from sales s where s.store_no = ? and s.sale_date between ? and ? and not exists " +
                "(select 1 from daily_closings c where c.store_no = s.store_no and c.closing_date = s.sale_date)", Long.class, storeNo, f, t);
        Long stocktakes = days == 0 ? 0L : jdbc.queryForObject(
                "select count(*) from stocktakes where store_no = ? and count_date between ? and ?", Long.class, storeNo, f, t);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("storeNo", storeNo);
        m.put("storeName", store.getStoreName());
        m.put("storeType", store.getStoreType());
        m.put("storeStatus", store.getStatus());
        m.put("franchise", franchise);
        m.put("month", ym.toString());
        m.putAll(b);
        m.put("salesAmount", sales);
        m.put("receipts", receipts);
        m.put("vat", vat);
        m.put("netSales", net);
        m.put("theoreticalCost", theoretical);
        m.put("consumeCost", consume);
        m.put("wasteAmount", waste);
        m.put("lossAmount", loss);
        m.put("foodCost", food);
        m.put("purchaseAmount", purchases);
        m.put("grossProfit", gross);
        m.put("laborCost", laborCost);
        m.put("labor", labor);
        m.put("rent", rent);
        m.put("fixedCost", fixed);
        m.put("cardFee", cardFee);
        m.put("deliveryFee", deliveryFee);
        m.put("feeAmount", cardFee + deliveryFee);
        m.put("operatingProfit", op);
        m.put("avgTicket", receipts == 0 ? 0 : Math.round((double) sales / receipts));
        m.put("dailyAvgSales", days == 0 ? 0 : Math.round((double) sales / days));
        // 진행 중인 달: 지금 추세대로면 월말 매출
        m.put("forecastSales", days == 0 ? 0 : Math.round((double) sales / days * dim));
        m.put("unclosedDays", unclosed == null ? 0 : unclosed);
        m.put("stocktakeCount", stocktakes == null ? 0 : stocktakes);
        m.put("closing", monthlyRepository.findByStore_StoreNoAndYearMonth(storeNo, ym.toString()).map(this::closingMap).orElse(null));
        addRatios(m);
        return m;
    }

    // 인건비: 정규직·계약직은 월급 × 지난 날 비율, 파트타임은 근무표 시간 × 시급 + 주휴수당(그 주 일요일이 기간 안에 있으면)
    private Map<String, Object> labor(Store store, LocalDate from, LocalDate to, double ratio) {
        List<User> staff = userRepository.findAll().stream()
                .filter(u -> u.getStore() != null && u.getStore().getStoreNo().equals(store.getStoreNo())).toList();
        long fullTime = 0;
        int fullCount = 0;
        Map<Long, Long> hourly = new HashMap<>();
        for (User u : staff) {
            if (ScheduleService.PART_TIME.equals(u.getEmploymentType())) {
                hourly.put(u.getUserNo(), u.getUserSalary() == null ? 0 : u.getUserSalary());
            } else {
                fullTime += Math.round((u.getUserSalary() == null ? 0 : u.getUserSalary()) * ratio);
                fullCount++;
            }
        }
        long partPay = 0, holiday = 0;
        double partHours = 0;
        if (!hourly.isEmpty()) {
            LocalDate weekFrom = ScheduleService.weekStart(from);
            Map<Long, Map<LocalDate, Double>> weekHours = new HashMap<>();
            for (Map<String, Object> r : jdbc.queryForList(
                    "select user_no, work_date, (time_to_sec(timediff(end_time, start_time)) / 60 - break_minutes) / 60 as hours " +
                    "from shifts where store_no = ? and work_date between ? and ?",
                    store.getStoreNo(), Date.valueOf(weekFrom), Date.valueOf(to))) {
                long userNo = ((Number) r.get("user_no")).longValue();
                if (!hourly.containsKey(userNo)) continue;
                LocalDate d = ((Date) r.get("work_date")).toLocalDate();
                double h = ((Number) r.get("hours")).doubleValue();
                weekHours.computeIfAbsent(userNo, k -> new HashMap<>()).merge(ScheduleService.weekStart(d), h, Double::sum);
                if (!d.isBefore(from)) {
                    partHours += h;
                    partPay += Math.round(h * hourly.get(userNo));
                }
            }
            for (var e : weekHours.entrySet()) {
                for (var w : e.getValue().entrySet()) {
                    LocalDate sunday = w.getKey().plusDays(6);
                    if (sunday.isBefore(from) || sunday.isAfter(to)) continue;
                    holiday += ScheduleService.holidayPay(w.getValue(), hourly.get(e.getKey()));
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fullTime", fullTime);
        m.put("fullTimeCount", fullCount);
        m.put("partTime", partPay);
        m.put("partTimeHours", round1(partHours));
        m.put("holidayPay", holiday);
        return m;
    }

    // 순매출 대비 비율 (%)
    private static void addRatios(Map<String, Object> m) {
        // 매장이 하나도 없으면 합계 맵이 비어 있으므로 없는 값은 0으로 본다
        java.util.function.Function<String, Double> v = k -> ((Number) m.getOrDefault(k, 0)).doubleValue();
        double net = v.apply("netSales");
        java.util.function.Function<String, Double> rate = k -> net == 0 ? 0 : round1(v.apply(k) / net * 100);
        m.put("foodCostRate", rate.apply("foodCost"));
        m.put("theoreticalRate", rate.apply("theoreticalCost"));
        m.put("laborRate", rate.apply("laborCost"));
        m.put("lossRate", net == 0 ? 0 : round1((((Number) m.getOrDefault("wasteAmount", 0)).doubleValue() + ((Number) m.getOrDefault("lossAmount", 0)).doubleValue()) / net * 100));
        m.put("flRate", net == 0 ? 0 : round1((v.apply("foodCost") + v.apply("laborCost")) / net * 100));
        m.put("operatingMargin", rate.apply("operatingProfit"));
    }

    private Map<String, Object> closingMap(MonthlyClosing mc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("monthlyNo", mc.getMonthlyNo());
        m.put("yearMonth", mc.getYearMonth());
        m.put("operatingProfit", mc.getOperatingProfit());
        m.put("salesAmount", mc.getSalesAmount());
        m.put("note", mc.getNote());
        m.put("closedBy", mc.getClosedBy());
        m.put("closedAt", mc.getClosedAt());
        return m;
    }

    private static long num(Map<String, Object> m, String k) {
        return ((Number) m.get(k)).longValue();
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
