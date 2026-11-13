package com.example.docksystem_erp.service.Simulation;

import com.example.docksystem_erp.service.Automation.WebhookPublisher;
import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.entity.Inventory.InventoryTxn;
import com.example.docksystem_erp.entity.Material.Material;
import com.example.docksystem_erp.entity.Menu.Menu;
import com.example.docksystem_erp.entity.Menu.RecipeItem;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Material.MaterialRepository;
import com.example.docksystem_erp.repository.Store.StoreRepository;
import com.example.docksystem_erp.service.Integration.IntegrationService;
import com.example.docksystem_erp.service.Inventory.InventoryService;
import com.example.docksystem_erp.service.Message.StoreMessageService;
import com.example.docksystem_erp.service.Monthly.MonthlyService;
import com.example.docksystem_erp.service.OrderRequest.OrderRequestService;
import com.example.docksystem_erp.service.PurchaseOrder.PurchaseOrderService;
import com.example.docksystem_erp.service.Sales.OpsGuard;
import com.example.docksystem_erp.util.BizClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * 매장 영업 시뮬레이터: 매장 직원처럼 POS API를 불러 영업을 돌린다. 데이터는 실제 경로(POS → 전송 대기열 → 본사 수집 API)로 쌓인다.
 *  - 날짜별 영업: 달력에서 고른 날(또는 기간) · 매장 · 영업 시간 · 시나리오(날씨 · 손님 수 · 배달 · 프로모션) · 매장 업무 · 이상 상황으로
 *    하루를 돌린다. 속도는 즉시 · 60배속(1시간 = 1분) · 실시간(오늘만). 본사 업무(요청 승인 · 보충 발주 · 입고)는 고를 때만 대신 한다
 *  - 빠른 재생: 기존 운영 기록을 지우고 오늘 전까지 N일 치를 본사 업무까지 대신하며 돌린다 (처음 이력 만들기)
 * 하루 일과: 08시 입고 확인(본사: 입고 처리 · 요청 승인) → (월 · 목 10시) 물류센터 보충 발주 → 07~22시 판매
 *          → 21시 30분 폐기 · 재고 실사 · 발주 요청 → 22시 일 마감
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosRunService {
    private static final String ACTOR = "본사 시뮬레이터";
    private static final String BAKERY = "베이커리·디저트";
    private static final int CYCLE_DAYS = 4;   // 정기 발주 간격 (월 · 목)
    private static final int SAFETY_DAYS = 2;
    private static final int OPENING_DAYS = 9;  // 매장 기초 재고: 첫날 사용량의 9일 치
    private static final int CENTER_OPENING_DAYS = 14;
    private static final int MAX_ERRORS = 100;
    private static final int FIRST_HOUR = 7, LAST_HOUR = 22;
    private static final LocalTime MORNING = LocalTime.of(8, 0), RESTOCK = LocalTime.of(10, 0),
            EVENING = LocalTime.of(21, 30), CLOSING = LocalTime.of(22, 0);

    private final PosClient pos;
    private final SimulationService sim;
    private final StoreRepository storeRepository;
    private final MaterialRepository materialRepository;
    private final IntegrationService integrationService;
    private final InventoryService inventoryService;
    private final OrderRequestService orderRequestService;
    private final PurchaseOrderService poService;
    private final MonthlyService monthlyService;
    private final StoreMessageService messageService;
    private final JdbcTemplate jdbc;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pos-simulator");
        t.setDaemon(true);
        return t;
    });
    // 매장별 판매를 동시에 보내는 일꾼
    private final ExecutorService sellers = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "pos-simulator-sale");
        t.setDaemon(true);
        return t;
    });

    // 시뮬레이터에서 다루는 매장 하나: POS 토큰 · 하루 사용량
    private static final class Shop {
        final Store store;
        final String token;
        final Map<Long, Double> avgUse = new HashMap<>();
        Shop(Store store, String token) {
            this.store = store;
            this.token = token;
        }
    }

    // 날짜별 영업 설정 (화면의 설정 그대로)
    public record DayRun(LocalDate from, LocalDate to, List<Long> storeNos, LocalTime openAt, LocalTime closeAt, boolean close,
                         String speed, String weather, Double customerRate, Double deliveryBoost, Map<String, Double> promos,
                         boolean deliveries, Double shortRate, boolean evening, boolean stocktake, Double cashDiffRate,
                         LocalTime offlineFrom, LocalTime offlineTo, List<String> lowStock, boolean hq, boolean rewind) {
        SimulationService.Scenario scenario() {
            return new SimulationService.Scenario(weather == null ? "평소" : weather, customerRate == null ? 1.0 : customerRate,
                    deliveryBoost == null ? 0 : deliveryBoost, promos == null ? Map.of() : promos);
        }

        LocalTime open() {
            return openAt == null ? LocalTime.of(FIRST_HOUR, 0) : openAt;
        }

        LocalTime end() {
            return closeAt == null ? CLOSING : closeAt;
        }

        double factor() {
            return "x60".equals(speed) ? 60 : "realtime".equals(speed) ? 1 : 0;
        }
    }

    // ----- 진행 상태 -----
    private volatile boolean running, stopRequested;
    private volatile String mode = "", phase = "대기";
    private volatile int day, totalDays;
    private volatile LocalDate currentDate;
    private volatile LocalTime simTime;
    private final AtomicInteger receipts = new AtomicInteger();
    private volatile int requests, closings, wastes, stocktakes, errors;
    private volatile String lastError;
    private volatile LocalDateTime startedAt, finishedAt;
    private volatile int[] hourPlanned = new int[LAST_HOUR - FIRST_HOUR];
    private final AtomicIntegerArray hourPosted = new AtomicIntegerArray(LAST_HOUR - FIRST_HOUR);
    private final Deque<Map<String, Object>> history = new ArrayDeque<>();
    private Map<String, Object> current;

    public synchronized Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", running);
        m.put("mode", mode);
        m.put("phase", phase);
        m.put("day", day);
        m.put("totalDays", totalDays);
        m.put("currentDate", currentDate);
        m.put("simTime", simTime);
        m.put("receipts", receipts.get());
        m.put("requests", requests);
        m.put("closings", closings);
        m.put("wastes", wastes);
        m.put("stocktakes", stocktakes);
        m.put("errors", errors);
        m.put("lastError", lastError);
        m.put("startedAt", startedAt);
        m.put("finishedAt", finishedAt);
        List<Map<String, Object>> hours = new ArrayList<>();
        for (int i = 0; i < hourPlanned.length; i++) hours.add(Map.of("hour", FIRST_HOUR + i, "planned", hourPlanned[i], "posted", hourPosted.get(i)));
        m.put("hours", hours);
        m.put("history", new ArrayList<>(history));
        return m;
    }

    private void resetCounts() {
        day = totalDays = requests = closings = wastes = stocktakes = errors = 0;
        receipts.set(0);
        lastError = null;
        currentDate = null;
        simTime = null;
        stopRequested = false;
        startedAt = LocalDateTime.now();
        finishedAt = null;
        hourPlanned = new int[LAST_HOUR - FIRST_HOUR];
        for (int i = 0; i < hourPlanned.length; i++) hourPosted.set(i, 0);
    }

    public synchronized Map<String, Object> stop() {
        if (running) {
            stopRequested = true;
            phase = "멈추는 중";
        }
        return status();
    }

    // ===================== 날짜별 영업 =====================

    // 달력: 매장별 마지막 마감일과 이 달에 마감한 날
    public Map<String, Object> calendar(YearMonth month) {
        List<Map<String, Object>> stores = new ArrayList<>();
        for (Store s : shopStores()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("storeNo", s.getStoreNo());
            m.put("storeName", s.getStoreName());
            m.put("storeType", s.getStoreType());
            SimulationService.StoreTrait t = sim.trait(s);
            m.put("area", t.area());
            m.put("baseCustomers", t.baseCustomers());
            m.put("lastClosing", lastClosing(s.getStoreNo()));
            m.put("closedDates", jdbc.queryForList("select closing_date from daily_closings where store_no = ? and closing_date between ? and ? order by closing_date",
                    LocalDate.class, s.getStoreNo(), Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth())));
            stores.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("today", LocalDate.now());
        r.put("now", LocalTime.now().withNano(0));
        r.put("stores", stores);
        return r;
    }

    // 미리보기: 첫날의 매장별 손님 수 · 매출 · 시간대별 손님 (실행하면 같은 손님이 온다)
    public Map<String, Object> preview(DayRun req) {
        LocalDate d = req.from() == null ? LocalDate.now() : req.from();
        List<Menu> menus = sellable(sim.loadMenus());
        int[] hours = new int[LAST_HOUR - FIRST_HOUR];
        List<Map<String, Object>> stores = new ArrayList<>();
        long total = 0, amount = 0, delivery = 0;
        LocalDate needsRewindFrom = null;
        for (Store s : selected(req.storeNos())) {
            List<SimulationService.Receipt> list = window(sim.scenarioReceipts(s, d, menus, req.scenario()), req.open(), effectiveEnd(req, d));
            long amt = 0, del = 0;
            for (SimulationService.Receipt r : list) {
                hours[r.time().getHour() - FIRST_HOUR]++;
                for (SimulationService.Item it : r.items()) amt += it.menu().getPrice() * it.qty();
                if ("배달".equals(r.channel())) del++;
            }
            total += list.size();
            amount += amt;
            delivery += del;
            LocalDate last = lastClosing(s.getStoreNo());
            if (last != null && !d.isAfter(last)) needsRewindFrom = d;
            stores.add(Map.of("storeNo", s.getStoreNo(), "storeName", s.getStoreName(), "customers", list.size(), "amount", amt));
        }
        List<Map<String, Object>> h = new ArrayList<>();
        for (int i = 0; i < hours.length; i++) h.add(Map.of("hour", FIRST_HOUR + i, "count", hours[i]));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("date", d);
        r.put("customers", total);
        r.put("amount", amount);
        r.put("deliveryShare", total == 0 ? 0 : Math.round(delivery * 1000.0 / total) / 10.0);
        r.put("stores", stores);
        r.put("hours", h);
        r.put("needsRewind", needsRewindFrom != null);
        return r;
    }

    public synchronized Map<String, Object> startDays(DayRun req) {
        if (running) throw new IllegalStateException("시뮬레이션이 이미 돌고 있습니다.");
        LocalDate today = LocalDate.now();
        LocalDate from = req.from(), to = req.to() == null ? req.from() : req.to();
        if (from == null || to.isBefore(from)) throw new IllegalArgumentException("날짜를 골라 주세요.");
        if (to.isAfter(today)) throw new IllegalArgumentException("오늘 이후 날짜는 영업할 수 없습니다.");
        if (from.plusDays(60).isBefore(to)) throw new IllegalArgumentException("한 번에 60일까지 돌릴 수 있습니다.");
        if (req.storeNos() == null || req.storeNos().isEmpty()) throw new IllegalArgumentException("매장을 하나 이상 골라 주세요.");
        if ("realtime".equals(req.speed()) && !(from.equals(today) && to.equals(today))) throw new IllegalArgumentException("실시간은 오늘만 돌릴 수 있습니다.");
        if (!req.open().isBefore(req.end())) throw new IllegalArgumentException("영업 시작 시각이 끝 시각보다 빨라야 합니다.");
        for (Store s : selected(req.storeNos())) {
            LocalDate last = lastClosing(s.getStoreNo());
            if (last != null && !from.isAfter(last) && !req.rewind()) {
                throw new IllegalStateException(s.getStoreName() + "은(는) " + last + "까지 마감했습니다. 이 날부터 이후 기록을 지우고 다시 쌓을지 확인해 주세요.");
            }
        }
        running = true;
        mode = "days";
        resetCounts();
        totalDays = (int) (to.toEpochDay() - from.toEpochDay()) + 1;
        phase = "준비 중";
        current = new LinkedHashMap<>();
        current.put("at", LocalDateTime.now().withNano(0));
        current.put("dates", from.equals(to) ? from.toString() : from + " ~ " + to);
        current.put("stores", selected(req.storeNos()).stream().map(Store::getStoreName).toList());
        current.put("weather", req.scenario().weather());
        current.put("speed", req.speed());
        LocalDate end = to;
        worker.submit(() -> asActor(() -> runDays(req, from, end)));
        return status();
    }

    private void runDays(DayRun req, LocalDate from, LocalDate to) {
        try {
            if (req.rewind()) {
                phase = from + "부터 기록 지우는 중";
                rewindFrom(from);
            }
            integrationService.ensureKeys();
            phase = "매장 POS 등록";
            Set<Long> picked = new HashSet<>(req.storeNos());
            List<Shop> shops = registerShops().stream().filter(s -> picked.contains(s.store.getStoreNo())).toList();
            Store center = center();
            List<Menu> menus = sellable(sim.loadMenus());
            Map<Long, Material> materials = materials();

            // 처음 영업하는 매장 · 물류센터는 기초 재고부터
            List<Shop> fresh = shops.stream().filter(s -> !hasStock(s.store)).toList();
            if (!fresh.isEmpty() || (center != null && !hasStock(center))) {
                openingStock(fresh, center != null && !hasStock(center) ? center : null, from, menus, materials);
            }

            for (LocalDate d = from; !d.isAfter(to) && !stopRequested; d = d.plusDays(1)) {
                day++;
                currentDate = d;
                runScenarioDay(shops, center, d, req, menus, materials);
            }
            phase = stopRequested ? "멈춤" : "완료";
        } catch (RuntimeException e) {
            log.error("날짜별 영업 시뮬레이션 중단", e);
            lastError = e.getMessage();
            phase = "중단됨";
        } finally {
            cleanup();
            current.put("result", phase);
            current.put("receipts", receipts.get());
            current.put("closings", closings);
            current.put("errors", errors);
            synchronized (this) {
                history.addFirst(current);
                while (history.size() > 10) history.removeLast();
            }
        }
    }

    // 하루: 손님 계획을 세우고, 일과(입고 · 보충 · 이상 상황 · 폐기 · 마감)를 시각 순서대로 끼워 넣으며 판매를 보낸다
    private void runScenarioDay(List<Shop> shops, Store center, LocalDate d, DayRun req, List<Menu> menus, Map<Long, Material> materials) {
        LocalDate today = LocalDate.now();
        boolean realtimeToday = "realtime".equals(req.speed());
        LocalTime open = req.open(), end = effectiveEnd(req, d);
        phase = "영업 중 " + d;
        Random rnd = new Random(d.toEpochDay() * 31 + req.scenario().hashCode());
        for (Shop s : shops) seedAvgUse(s);

        // 손님 계획
        Map<Shop, List<SimulationService.Receipt>> plans = new LinkedHashMap<>();
        int[] planned = new int[LAST_HOUR - FIRST_HOUR];
        for (Shop s : shops) {
            List<SimulationService.Receipt> list = window(sim.scenarioReceipts(s.store, d, menus, req.scenario()), open, end);
            if (realtimeToday) {
                LocalTime now = LocalTime.now();
                list = list.stream().filter(r -> r.time().isAfter(now)).toList(); // 켠 시각 이후 손님부터
            }
            plans.put(s, list);
            list.forEach(r -> planned[r.time().getHour() - FIRST_HOUR]++);
        }
        hourPlanned = planned;
        for (int i = 0; i < planned.length; i++) hourPosted.set(i, 0);

        // 일과 (시각 순서)
        TreeMap<LocalTime, List<Runnable>> events = new TreeMap<>();
        double shortRate = req.shortRate() == null ? 0.03 : req.shortRate();
        if (req.lowStock() != null && !req.lowStock().isEmpty()) on(events, open, () -> lowStock(shops, req.lowStock()));
        LocalTime morning = MORNING.isBefore(open) ? open : MORNING;
        if (!morning.isAfter(end)) {
            if (req.hq()) on(events, morning, () -> hqMorning(center, d));
            if (req.deliveries()) on(events, morning, () -> storeDeliveries(shops, d, rnd, shortRate));
        }
        if (req.hq() && isOrderDay(d) && !RESTOCK.isBefore(open) && !RESTOCK.isAfter(end)) on(events, RESTOCK, () -> restockCenter(center));
        if (req.offlineFrom() != null && req.offlineTo() != null && req.offlineFrom().isBefore(req.offlineTo())) {
            on(events, req.offlineFrom(), () -> pos.offline(true));
            on(events, req.offlineTo(), () -> pos.offline(false));
        }
        boolean fullDay = !end.isBefore(CLOSING);
        if (fullDay && req.evening()) on(events, EVENING, () -> evening(shops, d, materials, rnd, true, req.stocktake() || d.getDayOfWeek() == DayOfWeek.SUNDAY, true));
        else if (fullDay && req.stocktake()) on(events, EVENING, () -> evening(shops, d, materials, rnd, false, true, false));
        double diffRate = req.cashDiffRate() == null ? 0.04 : req.cashDiffRate();
        if (fullDay && req.close()) on(events, CLOSING, () -> shops.forEach(s -> closeDay(s, rnd, diffRate)));

        // 진행: 일과 사이사이 그 시각까지의 손님을 매장별로 동시에 보낸다
        Pace pace = new Pace(d, open, req.factor(), realtimeToday);
        LocalTime prev = open;
        for (var e : events.entrySet()) {
            if (stopRequested) return;
            sellUntil(plans, d, prev, e.getKey(), pace);
            pace.waitUntil(e.getKey());
            simTime = e.getKey();
            phase = "영업 중 " + d + " " + e.getKey();
            // 일과는 그 날 그 시각으로 업무 시계를 맞추고 하고, 쌓인 전송을 다 보낸 뒤 실제 시계로 돌린다
            setClock(d.atTime(e.getKey()));
            try {
                for (Runnable r : e.getValue()) {
                    try {
                        r.run();
                    } catch (RuntimeException ex) {
                        error(ex);
                    }
                }
                pos.syncAll();
            } finally {
                setClock(null);
            }
            prev = e.getKey();
        }
        sellUntil(plans, d, prev, end.plusSeconds(1), pace);
        pos.offline(false);
        pos.syncAll();
    }

    // 실행 속도: 즉시(기다리지 않음) · 60배속 · 실시간
    private final class Pace {
        final LocalDate d;
        final LocalTime simStart;
        final double factor;
        final long realStart = System.nanoTime();

        Pace(LocalDate d, LocalTime open, double factor, boolean realtimeToday) {
            this.d = d;
            this.factor = factor;
            this.simStart = realtimeToday ? LocalTime.now() : open;
        }

        void waitUntil(LocalTime t) {
            if (factor <= 0 || stopRequested) return;
            long simNanos = Duration.between(simStart, t).toNanos();
            if (simNanos <= 0) return;
            long target = realStart + (long) (simNanos / factor);
            long wait = target - System.nanoTime();
            if (wait <= 0) return;
            try {
                Thread.sleep(Math.min(wait / 1_000_000, 1000));
                if (System.nanoTime() < target) waitUntil(t);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void sellUntil(Map<Shop, List<SimulationService.Receipt>> plans, LocalDate d, LocalTime from, LocalTime until, Pace pace) {
        List<Future<?>> jobs = new ArrayList<>();
        for (var e : plans.entrySet()) {
            jobs.add(sellers.submit(() -> {
                Map<Long, Double> used = new HashMap<>();
                for (SimulationService.Receipt r : e.getValue()) {
                    if (r.time().isBefore(from) || !r.time().isBefore(until)) continue;
                    if (stopRequested) return;
                    pace.waitUntil(r.time());
                    sell(e.getKey(), d.atTime(r.time()), r, used);
                    hourPosted.incrementAndGet(r.time().getHour() - FIRST_HOUR);
                    if (simTime == null || r.time().isAfter(simTime)) simTime = r.time();
                }
            }));
        }
        for (Future<?> f : jobs) {
            try {
                f.get();
            } catch (Exception ex) {
                throw new IllegalStateException("판매 중 오류: " + ex.getMessage(), ex);
            }
        }
    }

    private static void on(TreeMap<LocalTime, List<Runnable>> events, LocalTime t, Runnable r) {
        events.computeIfAbsent(t, k -> new ArrayList<>()).add(r);
    }

    // 오늘은 지금 시각까지만 (실시간이 아니면 앞으로 올 손님을 미리 받지 않는다)
    private static LocalTime effectiveEnd(DayRun req, LocalDate d) {
        LocalTime end = req.end();
        if (d.equals(LocalDate.now()) && !"realtime".equals(req.speed())) {
            LocalTime now = LocalTime.now().withNano(0);
            if (now.isBefore(end)) end = now;
        }
        return end;
    }

    private static List<SimulationService.Receipt> window(List<SimulationService.Receipt> list, LocalTime open, LocalTime end) {
        return list.stream().filter(r -> !r.time().isBefore(open) && r.time().isBefore(end)).toList();
    }

    // 이상 상황: 직원이 세어 보니 재고가 하루 치밖에 없다 (실사로 기록 → 저녁에 긴급 발주가 나가는지 확인)
    private void lowStock(List<Shop> shops, List<String> codes) {
        Set<String> want = new HashSet<>(codes);
        for (Shop s : shops) {
            List<Map<String, Object>> lines = new ArrayList<>();
            for (Map<String, Object> r : inventoryService.stock(s.store.getStoreNo())) {
                if (!want.contains(String.valueOf(r.get("materialCode")))) continue;
                double avg = ((Number) r.get("avgDaily")).doubleValue();
                double qty = ((Number) r.get("quantity")).doubleValue();
                lines.add(Map.of("materialCode", r.get("materialCode"), "countedQty", Math.max(0, Math.round(Math.min(qty, avg)))));
            }
            if (lines.isEmpty()) continue;
            pos.stocktake(s.token, "개점 점검: 재고가 예상보다 적음", lines);
            stocktakes++;
        }
    }

    // 이 날부터 이후 기록을 지운다 (모든 매장 · 물류센터). 그 전에 들어온 발주의 입고 · 승인은 '대기'로 되돌린다
    private void rewindFrom(LocalDate from) {
        Date d = Date.valueOf(from);
        jdbc.update("delete t from store_message_targets t join store_messages m on m.message_no = t.message_no where m.sent_at >= ?", d);
        jdbc.update("delete from store_messages where sent_at >= ?", d);
        jdbc.update("delete from ingest_logs where business_date >= ?", d);
        jdbc.update("delete l from order_request_lines l join order_requests r on r.request_no = l.request_no where r.request_date >= ?", d);
        jdbc.update("delete from order_requests where request_date >= ?", d);
        jdbc.update("update order_request_lines l join order_requests r on r.request_no = l.request_no set l.approved_packs = null where r.decided_at >= ?", d);
        jdbc.update("update order_requests set status = '요청', decided_at = null, decided_by = null, po_numbers = null, reject_reason = null where decided_at >= ?", d);
        jdbc.update("delete l from purchase_order_lines l join purchase_orders p on p.po_no = l.po_no where p.order_date >= ?", d);
        jdbc.update("delete from purchase_orders where order_date >= ?", d);
        jdbc.update("update purchase_order_lines l join purchase_orders p on p.po_no = l.po_no set l.received_packs = null where p.received_date >= ?", d);
        jdbc.update("update purchase_orders set status = '발주완료', received_date = null, received_by = null, receive_note = null where received_date >= ?", d);
        jdbc.update("delete l from stocktake_lines l join stocktakes s on s.stocktake_no = l.stocktake_no where s.count_date >= ?", d);
        jdbc.update("delete from stocktakes where count_date >= ?", d);
        jdbc.update("delete from inventory_txns where txn_date >= ?", d);
        jdbc.update("delete from daily_closings where closing_date >= ?", d);
        jdbc.update("delete from sales where sale_date >= ?", d);
        jdbc.update("delete from monthly_closings where year_month_key >= ?", YearMonth.from(from).toString());
        jdbc.update("delete from doc_sequences");
        pos.rewind(from);
    }

    private LocalDate lastClosing(Long storeNo) {
        return jdbc.queryForObject("select max(closing_date) from daily_closings where store_no = ?", LocalDate.class, storeNo);
    }

    private boolean hasStock(Store s) {
        Long n = jdbc.queryForObject("select count(*) from inventory_txns where store_no = ?", Long.class, s.getStoreNo());
        return n != null && n > 0;
    }

    private List<Store> shopStores() {
        return storeRepository.findAll().stream()
                .filter(s -> OpsGuard.SHOP_TYPES.contains(s.getStoreType()) && "영업중".equals(s.getStatus()))
                .sorted(Comparator.comparing(Store::getStoreNo)).toList();
    }

    private List<Store> selected(List<Long> storeNos) {
        Set<Long> want = storeNos == null ? Set.of() : new HashSet<>(storeNos);
        return shopStores().stream().filter(s -> want.contains(s.getStoreNo())).toList();
    }

    // ===================== 빠른 재생 =====================

    public synchronized Map<String, Object> startReplay(int days) {
        if (running) throw new IllegalStateException("시뮬레이션이 이미 돌고 있습니다.");
        int n = Math.max(7, Math.min(days, 120));
        running = true;
        mode = "replay";
        resetCounts();
        totalDays = n;
        phase = "준비 중";
        current = new LinkedHashMap<>();
        current.put("at", LocalDateTime.now().withNano(0));
        current.put("dates", "빠른 재생 " + n + "일");
        current.put("stores", List.of("전체"));
        current.put("weather", "평소");
        current.put("speed", "instant");
        worker.submit(() -> asActor(() -> replay(n)));
        return status();
    }

    private void replay(int days) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days);
        // 수십 일치 기록을 다시 만드는 동안에는 자동화 이벤트를 내지 않는다 (지난 일에 자동화가 반응하지 않게)
        WebhookPublisher.mute(true);
        try {
            phase = "기존 운영 기록 지우는 중";
            sim.wipeOperations();
            pos.reset();
            integrationService.ensureKeys();
            setClock(start.atTime(6, 0));

            phase = "매장 POS 등록";
            List<Shop> shops = registerShops();
            Store center = center();
            List<Menu> menus = sellable(sim.loadMenus());
            Map<Long, Material> materials = materials();

            phase = "근무표 · 기초 재고";
            sim.shiftsFor(start, today, days);
            Random rnd = new Random(days * 7919L);
            openingStock(shops, center, start, menus, materials);

            for (LocalDate d = start; d.isBefore(today) && !stopRequested; d = d.plusDays(1)) {
                day++;
                currentDate = d;
                phase = "영업 중 " + d;
                replayDay(shops, center, d, start, days, menus, materials, rnd);
            }

            phase = "월 마감 · 공지";
            for (Shop s : shops) {
                for (YearMonth ym = YearMonth.from(start); ym.isBefore(YearMonth.from(today).minusMonths(1)); ym = ym.plusMonths(1)) {
                    try {
                        monthlyService.close(s.store.getStoreNo(), ym.toString(), "정기 월 마감");
                    } catch (RuntimeException e) {
                        error(e);
                    }
                }
            }
            setClock(null);
            messageService.seedSamples(today);
            pos.syncAll();
            phase = stopRequested ? "멈춤" : "완료";
        } catch (RuntimeException e) {
            log.error("매장 영업 시뮬레이션 중단", e);
            lastError = e.getMessage();
            phase = "중단됨";
        } finally {
            cleanup();
            current.put("result", phase);
            current.put("receipts", receipts.get());
            current.put("closings", closings);
            current.put("errors", errors);
            synchronized (this) {
                history.addFirst(current);
                while (history.size() > 10) history.removeLast();
            }
        }
    }

    // 하루 영업 (빠른 재생: 본사 업무까지)
    private void replayDay(List<Shop> shops, Store center, LocalDate d, LocalDate start, int days, List<Menu> menus,
                           Map<Long, Material> materials, Random rnd) {
        setClock(d.atTime(MORNING));
        hqMorning(center, d);
        storeDeliveries(shops, d, rnd, 0.03);
        if (isOrderDay(d)) {
            setClock(d.atTime(RESTOCK));
            restockCenter(center);
        }
        Map<Shop, List<SimulationService.Receipt>> plans = new LinkedHashMap<>();
        for (Shop s : shops) plans.put(s, sim.receiptsFor(s.store, d, start, days, menus, rnd));
        List<Future<?>> jobs = new ArrayList<>();
        for (var e : plans.entrySet()) {
            jobs.add(sellers.submit(() -> {
                Map<Long, Double> used = new HashMap<>();
                for (SimulationService.Receipt r : e.getValue()) sell(e.getKey(), d.atTime(r.time()), r, used);
                used.forEach((no, q) -> e.getKey().avgUse.merge(no, q, (old, v) -> old * 0.7 + v * 0.3));
            }));
        }
        for (Future<?> f : jobs) {
            try {
                f.get();
            } catch (Exception ex) {
                throw new IllegalStateException("판매 중 오류: " + ex.getMessage(), ex);
            }
        }
        setClock(d.atTime(EVENING));
        pos.syncAll();
        evening(shops, d, materials, rnd, true, d.getDayOfWeek() == DayOfWeek.SUNDAY, true);
        setClock(d.atTime(CLOSING));
        pos.syncAll();
        for (Shop s : shops) closeDay(s, rnd, 0.04);
        pos.syncAll();
    }

    // ===================== 하루 일과 =====================

    // 본사 아침: 오늘까지 도착한 물류센터 보충 발주 입고, 어제 올라온 매장 발주 요청 승인 (요청 수량 그대로)
    private void hqMorning(Store center, LocalDate d) {
        if (center != null) {
            for (Map<String, Object> po : poService.open(center.getStoreNo())) {
                if (((LocalDate) po.get("expectedDate")).isAfter(d)) continue;
                try {
                    poService.receive(((Number) po.get("poNo")).longValue(), new OpsRequests.PoReceive(d, null, List.of()));
                } catch (RuntimeException e) {
                    error(e);
                }
            }
        }
        for (Map<String, Object> r : orderRequestService.pending()) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> lines = (List<Map<String, Object>>) r.get("lines");
                orderRequestService.approve(((Number) r.get("requestNo")).longValue(), lines.stream()
                        .map(l -> new OpsRequests.ReceiveLine(((Number) l.get("lineNo")).longValue(), ((Number) l.get("requestedPacks")).intValue()))
                        .toList());
            } catch (RuntimeException e) {
                error(e);
            }
        }
    }

    // 매장 아침: 본사 데이터를 받고, 도착한 물건 입고 확인 (가끔 1포장 덜 옴)
    private void storeDeliveries(List<Shop> shops, LocalDate d, Random rnd, double shortRate) {
        for (Shop s : shops) {
            try {
                pos.sync(s.token);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> open = (List<Map<String, Object>>) pos.today(s.token).getOrDefault("openOrders", List.of());
                for (Map<String, Object> po : open) {
                    if (LocalDate.parse(String.valueOf(po.get("expectedDate"))).isAfter(d)) continue;
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> lines = (List<Map<String, Object>>) po.get("lines");
                    List<Map<String, Object>> got = new ArrayList<>();
                    boolean shortage = false;
                    for (Map<String, Object> l : lines) {
                        int packs = ((Number) l.get("orderPacks")).intValue();
                        if (packs >= 2 && rnd.nextDouble() < shortRate) {
                            packs--;
                            shortage = true;
                        }
                        got.add(Map.of("materialCode", l.get("materialCode"), "packs", packs));
                    }
                    pos.delivery(s.token, String.valueOf(po.get("poNumber")), shortage ? "공급처 재고 부족으로 1포장 덜 옴" : null, got);
                }
            } catch (RuntimeException e) {
                error(e);
            }
        }
    }

    private void restockCenter(Store center) {
        if (center == null) return;
        for (Map<String, Object> g : poService.suggestion(center.getStoreNo(), CYCLE_DAYS)) {
            if (g.get("clientNo") == null) continue;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lines = (List<Map<String, Object>>) g.get("lines");
            List<OpsRequests.PoLine> order = lines.stream()
                    .filter(l -> ((Number) l.get("suggestedPacks")).intValue() > 0)
                    .map(l -> new OpsRequests.PoLine(((Number) l.get("materialNo")).longValue(), ((Number) l.get("suggestedPacks")).intValue()))
                    .toList();
            if (order.isEmpty()) continue;
            try {
                poService.create(new OpsRequests.PoCreate(center.getStoreNo(), ((Number) g.get("clientNo")).longValue(), null, "물류센터 정기 보충", order));
            } catch (RuntimeException e) {
                error(e);
            }
        }
    }

    private void sell(Shop s, LocalDateTime at, SimulationService.Receipt r, Map<Long, Double> used) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (SimulationService.Item it : r.items()) {
            items.add(Map.of("menuCode", it.menu().getMenuCode(), "quantity", it.qty()));
            for (RecipeItem ri : it.menu().getRecipeItems()) {
                used.merge(ri.getMaterial().getMaterialNo(), ri.getQuantity() * it.qty(), Double::sum);
            }
        }
        try {
            pos.sale(s.token, at, r.payment(), r.channel(), items);
            receipts.incrementAndGet();
        } catch (RuntimeException e) {
            error(e);
        }
    }

    // 21:30 폐기 · 재고 실사 · 발주 요청
    private void evening(List<Shop> shops, LocalDate d, Map<Long, Material> materials, Random rnd,
                         boolean doWaste, boolean doStocktake, boolean doRequest) {
        for (Shop s : shops) {
            try {
                List<Map<String, Object>> stock = inventoryService.stock(s.store.getStoreNo());
                if (doWaste) {
                    for (Map<String, Object> row : stock) {
                        Material m = materials.get(((Number) row.get("materialNo")).longValue());
                        double qty = ((Number) row.get("quantity")).doubleValue();
                        if (m == null || qty <= 0) continue;
                        if (BAKERY.equals(m.getMaterialType()) && rnd.nextDouble() < 0.35) {
                            pos.waste(s.token, m.getMaterialCode(), Math.min(qty, 1 + rnd.nextInt(2)), "당일 미판매");
                            wastes++;
                        } else if ("냉장".equals(m.getStorageType()) && m.getShelfLifeDays() != null && m.getShelfLifeDays() <= 30
                                && rnd.nextDouble() < 0.08) {
                            double use = s.avgUse.getOrDefault(m.getMaterialNo(), 0.0);
                            double w = Math.round(Math.min(qty, use * (0.03 + rnd.nextDouble() * 0.05)));
                            if (w > 0) {
                                pos.waste(s.token, m.getMaterialCode(), w, "유통기한 경과");
                                wastes++;
                            }
                        }
                    }
                }
                if (doStocktake) stocktake(s, stock, rnd);
                if (doRequest) orderRequest(s, d, stock, materials);
            } catch (RuntimeException e) {
                error(e);
            }
        }
    }

    // 재고 실사: 식자재 몇 개를 세면 대부분 장부와 같고, 가끔 조금 모자람
    private void stocktake(Shop s, List<Map<String, Object>> stock, Random rnd) {
        List<Map<String, Object>> rows = new ArrayList<>(stock.stream()
                .filter(r -> ((Number) r.get("txnCount")).longValue() > 0 && ((Number) r.get("quantity")).doubleValue() > 0).toList());
        Collections.shuffle(rows, rnd);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map<String, Object> r : rows.subList(0, Math.min(6, rows.size()))) {
            double qty = ((Number) r.get("quantity")).doubleValue();
            double counted = rnd.nextDouble() < 0.6 ? qty : qty * (1 - rnd.nextDouble() * 0.02);
            lines.add(Map.of("materialCode", r.get("materialCode"), "countedQty", Math.round(counted * 10) / 10.0));
        }
        if (lines.isEmpty()) return;
        pos.stocktake(s.token, "주간 재고 실사", lines);
        stocktakes++;
    }

    // 발주 요청: 월 · 목은 다음 발주까지 쓸 만큼, 다른 날은 3일 치도 안 남은 품목만 급하게
    private void orderRequest(Shop s, LocalDate d, List<Map<String, Object>> stock, Map<Long, Material> materials) {
        boolean regular = isOrderDay(d);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map<String, Object> r : stock) {
            Material m = materials.get(((Number) r.get("materialNo")).longValue());
            double avg = s.avgUse.getOrDefault(m == null ? -1L : m.getMaterialNo(), 0.0);
            if (m == null || avg <= 0 || m.getClient() == null) continue;
            double position = ((Number) r.get("quantity")).doubleValue() + ((Number) r.get("onOrder")).doubleValue();
            int lead = m.isDirect() ? PurchaseOrderService.leadDays(m.getClient()) : PurchaseOrderService.SHIP_DAYS;
            double need = regular ? avg * (CYCLE_DAYS + lead + 1 + SAFETY_DAYS) - position
                    : position < avg * 3 ? avg * (lead + 1 + SAFETY_DAYS + 2) - position : 0;
            if (need <= 0) continue;
            lines.add(Map.of("materialCode", m.getMaterialCode(), "packs", (int) Math.ceil(need / m.getPackQty())));
        }
        if (lines.isEmpty()) return;
        pos.orderRequest(s.token, d.plusDays(2), regular ? "정기 발주" : "긴급 발주 (재고 부족)", lines);
        requests++;
    }

    // 22:00 일 마감: 대부분 시재 일치, 가끔 거스름돈 착오. 이미 마감한 매장은 건너뛴다
    @SuppressWarnings("unchecked")
    private void closeDay(Shop s, Random rnd, double diffRate) {
        try {
            Map<String, Object> today = pos.today(s.token);
            if (today.get("closing") != null) return;
            long expected = ((Number) ((Map<String, Object>) today.get("sales")).get("expectedCash")).longValue();
            long diff = rnd.nextDouble() < diffRate ? (rnd.nextBoolean() ? 1 : -1) * 1000L * (1 + rnd.nextInt(10)) : 0;
            pos.close(s.token, expected + diff, diff == 0 ? null : "거스름돈 착오");
            closings++;
        } catch (RuntimeException e) {
            error(e);
        }
    }

    // ===================== 준비 =====================

    private List<Shop> registerShops() {
        List<Shop> list = new ArrayList<>();
        for (Store st : shopStores()) {
            if (st.getIntegrationKey() == null) continue;
            list.add(new Shop(st, pos.register(st.getIntegrationKey())));
        }
        return list;
    }

    private Store center() {
        return storeRepository.findAll().stream()
                .filter(s -> PurchaseOrderService.CENTER_TYPE.equals(s.getStoreType()) && !"폐점".equals(s.getStatus()))
                .min(Comparator.comparing(Store::getStoreNo)).orElse(null);
    }

    private Map<Long, Material> materials() {
        Map<Long, Material> m = new HashMap<>();
        materialRepository.findAll().forEach(x -> m.put(x.getMaterialNo(), x));
        return m;
    }

    // POS에서 팔 수 있는 메뉴만: 판매중이고 레시피(또는 완제품 납품 품목)가 있는 메뉴
    private static List<Menu> sellable(List<Menu> menus) {
        return menus.stream().filter(m -> "판매중".equals(m.getStatus()) && !m.getRecipeItems().isEmpty()).toList();
    }

    // 하루 사용량: 본사 재고 화면의 최근 일평균
    private void seedAvgUse(Shop s) {
        s.avgUse.clear();
        for (Map<String, Object> r : inventoryService.stock(s.store.getStoreNo())) {
            double avg = ((Number) r.get("avgDaily")).doubleValue();
            if (avg > 0) s.avgUse.put(((Number) r.get("materialNo")).longValue(), avg);
        }
    }

    // 기초 재고: 매장은 첫날 예상 사용량의 9일 치, 물류센터는 매장들이 받아 갈 양의 14일 치 (포장 단위로 올림)
    private void openingStock(List<Shop> shops, Store center, LocalDate start, List<Menu> menus, Map<Long, Material> materials) {
        List<Object[]> rows = new ArrayList<>();
        Map<Long, Double> centerUse = new HashMap<>();
        Timestamp at = Timestamp.valueOf(start.atTime(6, 0));
        List<Store> estimateFrom = center != null ? shopStores() : shops.stream().map(s -> s.store).toList();
        Set<Long> openShops = new HashSet<>();
        shops.forEach(s -> openShops.add(s.store.getStoreNo()));
        for (Store st : estimateFrom) {
            Map<Long, Double> used = new HashMap<>();
            for (SimulationService.Receipt r : sim.scenarioReceipts(st, start, menus, SimulationService.Scenario.plain())) {
                for (SimulationService.Item it : r.items()) {
                    for (RecipeItem ri : it.menu().getRecipeItems()) used.merge(ri.getMaterial().getMaterialNo(), ri.getQuantity() * it.qty(), Double::sum);
                }
            }
            for (var e : used.entrySet()) {
                Material m = materials.get(e.getKey());
                if (openShops.contains(st.getStoreNo())) rows.add(openingRow(st, m, start, e.getValue() * OPENING_DAYS, at));
                if (!m.isDirect()) centerUse.merge(e.getKey(), e.getValue(), Double::sum);
            }
            shops.stream().filter(s -> s.store.getStoreNo().equals(st.getStoreNo())).forEach(s -> s.avgUse.putAll(used));
        }
        if (center != null) {
            for (var e : centerUse.entrySet()) rows.add(openingRow(center, materials.get(e.getKey()), start, e.getValue() * CENTER_OPENING_DAYS, at));
        }
        if (rows.isEmpty()) return;
        jdbc.batchUpdate("insert into inventory_txns (store_no, material_no, txn_date, txn_type, quantity, unit_cost, reason, note, ref_type, ref_no, created_at, created_by) " +
                "values (?,?,?,?,?,?,?,?,?,?,?,?)", rows);
    }

    private static Object[] openingRow(Store store, Material m, LocalDate d, double qty, Timestamp at) {
        double packs = Math.ceil(qty / m.getPackQty()) * m.getPackQty();
        return new Object[]{store.getStoreNo(), m.getMaterialNo(), Date.valueOf(d), InventoryTxn.OPENING, packs, m.getUnitCost(),
                null, "시스템 도입 기초 재고", null, null, at, ACTOR};
    }

    // ===================== 공통 =====================

    private static boolean isOrderDay(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.MONDAY || d.getDayOfWeek() == DayOfWeek.THURSDAY;
    }

    // 본사 업무 시계와 POS 업무 시계를 같이 옮긴다 (null = 실제 시계)
    private void setClock(LocalDateTime at) {
        BizClock.set(at);
        pos.clock(at);
    }

    private void cleanup() {
        WebhookPublisher.mute(false);
        try {
            pos.offline(false);
            setClock(null);
        } catch (RuntimeException ignored) {
            BizClock.set(null);
        }
        simTime = null;
        finishedAt = LocalDateTime.now();
        running = false;
    }

    private synchronized void error(RuntimeException e) {
        errors++;
        lastError = e.getMessage();
        if (errors > MAX_ERRORS && running) throw new IllegalStateException("오류가 너무 많아 멈췄습니다. 마지막 오류: " + e.getMessage());
    }

    // 본사 쪽 기록(승인 · 발주 · 입고)의 처리자를 '본사 시뮬레이터'로 남긴다
    private void asActor(Runnable r) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ACTOR, null, List.of()));
        try {
            r.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
