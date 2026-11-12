package com.example.docksystem_erp.service.Sales;

import com.example.docksystem_erp.util.BizClock;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.entity.Menu.Menu;
import com.example.docksystem_erp.entity.Sales.Sale;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Menu.MenuRepository;
import com.example.docksystem_erp.repository.Sales.SaleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class SalesService {
    public static final List<String> PAYMENTS = List.of("카드", "간편결제", "현금");
    public static final List<String> CHANNELS = List.of("매장", "포장", "배달");
    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final JdbcTemplate jdbc;
    private final SaleRepository saleRepository;
    private final MenuRepository menuRepository;
    private final OpsGuard guard;

    // 영수증 번호: 매장코드-날짜-순번 (예: ST-GN01-261106-0012)
    public static String receiptNo(String storeCode, LocalDate date, int seq) {
        return String.format("%s-%s-%04d", storeCode, date.format(RECEIPT_DATE), seq);
    }

    // 하루 판매 요약: 합계 · 시간대별 · 메뉴별 · 결제수단별 · 채널별 · 지난주 같은 요일 비교
    @Transactional(readOnly = true)
    public Map<String, Object> summary(Long storeNo, LocalDate date) {
        Store store = guard.store(storeNo);
        Date day = Date.valueOf(date);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storeNo", storeNo);
        result.put("storeName", store.getStoreName());
        result.put("date", date);
        result.put("closed", guard.isClosed(storeNo, date));
        result.put("totals", totals(storeNo, date, null));
        // 오늘은 영업 중이므로 지난주 같은 요일도 '지금 시각까지'만 비교한다
        boolean today = date.equals(BizClock.today());
        result.put("lastWeek", totals(storeNo, date.minusDays(7), today ? BizClock.time() : null));
        result.put("compareUntil", today ? BizClock.time().truncatedTo(ChronoUnit.MINUTES).toString() : null);
        result.put("byHour", jdbc.queryForList(
                "select hour(sale_time) as hour, sum(amount) as amount, count(distinct receipt_no) as receipts " +
                "from sales where store_no = ? and sale_date = ? group by hour(sale_time) order by hour", storeNo, day));
        result.put("byMenu", jdbc.queryForList(
                "select menu_no as menuNo, menu_name as menuName, category, sum(quantity) as quantity, sum(amount) as amount, " +
                "round(sum(unit_cost * quantity)) as cost from sales where store_no = ? and sale_date = ? " +
                "group by menu_no, menu_name, category order by amount desc", storeNo, day));
        result.put("byPayment", jdbc.queryForList(
                "select payment_method as name, sum(amount) as amount, count(distinct receipt_no) as receipts " +
                "from sales where store_no = ? and sale_date = ? group by payment_method order by amount desc", storeNo, day));
        result.put("byChannel", jdbc.queryForList(
                "select channel as name, sum(amount) as amount, count(distinct receipt_no) as receipts " +
                "from sales where store_no = ? and sale_date = ? group by channel order by amount desc", storeNo, day));
        return result;
    }

    // until이 있으면 그 시각까지 판매만 합산
    public Map<String, Object> totals(Long storeNo, LocalDate date, LocalTime until) {
        String sql = "select count(distinct receipt_no) as receipts, coalesce(sum(quantity), 0) as items, " +
                "coalesce(sum(amount), 0) as amount, coalesce(round(sum(unit_cost * quantity)), 0) as cost " +
                "from sales where store_no = ? and sale_date = ?";
        Map<String, Object> t = until == null
                ? jdbc.queryForMap(sql, storeNo, Date.valueOf(date))
                : jdbc.queryForMap(sql + " and sale_time <= ?", storeNo, Date.valueOf(date), java.sql.Time.valueOf(until));
        long receipts = ((Number) t.get("receipts")).longValue();
        long amount = ((Number) t.get("amount")).longValue();
        double cost = ((Number) t.get("cost")).doubleValue();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", date);
        out.put("receipts", receipts);
        out.put("items", ((Number) t.get("items")).longValue());
        out.put("amount", amount);
        out.put("cost", cost);
        out.put("avgTicket", receipts == 0 ? 0 : Math.round((double) amount / receipts));
        out.put("costRate", amount == 0 ? 0 : Math.round(cost / amount * 1000) / 10.0);
        return out;
    }

    // POS 판매 입력 (영수증 1장)
    public Map<String, Object> createReceipt(OpsRequests.Receipt req) {
        Store store = guard.shop(req.storeNo());
        if (!"영업중".equals(store.getStatus())) {
            throw new IllegalStateException(store.getStoreName() + "은(는) 지금 " + store.getStatus() + " 상태라 판매를 입력할 수 없습니다.");
        }
        LocalDate date = req.saleDate() == null ? BizClock.today() : req.saleDate();
        guard.requireOpenDay(store, date);
        if (!PAYMENTS.contains(req.paymentMethod())) throw new IllegalArgumentException("결제수단을 선택해 주세요.");
        if (!CHANNELS.contains(req.channel())) throw new IllegalArgumentException("판매 채널을 선택해 주세요.");
        if (req.items() == null || req.items().isEmpty()) throw new IllegalArgumentException("메뉴를 하나 이상 담아 주세요.");

        Integer seq = jdbc.queryForObject(
                "select count(distinct receipt_no) from sales where store_no = ? and sale_date = ?",
                Integer.class, store.getStoreNo(), Date.valueOf(date));
        String no = receiptNo(store.getStoreCode(), date, (seq == null ? 0 : seq) + 1);
        LocalTime time = date.equals(BizClock.today()) ? BizClock.time().truncatedTo(ChronoUnit.SECONDS) : LocalTime.of(12, 0);

        long total = 0;
        for (OpsRequests.ReceiptItem item : req.items()) {
            if (item.quantity() == null || item.quantity() <= 0) throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
            Menu menu = menuRepository.findById(item.menuNo())
                    .orElseThrow(() -> new EntityNotFoundException("해당 메뉴를 찾을 수 없습니다: " + item.menuNo()));
            if (!"판매중".equals(menu.getStatus())) {
                throw new IllegalStateException(menu.getMenuName() + "은(는) " + menu.getStatus() + " 상태라 판매할 수 없습니다.");
            }
            Sale sale = new Sale();
            sale.setReceiptNo(no);
            sale.setStore(store);
            sale.setSaleDate(date);
            sale.setSaleTime(time);
            sale.setMenu(menu);
            sale.setMenuName(menu.getMenuName());
            sale.setCategory(menu.getCategory());
            sale.setQuantity(item.quantity());
            sale.setUnitPrice(menu.getPrice());
            sale.setUnitCost(Math.round(menu.getCost() * 10) / 10.0);
            sale.setAmount(menu.getPrice() * item.quantity());
            sale.setPaymentMethod(req.paymentMethod());
            sale.setChannel(req.channel());
            saleRepository.save(sale);
            total += sale.getAmount();
        }
        return Map.of("receiptNo", no, "amount", total);
    }

    /**
     * 매장 POS에서 보낸 영수증 1장 저장 (수집 API).
     * 같은 영수증 번호가 이미 있으면 다시 보낸 것으로 보고 건너뛴다(false). 메뉴는 메뉴 코드로 찾는다.
     */
    public boolean importReceipt(Store store, String receiptNo, LocalDate date, LocalTime time, String payment, String channel,
                                 List<Map.Entry<Menu, Integer>> items) {
        if (receiptNo != null && !receiptNo.isBlank() && saleRepository.existsByReceiptNo(receiptNo)) return false;
        LocalDate day = date == null ? BizClock.today() : date;
        guard.requireOpenDay(store, day);
        if (!PAYMENTS.contains(payment)) throw new IllegalArgumentException("결제수단은 " + PAYMENTS + " 중 하나여야 합니다: " + payment);
        if (!CHANNELS.contains(channel)) throw new IllegalArgumentException("판매 채널은 " + CHANNELS + " 중 하나여야 합니다: " + channel);
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("영수증에 메뉴가 없습니다.");
        String no = receiptNo;
        if (no == null || no.isBlank()) {
            Integer seq = jdbc.queryForObject("select count(distinct receipt_no) from sales where store_no = ? and sale_date = ?",
                    Integer.class, store.getStoreNo(), Date.valueOf(day));
            no = receiptNo(store.getStoreCode(), day, (seq == null ? 0 : seq) + 1);
        }
        LocalTime t = time == null ? BizClock.time().truncatedTo(ChronoUnit.SECONDS) : time;
        for (var e : items) {
            Menu menu = e.getKey();
            int qty = e.getValue() == null ? 0 : e.getValue();
            if (qty <= 0) throw new IllegalArgumentException("수량은 1개 이상이어야 합니다: " + menu.getMenuName());
            Sale sale = new Sale();
            sale.setReceiptNo(no);
            sale.setStore(store);
            sale.setSaleDate(day);
            sale.setSaleTime(t);
            sale.setMenu(menu);
            sale.setMenuName(menu.getMenuName());
            sale.setCategory(menu.getCategory());
            sale.setQuantity(qty);
            sale.setUnitPrice(menu.getPrice());
            sale.setUnitCost(Math.round(menu.getCost() * 10) / 10.0);
            sale.setAmount(menu.getPrice() * qty);
            sale.setPaymentMethod(payment);
            sale.setChannel(channel);
            saleRepository.save(sale);
        }
        return true;
    }

    // 영수증 취소 (마감 전만)
    public void deleteReceipt(String receiptNo) {
        List<Sale> lines = saleRepository.findByReceiptNo(receiptNo);
        if (lines.isEmpty()) throw new EntityNotFoundException("해당 영수증을 찾을 수 없습니다: " + receiptNo);
        Sale first = lines.get(0);
        guard.requireOpenDay(first.getStore(), first.getSaleDate());
        saleRepository.deleteAll(lines);
    }
}
