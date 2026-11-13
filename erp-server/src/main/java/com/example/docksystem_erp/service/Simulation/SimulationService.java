package com.example.docksystem_erp.service.Simulation;

import com.example.docksystem_erp.util.BizClock;

import com.example.docksystem_erp.entity.Client.Client;
import com.example.docksystem_erp.entity.Integration.IngestLog;
import com.example.docksystem_erp.entity.OrderRequest.OrderRequest;
import com.example.docksystem_erp.entity.OrderRequest.OrderRequestLine;
import com.example.docksystem_erp.repository.OrderRequest.OrderRequestRepository;
import com.example.docksystem_erp.service.OrderRequest.OrderRequestService;
import com.example.docksystem_erp.entity.PurchaseOrder.PurchaseOrder;
import com.example.docksystem_erp.entity.PurchaseOrder.PurchaseOrderLine;
import com.example.docksystem_erp.entity.User.User;
import com.example.docksystem_erp.repository.PurchaseOrder.PurchaseOrderRepository;
import com.example.docksystem_erp.repository.User.UserRepository;
import com.example.docksystem_erp.service.PurchaseOrder.PurchaseOrderService;
import com.example.docksystem_erp.service.Schedule.ScheduleService;
import com.example.docksystem_erp.entity.Closing.DailyClosing;
import com.example.docksystem_erp.entity.Inventory.InventoryTxn;
import com.example.docksystem_erp.entity.Inventory.Stocktake;
import com.example.docksystem_erp.entity.Inventory.StocktakeLine;
import com.example.docksystem_erp.entity.Material.Material;
import com.example.docksystem_erp.entity.Menu.Menu;
import com.example.docksystem_erp.entity.Menu.RecipeItem;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Closing.DailyClosingRepository;
import com.example.docksystem_erp.repository.Inventory.StocktakeRepository;
import com.example.docksystem_erp.repository.Material.MaterialRepository;
import com.example.docksystem_erp.repository.Menu.MenuRepository;
import com.example.docksystem_erp.repository.Store.StoreRepository;
import com.example.docksystem_erp.service.Sales.OpsGuard;
import com.example.docksystem_erp.service.Sales.SalesService;
import com.example.docksystem_erp.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 분석·보고 기능을 시험할 수 있게 매장 운영 데이터를 만들어 넣는다.
 * 기존 판매 · 마감 · 재고 · 실사 기록을 모두 지우고, 오늘부터 N일 전까지 하루씩 진행하며 만든다.
 *  - 판매: 매장별 손님 수 · 요일 · 시간대 · 계절(아이스 음료 비중) 패턴
 *  - 어제까지는 일 마감(현금 시재 포함) + 레시피 기준 판매소진
 *  - 유통기한 경과 · 당일 미판매 폐기, 일요일 재고 실사(작은 로스)
 *  - 월·목 정기 발주 → 물류센터 공급 품목은 물류센터 출고(다음 날 도착), 직송 품목은 공급업체 발주서
 *    → 도착일에 입고 검수, 재고가 바닥나면 긴급 발주
 *  - 물류센터: 기초 재고 → 매장 출고 → 월·목 공급업체에 정기 보충 → 납품일에 입고
 *  - 직원 근무표 (지난 기간 + 이번 주)
 *  - 오늘은 지금 시각까지만 판매가 있고 마감 전 상태
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {
    private final JdbcTemplate jdbc;
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final MaterialRepository materialRepository;
    private final DailyClosingRepository closingRepository;
    private final StocktakeRepository stocktakeRepository;
    private final PurchaseOrderRepository poRepository;
    private final OrderRequestRepository requestRepository;
    private final com.example.docksystem_erp.service.Integration.IntegrationService integrationService;
    private final com.example.docksystem_erp.service.Message.StoreMessageService messageService;
    private final UserRepository userRepository;
    private final com.example.docksystem_erp.service.Monthly.MonthlyService monthlyService;

    private static final String SALE_SQL = "insert into sales (receipt_no, store_no, sale_date, sale_time, menu_no, menu_name, category, " +
            "quantity, unit_price, unit_cost, amount, payment_method, channel) values (?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String TXN_SQL = "insert into inventory_txns (store_no, material_no, txn_date, txn_type, quantity, unit_cost, " +
            "reason, note, ref_type, ref_no, created_at, created_by) values (?,?,?,?,?,?,?,?,?,?,?,?)";

    // 매장별 성격: 하루 기본 손님 수, 오피스 상권 여부(주말 손님 적음), 주말 배수, 배달 비중, 시간대 패턴
    private record Profile(int base, double weekend, double delivery, double[] hours) {}

    private static final double[] OFFICE = {6, 9, 7, 5, 9, 8, 5, 4, 4, 4, 3, 2, 1, 1, 0.5};
    private static final double[] STREET = {2, 4, 5, 6, 8, 9, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    private static final double[] EVENING = {1, 2, 3, 4, 6, 7, 8, 8, 8, 8, 7, 7, 6, 5, 3};
    private static final int OPEN_HOUR = 7; // 07:00 ~ 22:00

    private static final Map<String, Profile> PROFILES = Map.of(
            "ST-GN01", new Profile(200, 1.0, 0.12, STREET),
            "ST-HD01", new Profile(175, 1.25, 0.08, EVENING),
            "ST-YD01", new Profile(160, 0.45, 0.05, OFFICE));
    private static final String BAKERY = "베이커리·디저트";
    private static final Profile DEFAULT = new Profile(120, 1.0, 0.10, STREET);

    // 메뉴 인기도 (없으면 2)
    private static final Map<String, Double> POPULARITY = Map.ofEntries(
            Map.entry("CF-IAM", 10.0), Map.entry("CF-AME", 7.0), Map.entry("CF-LAT", 6.0), Map.entry("CF-VLT", 4.0),
            Map.entry("CF-CBR", 3.0), Map.entry("CF-CMC", 3.0), Map.entry("CF-OAT", 1.5), Map.entry("CF-MOC", 1.5),
            Map.entry("CF-DCF", 1.5), Map.entry("NC-MAT", 2.0), Map.entry("NC-STR", 2.0), Map.entry("NC-PMK", 2.5),
            Map.entry("TE-EGR", 1.5), Map.entry("AD-LEM", 1.5), Map.entry("BK-CRO", 3.0), Map.entry("BK-SCN", 1.5),
            Map.entry("DS-SCK", 1.5), Map.entry("DS-MAC", 1.0));
    // 날이 추워질수록 덜 팔리는 차가운 메뉴
    private static final Set<String> ICED = Set.of("CF-IAM", "CF-CBR", "AD-LEM", "NC-STR");

    public record Item(Menu menu, int qty) {}

    public record Receipt(LocalTime time, String payment, String channel, List<Item> items) {}

    // 문서 번호 순번: 날짜마다 본사 전체에서 1부터 (예: RQ-261111-003)
    private final Map<String, Integer> docSeq = new HashMap<>();

    private String nextDoc(String prefix) {
        return prefix + String.format("%03d", docSeq.merge(prefix, 1, Integer::sum));
    }

    // 운영 기록(판매 · 마감 · 재고 · 발주 · 근무 · 메시지 · 수집 기록)을 모두 지운다. 기준 정보는 그대로
    @Transactional
    public void wipeOperations() {
        for (String t : List.of("store_message_targets", "store_messages", "ingest_logs", "order_request_lines", "order_requests",
                "monthly_closings", "shifts", "purchase_order_lines", "purchase_orders", "stocktake_lines", "stocktakes",
                "inventory_txns", "daily_closings", "sales", "doc_sequences", "webhook_events", "automation_logs")) {
            jdbc.update("delete from " + t);
        }
    }

    // ----- 매장 영업 시뮬레이터(PosRunService)가 같이 쓰는 부분 -----

    // 판매에 쓸 메뉴: 레시피(또는 완제품 납품 품목)까지 미리 불러 둔다
    @Transactional(readOnly = true)
    public List<Menu> loadMenus() {
        List<Menu> menus = menuRepository.findAll();
        menus.forEach(m -> m.getRecipeItems().forEach(r -> r.getMaterial().getMaterialCode()));
        return menus;
    }

    // 한 매장의 하루 손님 영수증 (매장 성격 · 요일 · 계절 패턴). start ~ start+days 사이의 진행도로 계절과 성장을 정한다
    public List<Receipt> receiptsFor(Store store, LocalDate d, LocalDate start, int days, List<Menu> menus, Random rnd) {
        Profile p = PROFILES.getOrDefault(store.getStoreCode(), DEFAULT);
        List<Receipt> list = dayReceipts(d, start, start.plusDays(days), days, false, LocalTime.MAX, p, menus, rnd);
        list.sort(Comparator.comparing(Receipt::time));
        return list;
    }

    // ----- 날짜별 영업 시뮬레이터: 시나리오 -----
    // weather: 평소 · 비 · 폭염 · 한파 · 행사 · 공휴일 / customerRate: 손님 수 배율 / deliveryBoost: 배달 비중 더하기 / promos: 메뉴 코드 → 판매 배수
    public record Scenario(String weather, double customerRate, double deliveryBoost, Map<String, Double> promos) {
        public static Scenario plain() {
            return new Scenario("평소", 1.0, 0, Map.of());
        }
    }

    public record StoreTrait(int baseCustomers, String area, double weekend, double delivery) {}

    // 화면에 보여 줄 매장 성격
    public StoreTrait trait(Store store) {
        Profile p = PROFILES.getOrDefault(store.getStoreCode(), DEFAULT);
        String area = p.hours() == OFFICE ? "오피스" : p.hours() == EVENING ? "저녁 상권" : "번화가";
        return new StoreTrait(p.base(), area, p.weekend(), p.delivery());
    }

    // 하루 손님 영수증: 매장 성격 · 요일 · 계절(달) + 시나리오. 같은 날 · 매장 · 시나리오면 늘 같은 결과 (미리보기 = 실제 실행)
    public List<Receipt> scenarioReceipts(Store store, LocalDate d, List<Menu> menus, Scenario sc) {
        Profile p = PROFILES.getOrDefault(store.getStoreCode(), DEFAULT);
        Random rnd = new Random(d.toEpochDay() * 7919L + store.getStoreNo() * 104729L + sc.hashCode());
        String w = sc.weather() == null ? "평소" : sc.weather();
        // 계절: 여름일수록 아이스, 겨울일수록 따뜻한 메뉴
        int month = d.getMonthValue();
        double ice = switch (month) { case 6, 7, 8 -> 1.6; case 5, 9 -> 1.3; case 4, 10 -> 1.0; case 3 -> 0.9; default -> 0.8; };
        double hot = switch (month) { case 6, 7, 8 -> 0.75; case 5, 9 -> 0.9; case 4, 10 -> 1.05; default -> 1.2; };
        switch (w) {
            case "비" -> { ice *= 0.8; hot *= 1.1; }
            case "폭염" -> { ice *= 1.6; hot *= 0.6; }
            case "한파" -> { ice *= 0.5; hot *= 1.3; }
            default -> { }
        }
        List<Menu> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (Menu m : menus) {
            if (!"판매중".equals(m.getStatus())) continue;
            double wt = POPULARITY.getOrDefault(m.getMenuCode(), 2.0);
            wt *= ICED.contains(m.getMenuCode()) ? ice : ("커피".equals(m.getCategory()) || "티".equals(m.getCategory()) ? hot : 1);
            wt *= sc.promos() == null ? 1 : sc.promos().getOrDefault(m.getMenuCode(), 1.0);
            pool.add(m);
            weights.add(wt);
        }
        if (pool.isEmpty()) return new ArrayList<>();

        boolean weekend = "공휴일".equals(w) || d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
        double dow = switch (d.getDayOfWeek()) {
            case MONDAY -> 0.94;
            case TUESDAY -> 0.97;
            case THURSDAY -> 1.02;
            case FRIDAY -> 1.1;
            default -> 1.0;
        };
        // 프로모션 메뉴는 그만큼 손님도 조금 늘린다
        double promoLift = sc.promos() == null ? 1 : 1 + sc.promos().values().stream().mapToDouble(v -> Math.max(0, v - 1) * 0.05).sum();
        double factor = (weekend ? p.weekend() : dow) * sc.customerRate() * promoLift * (0.92 + rnd.nextDouble() * 0.16);
        int count = (int) Math.round(p.base() * factor);
        double[] hours = p.hours().clone();
        if ("행사".equals(w)) for (int h = 11; h < hours.length; h++) hours[h] *= 1.8; // 18시 이후 행사 손님
        double delivery = Math.max(0, Math.min(0.6, p.delivery() + sc.deliveryBoost()));
        double takeout = "비".equals(w) || "한파".equals(w) ? 0.30 : 0.36;

        List<Receipt> receipts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int hour = OPEN_HOUR + pick(hours, rnd);
            LocalTime time = LocalTime.of(hour, rnd.nextInt(60), rnd.nextInt(60));
            double pr = rnd.nextDouble();
            String payment = pr < 0.72 ? "카드" : pr < 0.94 ? "간편결제" : "현금";
            double cr = rnd.nextDouble();
            String channel = cr < delivery ? "배달" : cr < delivery + takeout ? "포장" : "매장";
            if ("배달".equals(channel) && "현금".equals(payment)) payment = "간편결제";
            double ir = rnd.nextDouble();
            int itemCount = ir < 0.6 ? 1 : ir < 0.88 ? 2 : ir < 0.97 ? 3 : 4;
            if ("배달".equals(channel)) itemCount = Math.max(itemCount, 2);
            Map<Menu, Integer> basket = new LinkedHashMap<>();
            for (int k = 0; k < itemCount; k++) basket.merge(pool.get(pickW(weights, rnd)), rnd.nextDouble() < 0.9 ? 1 : 2, Integer::sum);
            List<Item> items = new ArrayList<>();
            basket.forEach((m, q) -> items.add(new Item(m, q)));
            receipts.add(new Receipt(time, payment, channel, items));
        }
        receipts.sort(Comparator.comparing(Receipt::time));
        return receipts;
    }

    // 직원이 있는 모든 사업장의 근무표 (start ~ until이 든 주까지)
    @Transactional
    public int shiftsFor(LocalDate start, LocalDate until, long seed) {
        int n = 0;
        for (Store store : storeRepository.findAll()) {
            if ("폐점".equals(store.getStatus())) continue;
            n += generateShifts(store, start, until, new Random(store.getStoreNo() * 31L + seed));
        }
        return n;
    }

    // 과거 기록을 한꺼번에 만드는 동안에는 자동화 이벤트를 내지 않는다
    @Transactional
    public Map<String, Object> regenerate(int days) {
        com.example.docksystem_erp.service.Automation.WebhookPublisher.mute(true);
        try {
            return regenerateAll(days);
        } finally {
            com.example.docksystem_erp.service.Automation.WebhookPublisher.mute(false);
        }
    }

    private Map<String, Object> regenerateAll(int days) {
        days = Math.max(7, Math.min(days, 120));
        docSeq.clear();
        long started = System.currentTimeMillis();
        wipeOperations();

        integrationService.ensureKeys();
        LocalDate today = BizClock.today();
        LocalDate start = today.minusDays(days);
        LocalTime now = BizClock.time();
        String user = CurrentUser.id();

        List<Menu> menus = menuRepository.findAll();
        menus.forEach(m -> m.getRecipeItems().size()); // 레시피 미리 불러오기
        List<Material> materials = materialRepository.findAll();
        Map<Long, Material> materialByNo = new HashMap<>();
        materials.forEach(m -> materialByNo.put(m.getMaterialNo(), m));

        int receiptTotal = 0, lineTotal = 0, closingTotal = 0, stocktakeTotal = 0, txnTotal = 0, poTotal = 0, shiftTotal = 0;
        // 물류센터 (없으면 모든 품목을 직송으로 만든다)
        Store center = storeRepository.findAll().stream()
                .filter(s -> PurchaseOrderService.CENTER_TYPE.equals(s.getStoreType()) && !"폐점".equals(s.getStatus()))
                .min(Comparator.comparing(Store::getStoreNo)).orElse(null);
        List<CenterShip> centerShips = new ArrayList<>(); // 매장으로 내보낸 기록 (물류센터 재고에서 뺄 것)
        // 판매가 없는 센트럴키친 · 물류센터도 근무표는 만든다
        for (Store store : storeRepository.findAll()) {
            if (!OpsGuard.SHOP_TYPES.contains(store.getStoreType()) && !"폐점".equals(store.getStatus())) {
                shiftTotal += generateShifts(store, start, today, new Random(store.getStoreNo() * 31L + days));
            }
        }
        for (Store store : storeRepository.findAll()) {
            if (!OpsGuard.SHOP_TYPES.contains(store.getStoreType()) || "폐점".equals(store.getStatus())) continue;
            // 휴점 매장은 3주 전까지만 영업한 것으로 만든다
            LocalDate lastDay = "휴점".equals(store.getStatus()) ? today.minusDays(22) : today;
            Profile p = PROFILES.getOrDefault(store.getStoreCode(), DEFAULT);
            Random rnd = new Random(store.getStoreNo() * 7919L + days);
            String pos = store.getStoreCode() + " POS"; // 매장 시스템이 보낸 기록의 작성자

            Map<Long, Double> stock = new HashMap<>();
            Map<Long, Double> avgUse = new HashMap<>();
            List<PurchaseOrder> pending = new ArrayList<>(); // 입고 대기 발주
            List<Object[]> saleRows = new ArrayList<>();
            List<Object[]> txnRows = new ArrayList<>();
            List<Object[]> logRows = new ArrayList<>(); // 매장 시스템에서 받은 기록 (수집 현황)

            for (LocalDate d = start; !d.isAfter(lastDay); d = d.plusDays(1)) {
                boolean isToday = d.equals(today);
                Timestamp at = Timestamp.valueOf(d.atTime(isToday ? now : LocalTime.of(22, 30)));

                // 1) 오늘 도착하는 발주 입고 검수 (가끔 공급업체 재고 부족으로 1포장 덜 옴)
                for (Iterator<PurchaseOrder> it = pending.iterator(); it.hasNext(); ) {
                    PurchaseOrder po = it.next();
                    if (po.getExpectedDate().isAfter(d)) continue;
                    boolean shortage = false;
                    for (PurchaseOrderLine line : po.getLines()) {
                        int got = line.getOrderPacks();
                        if (got >= 2 && rnd.nextDouble() < 0.04) {
                            got--;
                            shortage = true;
                        }
                        line.setReceivedPacks(got);
                        double qty = got * line.getPackQty();
                        stock.merge(line.getMaterial().getMaterialNo(), qty, Double::sum);
                        txnRows.add(txn(store, line.getMaterial(), d, InventoryTxn.RECEIPT, qty, null,
                                po.getPoNumber() + " · " + po.sourceName(), "PO", po.getPoNo(),
                                Timestamp.valueOf(d.atTime(9, 30)), pos));
                    }
                    logRows.add(ingestLog(store, IngestLog.DELIVERY, d, d.atTime(9, 40), po.getLines().size(), true, null));
                    po.setStatus(PurchaseOrder.RECEIVED);
                    po.setReceivedDate(d);
                    po.setReceivedBy(pos);
                    po.setReceiveNote(shortage ? "공급업체 재고 부족으로 일부 덜 받음" : null);
                    poRepository.save(po);
                    it.remove();
                }

                // 2) 하루 판매
                // 오늘 매출은 매장 POS에서 실제로 결제한 것만 들어온다 (가상 데이터는 어제까지)
                List<Receipt> receipts = isToday ? new ArrayList<>() : dayReceipts(d, start, today, days, isToday, now, p, menus, rnd);
                receipts.sort(Comparator.comparing(Receipt::time));
                long amount = 0, card = 0, cash = 0, easy = 0, dineIn = 0, takeout = 0, delivery = 0;
                double cost = 0;
                int items = 0;
                Map<Long, Double> used = new HashMap<>();
                for (int i = 0; i < receipts.size(); i++) {
                    Receipt r = receipts.get(i);
                    String no = SalesService.receiptNo(store.getStoreCode(), d, i + 1);
                    long rAmount = 0;
                    for (Item it : r.items()) {
                        Menu m = it.menu();
                        double unitCost = Math.round(m.getCost() * 10) / 10.0;
                        long lineAmount = m.getPrice() * it.qty();
                        saleRows.add(new Object[]{no, store.getStoreNo(), Date.valueOf(d), Time.valueOf(r.time()), m.getMenuNo(),
                                m.getMenuName(), m.getCategory(), it.qty(), m.getPrice(), unitCost, lineAmount, r.payment(), r.channel()});
                        rAmount += lineAmount;
                        cost += unitCost * it.qty();
                        items += it.qty();
                        for (RecipeItem ri : m.getRecipeItems()) {
                            used.merge(ri.getMaterial().getMaterialNo(), ri.getQuantity() * it.qty(), Double::sum);
                        }
                    }
                    amount += rAmount;
                    switch (r.payment()) {
                        case "카드" -> card += rAmount;
                        case "현금" -> cash += rAmount;
                        default -> easy += rAmount;
                    }
                    switch (r.channel()) {
                        case "매장" -> dineIn += rAmount;
                        case "포장" -> takeout += rAmount;
                        default -> delivery += rAmount;
                    }
                }
                receiptTotal += receipts.size();
                if (!receipts.isEmpty()) {
                    LocalDateTime sent = isToday ? LocalDateTime.of(d, now) : d.atTime(22, 5);
                    if (rnd.nextDouble() < 0.015) {
                        logRows.add(ingestLog(store, IngestLog.SALES, d, sent.minusMinutes(5), receipts.size(), false, "POS 전송 시간 초과 (자동 재전송)"));
                    }
                    logRows.add(ingestLog(store, IngestLog.SALES, d, sent, receipts.size(), true, null));
                }

                // 첫날: 기초 재고 = 첫날 사용량의 9일 치 (포장 단위로 올림)
                if (d.equals(start)) {
                    for (var e : used.entrySet()) {
                        Material m = materialByNo.get(e.getKey());
                        double qty = packs(e.getValue() * 9, m);
                        stock.put(e.getKey(), qty);
                        txnRows.add(txn(store, m, d, InventoryTxn.OPENING, qty, null, "시스템 도입 기초 재고", null, null,
                                Timestamp.valueOf(d.atTime(6, 0)), pos));
                    }
                }
                for (var e : used.entrySet()) {
                    avgUse.merge(e.getKey(), e.getValue(), (old, v) -> old * 0.7 + v * 0.3);
                }
                if (isToday) continue; // 오늘은 마감 전

                // 3) 폐기
                double wasteAmount = 0;
                for (var e : used.entrySet()) {
                    Material m = materialByNo.get(e.getKey());
                    double qty = 0;
                    String reason = null;
                    boolean perishable = "냉장".equals(m.getStorageType()) && m.getShelfLifeDays() != null && m.getShelfLifeDays() <= 30;
                    if (BAKERY.equals(m.getMaterialType()) && rnd.nextDouble() < 0.35) {
                        qty = 1 + rnd.nextInt(2); // 완제품 베이커리 · 디저트: 그날 못 판 것
                        reason = "당일 미판매";
                    } else if (perishable && rnd.nextDouble() < 0.2) {
                        qty = Math.round(e.getValue() * (0.03 + rnd.nextDouble() * 0.05));
                        reason = "유통기한 경과";
                    } else if ("PK-001".equals(m.getMaterialCode()) && rnd.nextDouble() < 0.05) {
                        qty = 5 + rnd.nextInt(16);
                        reason = "파손";
                    }
                    qty = Math.min(qty, Math.max(0, stock.getOrDefault(e.getKey(), 0.0) - e.getValue()));
                    if (qty <= 0) continue;
                    stock.merge(e.getKey(), -qty, Double::sum);
                    wasteAmount += qty * m.getUnitCost();
                    txnRows.add(txn(store, m, d, InventoryTxn.WASTE, -qty, reason, null, null, null,
                            Timestamp.valueOf(d.atTime(21, 50)), pos));
                }

                // 4) 일 마감 (대부분 시재가 맞고, 가끔 거스름돈 차이)
                long expectedCash = OpsGuard.CASH_FLOAT + cash;
                long counted = expectedCash;
                if (rnd.nextDouble() < 0.08) counted += (rnd.nextBoolean() ? 1 : -1) * (1 + rnd.nextInt(30)) * 500L;
                DailyClosing c = new DailyClosing();
                c.setStore(store);
                c.setClosingDate(d);
                c.setReceiptCount(receipts.size());
                c.setItemCount(items);
                c.setSalesAmount(amount);
                c.setCostAmount(Math.round(cost));
                c.setCardAmount(card);
                c.setCashAmount(cash);
                c.setEasyPayAmount(easy);
                c.setDineInAmount(dineIn);
                c.setTakeoutAmount(takeout);
                c.setDeliveryAmount(delivery);
                c.setCashFloat(OpsGuard.CASH_FLOAT);
                c.setCashCounted(counted);
                c.setCashDiff(counted - expectedCash);
                c.setWasteAmount(Math.round(wasteAmount));
                c.setNote(counted != expectedCash ? "시재 차이 확인 필요" : null);
                c.setClosedBy(pos);
                c.setClosedAt(at.toLocalDateTime());
                closingRepository.save(c);
                logRows.add(ingestLog(store, IngestLog.CLOSING, d, d.atTime(22, 30), 1, true, null));
                if (wasteAmount > 0) logRows.add(ingestLog(store, IngestLog.WASTE, d, d.atTime(21, 55), 1, true, null));
                closingTotal++;
                for (var e : used.entrySet()) {
                    Material m = materialByNo.get(e.getKey());
                    stock.merge(e.getKey(), -e.getValue(), Double::sum);
                    txnRows.add(txn(store, m, d, InventoryTxn.CONSUME, -e.getValue(), null, "일 마감 판매소진", "CLOSING", c.getClosingNo(), at, pos));
                }

                // 5) 일요일 마감 후 재고 실사 (우유·원두·얼음은 조금씩 모자라는 경우가 많다)
                if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    Stocktake st = new Stocktake();
                    st.setStore(store);
                    st.setCountDate(d);
                    st.setNote("주간 정기 실사");
                    st.setCreatedBy(pos);
                    st.setCreatedAt(Timestamp.valueOf(d.atTime(22, 40)).toLocalDateTime());
                    for (var e : stock.entrySet()) {
                        if (e.getValue() <= 0) continue;
                        Material m = materialByNo.get(e.getKey());
                        boolean lossy = Set.of("DY-001", "CF-001", "ET-001", "SY-001").contains(m.getMaterialCode());
                        double rate = 0;
                        if (lossy && rnd.nextDouble() < 0.6) rate = -(0.002 + rnd.nextDouble() * 0.013);
                        else if (rnd.nextDouble() < 0.12) rate = (rnd.nextDouble() - 0.6) * 0.01;
                        double expected = Math.round(e.getValue() * 10) / 10.0;
                        double counted2 = Math.max(0, Math.round(expected * (1 + rate)));
                        StocktakeLine line = new StocktakeLine();
                        line.setStocktake(st);
                        line.setMaterial(m);
                        line.setExpectedQty(expected);
                        line.setCountedQty(counted2);
                        line.setDiffQty(Math.round((counted2 - expected) * 10) / 10.0);
                        line.setUnitCost(m.getUnitCost());
                        st.getLines().add(line);
                    }
                    stocktakeRepository.save(st);
                    logRows.add(ingestLog(store, IngestLog.STOCKTAKE, d, d.atTime(22, 45), st.getLines().size(), true, null));
                    stocktakeTotal++;
                    for (StocktakeLine line : st.getLines()) {
                        if (line.getDiffQty() == 0) continue;
                        stock.merge(line.getMaterial().getMaterialNo(), line.getDiffQty(), Double::sum);
                        txnRows.add(txn(store, line.getMaterial(), d, InventoryTxn.ADJUST, line.getDiffQty(),
                                line.getDiffQty() < 0 ? "실사 부족" : "실사 초과", null, "STOCKTAKE", st.getStocktakeNo(), at, pos));
                    }
                }

                // 6) 발주: 월·목 정기 발주 (다음 발주일까지 + 납품 소요일 + 안전 2일 치 확보)
                //    그 밖의 날은 재고가 (납품 소요일 + 1일) 치도 안 남은 품목만 긴급 발주
                DayOfWeek dow = d.getDayOfWeek();
                boolean regular = dow == DayOfWeek.MONDAY || dow == DayOfWeek.THURSDAY;
                int cycle = dow == DayOfWeek.MONDAY ? 3 : 4;
                Map<Client, Map<Material, Integer>> orders = new LinkedHashMap<>();
                for (var e : avgUse.entrySet()) {
                    Material m = materialByNo.get(e.getKey());
                    if (m.getClient() == null) continue;
                    boolean fromCenter = center != null && !m.isDirect();
                    int lead = fromCenter ? PurchaseOrderService.SHIP_DAYS : Math.max(1, PurchaseOrderService.leadDays(m.getClient()));
                    double onOrder = pending.stream().flatMap(po -> po.getLines().stream())
                            .filter(l -> l.getMaterial().getMaterialNo().equals(e.getKey()))
                            .mapToDouble(l -> l.getOrderPacks() * l.getPackQty()).sum();
                    double have = stock.getOrDefault(e.getKey(), 0.0) + onOrder;
                    double need = regular
                            ? e.getValue() * (cycle + lead + PurchaseOrderService.SAFETY_DAYS) - have
                            : have < e.getValue() * (lead + 1) ? e.getValue() * (lead + 3) - have : 0;
                    if (need <= 0) continue;
                    int packCount = (int) Math.ceil(need / m.getPackQty());
                    orders.computeIfAbsent(m.getClient(), k -> new LinkedHashMap<>()).put(m, packCount);
                }
                // 매장 발주 요청(본사 승인 완료) → 공급업체별 발주서
                OrderRequest request = null;
                if (!orders.isEmpty()) {
                    request = new OrderRequest();
                    request.setStore(store);
                    request.setRequestDate(d);
                    request.setStatus(OrderRequest.APPROVED);
                    request.setNote(regular ? "정기 발주" : "긴급 발주 (재고 부족)");
                    request.setDecidedBy(user);
                    request.setDecidedAt(d.atTime(22, 55));
                    request.setCreatedAt(d.atTime(22, 50));
                    request.setRequestNumber(nextDoc(OrderRequestService.requestPrefix(d)));
                    for (var o : orders.entrySet()) {
                        for (var mp : o.getValue().entrySet()) {
                            OrderRequestLine rl = new OrderRequestLine();
                            rl.setRequest(request);
                            rl.setMaterial(mp.getKey());
                            rl.setRequestedPacks(mp.getValue());
                            rl.setApprovedPacks(mp.getValue());
                            request.getLines().add(rl);
                        }
                    }
                    logRows.add(ingestLog(store, IngestLog.ORDER, d, d.atTime(22, 50), request.getLines().size(), true, null));
                }
                List<String> poNumbers = new ArrayList<>();
                Map<Material, Integer> shipItems = new LinkedHashMap<>();
                Map<Client, Map<Material, Integer>> directOrders = new LinkedHashMap<>();
                for (var o : orders.entrySet()) {
                    for (var mp : o.getValue().entrySet()) {
                        if (center != null && !mp.getKey().isDirect()) shipItems.put(mp.getKey(), mp.getValue());
                        else directOrders.computeIfAbsent(o.getKey(), k -> new LinkedHashMap<>()).put(mp.getKey(), mp.getValue());
                    }
                }
                if (!shipItems.isEmpty()) {
                    PurchaseOrder po = new PurchaseOrder();
                    po.setStore(store);
                    po.setFromStore(center);
                    po.setKind(PurchaseOrder.KIND_SHIP);
                    po.setOrderDate(d);
                    po.setExpectedDate(d.plusDays(PurchaseOrderService.SHIP_DAYS));
                    po.setStatus(PurchaseOrder.ORDERED);
                    po.setNote("매장 발주 요청 " + request.getRequestNumber());
                    po.setOrderedBy(user);
                    po.setCreatedAt(d.atTime(22, 45));
                    po.setPoNumber(nextDoc(PurchaseOrderService.docPrefix(PurchaseOrderService.SO, d)));
                    shipItems.forEach((m, packCount) -> {
                        PurchaseOrderLine line = new PurchaseOrderLine();
                        line.setPurchaseOrder(po);
                        line.setMaterial(m);
                        line.setOrderPacks(packCount);
                        line.setPackPrice(m.getMaterialPrice());
                        line.setPackQty(m.getPackQty());
                        po.getLines().add(line);
                    });
                    poRepository.save(po);
                    pending.add(po);
                    poTotal++;
                    poNumbers.add(po.getPoNumber());
                    for (PurchaseOrderLine line : po.getLines()) {
                        centerShips.add(new CenterShip(d, line.getMaterial(), line.getOrderPacks() * line.getPackQty(), po.getPoNo(),
                                po.getPoNumber(), store.getStoreName()));
                    }
                }
                for (var o : directOrders.entrySet()) {
                    PurchaseOrder po = new PurchaseOrder();
                    po.setStore(store);
                    po.setClient(o.getKey());
                    po.setKind(PurchaseOrder.KIND_DIRECT);
                    po.setOrderDate(d);
                    po.setExpectedDate(d.plusDays(Math.max(1, PurchaseOrderService.leadDays(o.getKey()))));
                    po.setStatus(PurchaseOrder.ORDERED);
                    po.setNote("매장 발주 요청 " + request.getRequestNumber());
                    po.setOrderedBy(user);
                    po.setCreatedAt(d.atTime(22, 45));
                    po.setPoNumber(nextDoc(PurchaseOrderService.docPrefix(PurchaseOrderService.PO, d)));
                    o.getValue().forEach((m, packCount) -> {
                        PurchaseOrderLine line = new PurchaseOrderLine();
                        line.setPurchaseOrder(po);
                        line.setMaterial(m);
                        line.setOrderPacks(packCount);
                        line.setPackPrice(m.getMaterialPrice());
                        line.setPackQty(m.getPackQty());
                        po.getLines().add(line);
                    });
                    poRepository.save(po);
                    pending.add(po);
                    poTotal++;
                    poNumbers.add(po.getPoNumber());
                }
                if (request != null) {
                    request.setPoNumbers(String.join(", ", poNumbers));
                    requestRepository.save(request);
                }
            }
            // 오늘 이후 도착 예정인 발주는 '발주완료'로 남는다 (발주 · 입고 화면에서 입고 처리)
            // 오늘: 매장 번호가 짝수인 영업 매장은 본사 처리 대기 중인 추가 발주 요청을 올려 둔다
            if (lastDay.equals(today) && store.getStoreNo() % 2 == 0) {
                List<Map.Entry<Long, Double>> low = avgUse.entrySet().stream()
                        .filter(e -> e.getValue() > 0 && materialByNo.get(e.getKey()).getClient() != null)
                        .sorted(Comparator.comparingDouble(e -> stock.getOrDefault(e.getKey(), 0.0) / e.getValue()))
                        .limit(3).toList();
                if (!low.isEmpty()) {
                    OrderRequest r = new OrderRequest();
                    r.setStore(store);
                    r.setRequestDate(today);
                    r.setNeededDate(today.plusDays(2));
                    r.setStatus(OrderRequest.REQUESTED);
                    r.setNote("주말 대비 추가 요청");
                    r.setCreatedAt(LocalDateTime.of(today, now).minusMinutes(30 + store.getStoreNo()));
                    r.setRequestNumber(nextDoc(OrderRequestService.requestPrefix(today)));
                    for (var e : low) {
                        Material m = materialByNo.get(e.getKey());
                        OrderRequestLine rl = new OrderRequestLine();
                        rl.setRequest(r);
                        rl.setMaterial(m);
                        rl.setRequestedPacks((int) Math.max(1, Math.ceil(e.getValue() * 3 / m.getPackQty())));
                        r.getLines().add(rl);
                    }
                    requestRepository.save(r);
                    logRows.add(ingestLog(store, IngestLog.ORDER, today, r.getCreatedAt(), r.getLines().size(), true, null));
                }
            }
            // 휴점한 매장의 남은 발주는 취소
            if (!lastDay.equals(today)) {
                for (PurchaseOrder po : pending) {
                    po.setStatus(PurchaseOrder.CANCELED);
                    po.setReceiveNote("휴점으로 발주 취소");
                    poRepository.save(po);
                }
            }

            // 근무표: 지난 기간 + 이번 주 계획
            shiftTotal += generateShifts(store, start, lastDay.equals(today) ? today : lastDay, rnd);

            for (int i = 0; i < saleRows.size(); i += 5000) {
                jdbc.batchUpdate(SALE_SQL, saleRows.subList(i, Math.min(i + 5000, saleRows.size())));
            }
            for (int i = 0; i < txnRows.size(); i += 5000) {
                jdbc.batchUpdate(TXN_SQL, txnRows.subList(i, Math.min(i + 5000, txnRows.size())));
            }
            lineTotal += saleRows.size();
            txnTotal += txnRows.size();
            jdbc.batchUpdate("insert into ingest_logs (store_no, data_type, business_date, received_at, record_count, status, message) values (?,?,?,?,?,?,?)", logRows);
            log.info("가상 데이터: {} 영수증 {} 줄 {}", store.getStoreName(), saleRows.size(), txnRows.size());
        }

        if (center != null && !centerShips.isEmpty()) {
            int[] made = simulateCenter(center, start, today, centerShips, materials, user);
            poTotal += made[0];
            txnTotal += made[1];
        }

        // 월 마감: 지난달(가장 최근에 끝난 달)은 직접 해 볼 수 있게 남기고, 그 전 달까지만 마감
        int monthlyTotal = 0;
        for (Store store : storeRepository.findAll()) {
            if (!OpsGuard.SHOP_TYPES.contains(store.getStoreType()) || "폐점".equals(store.getStatus())) continue;
            for (YearMonth ym = YearMonth.from(start); ym.isBefore(YearMonth.from(today).minusMonths(1)); ym = ym.plusMonths(1)) {
                monthlyService.close(store.getStoreNo(), ym.toString(), "정기 월 마감");
                monthlyTotal++;
            }
        }

        int messageTotal = messageService.seedSamples(today);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messages", messageTotal);
        result.put("monthlyClosings", monthlyTotal);
        result.put("from", start);
        result.put("to", today);
        result.put("receipts", receiptTotal);
        result.put("saleLines", lineTotal);
        result.put("closings", closingTotal);
        result.put("stocktakes", stocktakeTotal);
        result.put("inventoryTxns", txnTotal);
        result.put("purchaseOrders", poTotal);
        result.put("shifts", shiftTotal);
        result.put("seconds", (System.currentTimeMillis() - started) / 1000.0);
        return result;
    }

    private List<Receipt> dayReceipts(LocalDate d, LocalDate start, LocalDate today, int days, boolean isToday, LocalTime now,
                                      Profile p, List<Menu> menus, Random rnd) {
        // 판매 가능한 메뉴와 그날 가중치 (날이 추워질수록 아이스 메뉴 ↓, 따뜻한 메뉴 ↑)
        double progress = ChronoUnit.DAYS.between(start, d) / (double) days;
        double iceFactor = 1.6 - 0.8 * progress;
        double hotFactor = 0.75 + 0.45 * progress;
        List<Menu> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (Menu m : menus) {
            boolean sellable = switch (m.getStatus()) {
                case "판매중" -> true;
                case "일시품절" -> d.isBefore(today.minusDays(4));
                case "단종" -> d.isBefore(today.minusDays(30));
                default -> false;
            };
            if (!sellable) continue;
            double w = POPULARITY.getOrDefault(m.getMenuCode(), 2.0);
            w *= ICED.contains(m.getMenuCode()) ? iceFactor : ("커피".equals(m.getCategory()) || "티".equals(m.getCategory()) ? hotFactor : 1);
            pool.add(m);
            weights.add(w);
        }
        if (pool.isEmpty()) return new ArrayList<>();

        // 손님 수: 기본 × 요일 × 완만한 성장 × 흔들림
        boolean weekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
        double dow = switch (d.getDayOfWeek()) {
            case MONDAY -> 0.94;
            case TUESDAY -> 0.97;
            case WEDNESDAY -> 1.0;
            case THURSDAY -> 1.02;
            case FRIDAY -> 1.1;
            default -> 1.0;
        };
        double factor = (weekend ? p.weekend() : dow) * (0.95 + 0.1 * progress) * (0.88 + rnd.nextDouble() * 0.24);
        int count = (int) Math.round(p.base() * factor);

        List<Receipt> receipts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int hour = OPEN_HOUR + pick(p.hours(), rnd);
            LocalTime time = LocalTime.of(hour, rnd.nextInt(60), rnd.nextInt(60));
            if (isToday && time.isAfter(now)) continue;
            double pr = rnd.nextDouble();
            String payment = pr < 0.72 ? "카드" : pr < 0.94 ? "간편결제" : "현금";
            double cr = rnd.nextDouble();
            String channel = cr < p.delivery() ? "배달" : cr < p.delivery() + 0.36 ? "포장" : "매장";
            // 배달은 현금 결제가 없다
            if ("배달".equals(channel) && "현금".equals(payment)) payment = "간편결제";
            double ir = rnd.nextDouble();
            int itemCount = ir < 0.6 ? 1 : ir < 0.88 ? 2 : ir < 0.97 ? 3 : 4;
            if ("배달".equals(channel)) itemCount = Math.max(itemCount, 2);
            Map<Menu, Integer> basket = new LinkedHashMap<>();
            for (int k = 0; k < itemCount; k++) {
                Menu m = pool.get(pickW(weights, rnd));
                basket.merge(m, rnd.nextDouble() < 0.9 ? 1 : 2, Integer::sum);
            }
            List<Item> items = new ArrayList<>();
            basket.forEach((m, q) -> items.add(new Item(m, q)));
            receipts.add(new Receipt(time, payment, channel, items));
        }
        return receipts;
    }

    /**
     * 근무표 만들기 (start가 속한 주 월요일 ~ until이 속한 주 일요일)
     *  - 점장·팀장: 주 5일 09:00~18:00
     *  - 그 밖의 정규직·계약직: 주 5일, 주마다 오픈(07~16)·마감(13~22) 번갈아
     *  - 파트타임 (업무 이름으로 판단): 주말 → 토·일 10~19, 오전 → 월~금 07~12, 저녁 → 월~금 17~22,
 *    평일·오후 → 월~금 14~19, 그 밖엔 월·수·금 08~13
     *  - 판매 매장이 아닌 곳(센트럴키친·물류센터)은 평일 06~15 / 08~17
     */
    private int generateShifts(Store store, LocalDate start, LocalDate until, Random rnd) {
        List<User> staff = userRepository.findAll().stream()
                .filter(u -> u.getStore() != null && u.getStore().getStoreNo().equals(store.getStoreNo()))
                .toList();
        if (staff.isEmpty()) return 0;
        boolean shop = OpsGuard.SHOP_TYPES.contains(store.getStoreType());
        LocalDate from = ScheduleService.weekStart(start);
        LocalDate to = ScheduleService.weekStart(until).plusDays(6);
        List<Object[]> rows = new ArrayList<>();
        for (User u : staff) {
            String role = u.getRole() == null ? "" : u.getRole().getRoleName();
            String work = u.getUserWork() == null ? "" : u.getUserWork();
            boolean partTime = ScheduleService.PART_TIME.equals(u.getEmploymentType());
            boolean lead = role.equals("점장") || role.equals("팀장");
            // 쉬는 날 두 개 (직원마다 다르게)
            int off1 = (int) (u.getUserNo() % 7), off2 = (off1 + 3) % 7;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                int dow = d.getDayOfWeek().getValue() - 1; // 월=0
                boolean weekend = dow >= 5;
                LocalTime s, e;
                String pos;
                int brk;
                if (partTime) {
                    if (work.contains("주말")) {
                        if (!weekend) continue;
                        s = LocalTime.of(10, 0); e = LocalTime.of(19, 0); brk = 60; pos = "미들";
                    } else if (work.contains("오전")) {
                        if (weekend) continue;
                        s = LocalTime.of(7, 0); e = LocalTime.of(12, 0); brk = 30; pos = "오픈";
                    } else if (work.contains("저녁")) {
                        if (weekend) continue;
                        s = LocalTime.of(17, 0); e = LocalTime.of(22, 0); brk = 30; pos = "마감";
                    } else if (work.contains("평일") || work.contains("오후")) {
                        if (weekend) continue;
                        s = LocalTime.of(14, 0); e = LocalTime.of(19, 0); brk = 30; pos = "미들";
                    } else {
                        if (dow != 0 && dow != 2 && dow != 4) continue;
                        s = LocalTime.of(8, 0); e = LocalTime.of(13, 0); brk = 30; pos = "오픈";
                    }
                } else if (!shop) {
                    if (weekend) continue;
                    boolean kitchen = "센트럴키친".equals(store.getStoreType());
                    s = kitchen ? LocalTime.of(6, 0) : LocalTime.of(8, 0);
                    e = kitchen ? LocalTime.of(15, 0) : LocalTime.of(17, 0);
                    brk = 60; pos = kitchen ? "생산" : "입출고";
                } else if (lead) {
                    if (dow == off1 || dow == 6) continue;
                    s = LocalTime.of(9, 0); e = LocalTime.of(18, 0); brk = 60; pos = "미들";
                } else {
                    if (dow == off1 || dow == off2) continue;
                    boolean open = (ScheduleService.weekStart(d).toEpochDay() / 7 + u.getUserNo()) % 2 == 0; // 월요일 기준 주마다 교대
                    s = open ? LocalTime.of(7, 0) : LocalTime.of(13, 0);
                    e = open ? LocalTime.of(16, 0) : LocalTime.of(22, 0);
                    brk = 60; pos = open ? "오픈" : "마감";
                }
                // 가끔 연장 근무 (지난 날만)
                String note = null;
                if (!d.isAfter(until) && d.isBefore(BizClock.today()) && rnd.nextDouble() < 0.06 && e.getHour() < 22) {
                    e = e.plusHours(1);
                    note = "연장 1시간";
                }
                rows.add(new Object[]{store.getStoreNo(), u.getUserNo(), Date.valueOf(d), Time.valueOf(s), Time.valueOf(e), brk, pos, note});
            }
        }
        jdbc.batchUpdate("insert into shifts (store_no, user_no, work_date, start_time, end_time, break_minutes, position, note) " +
                "values (?,?,?,?,?,?,?,?)", rows);
        return rows.size();
    }

    // 물류센터가 매장으로 내보낸 한 줄
    private record CenterShip(LocalDate date, Material material, double qty, Long poNo, String poNumber, String storeName) {}

    // 물류센터 재고: 기초 재고(출고량 14일 치) → 매장 출고 → 월·목 정기 보충 발주 → 납품일 입고.
    // 오늘 이후 도착할 보충 발주는 '발주완료'로 남겨 본사가 직접 입고 처리해 볼 수 있게 한다.
    private int[] simulateCenter(Store center, LocalDate start, LocalDate today, List<CenterShip> ships, List<Material> materials, String user) {
        String by = center.getStoreCode() + " 물류팀";
        Map<LocalDate, List<CenterShip>> byDate = new HashMap<>();
        Map<Long, Double> totalShip = new HashMap<>();
        for (CenterShip s : ships) {
            byDate.computeIfAbsent(s.date(), k -> new ArrayList<>()).add(s);
            totalShip.merge(s.material().getMaterialNo(), s.qty(), Double::sum);
        }
        long span = Math.max(1, ChronoUnit.DAYS.between(start, today));
        Map<Long, Double> avg = new HashMap<>();
        totalShip.forEach((k, v) -> avg.put(k, v / span));

        Map<Long, Double> stock = new HashMap<>();
        List<Object[]> rows = new ArrayList<>();
        List<PurchaseOrder> pending = new ArrayList<>();
        int poCount = 0;
        for (Material m : materials) {
            double a = avg.getOrDefault(m.getMaterialNo(), 0.0);
            if (a <= 0 || m.isDirect()) continue;
            double qty = packs(a * 14, m);
            stock.put(m.getMaterialNo(), qty);
            rows.add(txn(center, m, start, InventoryTxn.OPENING, qty, null, "물류센터 기초 재고", null, null, Timestamp.valueOf(start.atTime(6, 0)), by));
        }
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            // 1) 보충 발주 입고 (오늘 도착분부터는 본사가 입고 처리하도록 남긴다)
            for (Iterator<PurchaseOrder> it = pending.iterator(); it.hasNext(); ) {
                PurchaseOrder po = it.next();
                if (!po.getExpectedDate().isBefore(today) || po.getExpectedDate().isAfter(d)) continue;
                for (PurchaseOrderLine line : po.getLines()) {
                    line.setReceivedPacks(line.getOrderPacks());
                    double qty = line.getOrderPacks() * line.getPackQty();
                    stock.merge(line.getMaterial().getMaterialNo(), qty, Double::sum);
                    rows.add(txn(center, line.getMaterial(), d, InventoryTxn.RECEIPT, qty, null, po.getPoNumber() + " · " + po.sourceName(),
                            "PO", po.getPoNo(), Timestamp.valueOf(d.atTime(8, 0)), by));
                }
                po.setStatus(PurchaseOrder.RECEIVED);
                po.setReceivedDate(d);
                po.setReceivedBy(by);
                poRepository.save(po);
                it.remove();
            }
            // 2) 매장 출고
            for (CenterShip s : byDate.getOrDefault(d, List.of())) {
                stock.merge(s.material().getMaterialNo(), -s.qty(), Double::sum);
                rows.add(txn(center, s.material(), d, InventoryTxn.SHIP, -s.qty(), null, s.poNumber() + " → " + s.storeName(),
                        "PO", s.poNo(), Timestamp.valueOf(d.atTime(22, 45)), by));
            }
            // 3) 월·목 정기 보충: 다음 보충까지 + 납품 소요일 + 안전 3일 치 확보
            DayOfWeek dow = d.getDayOfWeek();
            if (d.isBefore(today) && (dow == DayOfWeek.MONDAY || dow == DayOfWeek.THURSDAY)) {
                int cycle = dow == DayOfWeek.MONDAY ? 3 : 4;
                Map<Client, Map<Material, Integer>> orders = new LinkedHashMap<>();
                for (Material m : materials) {
                    double a = avg.getOrDefault(m.getMaterialNo(), 0.0);
                    if (a <= 0 || m.isDirect() || m.getClient() == null) continue;
                    int lead = Math.max(1, PurchaseOrderService.leadDays(m.getClient()));
                    double onOrder = pending.stream().flatMap(po -> po.getLines().stream())
                            .filter(l -> l.getMaterial().getMaterialNo().equals(m.getMaterialNo()))
                            .mapToDouble(l -> l.getOrderPacks() * l.getPackQty()).sum();
                    double need = a * (cycle + lead + 3) - stock.getOrDefault(m.getMaterialNo(), 0.0) - onOrder;
                    if (need <= 0) continue;
                    orders.computeIfAbsent(m.getClient(), k -> new LinkedHashMap<>()).put(m, (int) Math.ceil(need / m.getPackQty()));
                }
                for (var o : orders.entrySet()) {
                    PurchaseOrder po = new PurchaseOrder();
                    po.setStore(center);
                    po.setClient(o.getKey());
                    po.setKind(PurchaseOrder.KIND_RESTOCK);
                    po.setOrderDate(d);
                    po.setExpectedDate(d.plusDays(Math.max(1, PurchaseOrderService.leadDays(o.getKey()))));
                    po.setStatus(PurchaseOrder.ORDERED);
                    po.setNote("물류센터 정기 보충");
                    po.setOrderedBy(user);
                    po.setCreatedAt(d.atTime(16, 0));
                    po.setPoNumber(nextDoc(PurchaseOrderService.docPrefix(PurchaseOrderService.PO, d)));
                    o.getValue().forEach((m, packCount) -> {
                        PurchaseOrderLine line = new PurchaseOrderLine();
                        line.setPurchaseOrder(po);
                        line.setMaterial(m);
                        line.setOrderPacks(packCount);
                        line.setPackPrice(m.getMaterialPrice());
                        line.setPackQty(m.getPackQty());
                        po.getLines().add(line);
                    });
                    poRepository.save(po);
                    pending.add(po);
                    poCount++;
                }
            }
        }
        for (int i = 0; i < rows.size(); i += 5000) {
            jdbc.batchUpdate(TXN_SQL, rows.subList(i, Math.min(i + 5000, rows.size())));
        }
        log.info("가상 데이터: {} 보충 발주 {} 재고 변동 {}", center.getStoreName(), poCount, rows.size());
        return new int[]{poCount, rows.size()};
    }

    private static Object[] ingestLog(Store store, String type, LocalDate day, LocalDateTime at, int count, boolean ok, String message) {
        return new Object[]{store.getStoreNo(), type, Date.valueOf(day), Timestamp.valueOf(at), count, ok ? "성공" : "실패", message};
    }

    private static Object[] txn(Store store, Material m, LocalDate d, String type, double qty, String reason, String note,
                                String refType, Long refNo, Timestamp at, String user) {
        return new Object[]{store.getStoreNo(), m.getMaterialNo(), Date.valueOf(d), type, Math.round(qty * 10) / 10.0,
                m.getUnitCost(), reason, note, refType, refNo, at, user};
    }

    // 포장 단위로 올림
    private static double packs(double qty, Material m) {
        double pack = m.getPackQty() == null || m.getPackQty() <= 0 ? 1 : m.getPackQty();
        return Math.max(1, Math.ceil(qty / pack)) * pack;
    }

    private static int pick(double[] w, Random rnd) {
        double sum = Arrays.stream(w).sum();
        double r = rnd.nextDouble() * sum;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r <= 0) return i;
        }
        return w.length - 1;
    }

    private static int pickW(List<Double> w, Random rnd) {
        double sum = w.stream().mapToDouble(Double::doubleValue).sum();
        double r = rnd.nextDouble() * sum;
        for (int i = 0; i < w.size(); i++) {
            r -= w.get(i);
            if (r <= 0) return i;
        }
        return w.size() - 1;
    }
}
