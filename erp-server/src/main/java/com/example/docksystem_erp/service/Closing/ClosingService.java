package com.example.docksystem_erp.service.Closing;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.entity.Closing.DailyClosing;
import com.example.docksystem_erp.entity.Inventory.InventoryTxn;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Closing.DailyClosingRepository;
import com.example.docksystem_erp.repository.Inventory.InventoryTxnRepository;
import com.example.docksystem_erp.service.Sales.OpsGuard;
import com.example.docksystem_erp.util.CurrentUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class ClosingService {
    private final JdbcTemplate jdbc;
    private final OpsGuard guard;
    private final DailyClosingRepository closingRepository;
    private final InventoryTxnRepository txnRepository;

    // 기간 안 날짜별 매출과 마감 여부 (최근 날짜부터)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> status(Long storeNo, LocalDate from, LocalDate to) {
        guard.store(storeNo);
        Map<LocalDate, Map<String, Object>> sales = new HashMap<>();
        jdbc.queryForList(
                "select sale_date, sum(amount) as amount, count(distinct receipt_no) as receipts from sales " +
                "where store_no = ? and sale_date between ? and ? group by sale_date",
                storeNo, Date.valueOf(from), Date.valueOf(to))
            .forEach(r -> sales.put(((Date) r.get("sale_date")).toLocalDate(), r));
        Map<LocalDate, DailyClosing> closings = new HashMap<>();
        closingRepository.findByStore_StoreNoAndClosingDateBetweenOrderByClosingDateDesc(storeNo, from, to)
                .forEach(c -> closings.put(c.getClosingDate(), c));

        List<Map<String, Object>> days = new ArrayList<>();
        for (LocalDate d = to; !d.isBefore(from); d = d.minusDays(1)) {
            Map<String, Object> s = sales.get(d);
            DailyClosing c = closings.get(d);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d);
            row.put("amount", s == null ? 0 : ((Number) s.get("amount")).longValue());
            row.put("receipts", s == null ? 0 : ((Number) s.get("receipts")).longValue());
            row.put("closed", c != null);
            row.put("closingNo", c == null ? null : c.getClosingNo());
            row.put("cashDiff", c == null ? null : c.getCashDiff());
            days.add(row);
        }
        return days;
    }

    // 마감 화면에 보여 줄 숫자 (마감 전: 계산값, 마감 후: 확정값)
    @Transactional(readOnly = true)
    public Map<String, Object> preview(Long storeNo, LocalDate date) {
        Store store = guard.store(storeNo);
        Map<String, Object> result = new LinkedHashMap<>(compute(storeNo, date));
        result.put("storeNo", storeNo);
        result.put("storeName", store.getStoreName());
        result.put("date", date);
        result.put("closing", closingRepository.findByStore_StoreNoAndClosingDate(storeNo, date).map(this::toMap).orElse(null));
        result.put("consumption", consumption(storeNo, date));
        Date earlier = jdbc.queryForObject(
                "select min(s.sale_date) from sales s where s.store_no = ? and s.sale_date < ? and not exists " +
                "(select 1 from daily_closings c where c.store_no = s.store_no and c.closing_date = s.sale_date)",
                Date.class, storeNo, Date.valueOf(date));
        result.put("earliestUnclosed", earlier == null ? null : earlier.toLocalDate());
        return result;
    }

    // 마감 확정
    public Map<String, Object> close(OpsRequests.Closing req) {
        Store store = guard.store(req.storeNo());
        LocalDate date = req.date() == null ? LocalDate.now() : req.date();
        guard.requireOpenDay(store, date);
        if (req.cashCounted() == null || req.cashCounted() < 0) {
            throw new IllegalArgumentException("실제로 센 현금(시재)을 입력해 주세요.");
        }
        Map<String, Object> c = compute(store.getStoreNo(), date);

        DailyClosing closing = new DailyClosing();
        closing.setStore(store);
        closing.setClosingDate(date);
        closing.setReceiptCount(((Number) c.get("receipts")).intValue());
        closing.setItemCount(((Number) c.get("items")).intValue());
        closing.setSalesAmount(((Number) c.get("amount")).longValue());
        closing.setCostAmount(((Number) c.get("cost")).doubleValue());
        closing.setCardAmount(((Number) c.get("cardAmount")).longValue());
        closing.setCashAmount(((Number) c.get("cashAmount")).longValue());
        closing.setEasyPayAmount(((Number) c.get("easyPayAmount")).longValue());
        closing.setDineInAmount(((Number) c.get("dineInAmount")).longValue());
        closing.setTakeoutAmount(((Number) c.get("takeoutAmount")).longValue());
        closing.setDeliveryAmount(((Number) c.get("deliveryAmount")).longValue());
        closing.setCashFloat(OpsGuard.CASH_FLOAT);
        closing.setCashCounted(req.cashCounted());
        closing.setCashDiff(req.cashCounted() - ((Number) c.get("expectedCash")).longValue());
        closing.setWasteAmount(((Number) c.get("wasteAmount")).doubleValue());
        closing.setNote(req.note());
        closing.setClosedBy(CurrentUser.id());
        closing.setClosedAt(LocalDateTime.now());
        closingRepository.save(closing);

        postConsumption(store.getStoreNo(), date, closing.getClosingNo(), CurrentUser.id());
        return toMap(closing);
    }

    // 마감 취소: 판매소진 재고 기록도 함께 되돌린다
    public void cancel(Long closingNo) {
        DailyClosing closing = closingRepository.findById(closingNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 마감을 찾을 수 없습니다: " + closingNo));
        guard.requireOpenMonth(closing.getStore(), closing.getClosingDate());
        txnRepository.deleteByRefTypeAndRefNo("CLOSING", closingNo);
        closingRepository.delete(closing);
    }

    // 판매 · 결제 · 채널 · 폐기 합계
    private Map<String, Object> compute(Long storeNo, LocalDate date) {
        Date day = Date.valueOf(date);
        Map<String, Object> t = jdbc.queryForMap(
                "select count(distinct receipt_no) as receipts, coalesce(sum(quantity), 0) as items, " +
                "coalesce(sum(amount), 0) as amount, coalesce(round(sum(unit_cost * quantity)), 0) as cost, " +
                "coalesce(sum(case when payment_method = '카드' then amount end), 0) as cardAmount, " +
                "coalesce(sum(case when payment_method = '현금' then amount end), 0) as cashAmount, " +
                "coalesce(sum(case when payment_method = '간편결제' then amount end), 0) as easyPayAmount, " +
                "coalesce(sum(case when channel = '매장' then amount end), 0) as dineInAmount, " +
                "coalesce(sum(case when channel = '포장' then amount end), 0) as takeoutAmount, " +
                "coalesce(sum(case when channel = '배달' then amount end), 0) as deliveryAmount " +
                "from sales where store_no = ? and sale_date = ?", storeNo, day);
        Double waste = jdbc.queryForObject(
                "select coalesce(round(sum(-quantity * unit_cost)), 0) from inventory_txns " +
                "where store_no = ? and txn_date = ? and txn_type = ?", Double.class, storeNo, day, InventoryTxn.WASTE);
        Map<String, Object> out = new LinkedHashMap<>();
        t.forEach((k, v) -> out.put(k, v));
        long amount = ((Number) t.get("amount")).longValue();
        long receipts = ((Number) t.get("receipts")).longValue();
        out.put("avgTicket", receipts == 0 ? 0 : Math.round((double) amount / receipts));
        out.put("costRate", amount == 0 ? 0 : Math.round(((Number) t.get("cost")).doubleValue() / amount * 1000) / 10.0);
        out.put("wasteAmount", waste == null ? 0 : waste);
        out.put("cashFloat", OpsGuard.CASH_FLOAT);
        out.put("expectedCash", OpsGuard.CASH_FLOAT + ((Number) t.get("cashAmount")).longValue());
        return out;
    }

    // 레시피 기준 식자재 소진량 (판매 수량 × 레시피 사용량)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> consumption(Long storeNo, LocalDate date) {
        return jdbc.queryForList(
                "select m.material_no as materialNo, m.material_name as materialName, m.material_unit as unit, " +
                "sum(s.quantity * ri.quantity) as quantity, round(sum(s.quantity * ri.quantity) * m.material_price / m.pack_qty) as cost " +
                "from sales s join recipe_items ri on ri.menu_no = s.menu_no join materials m on m.material_no = ri.material_no " +
                "where s.store_no = ? and s.sale_date = ? group by m.material_no, m.material_name, m.material_unit, m.material_price, m.pack_qty " +
                "order by cost desc", storeNo, Date.valueOf(date));
    }

    private void postConsumption(Long storeNo, LocalDate date, Long closingNo, String user) {
        List<Object[]> rows = new ArrayList<>();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        for (Map<String, Object> c : consumption(storeNo, date)) {
            double qty = ((Number) c.get("quantity")).doubleValue();
            double cost = ((Number) c.get("cost")).doubleValue();
            rows.add(new Object[]{storeNo, c.get("materialNo"), Date.valueOf(date), InventoryTxn.CONSUME,
                    -qty, qty == 0 ? 0 : cost / qty, null, "일 마감 판매소진", "CLOSING", closingNo, now, user});
        }
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("insert into inventory_txns (store_no, material_no, txn_date, txn_type, quantity, unit_cost, " +
                    "reason, note, ref_type, ref_no, created_at, created_by) values (?,?,?,?,?,?,?,?,?,?,?,?)", rows);
        }
    }

    private Map<String, Object> toMap(DailyClosing c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("closingNo", c.getClosingNo());
        m.put("closingDate", c.getClosingDate());
        m.put("salesAmount", c.getSalesAmount());
        m.put("receiptCount", c.getReceiptCount());
        m.put("cashFloat", c.getCashFloat());
        m.put("cashCounted", c.getCashCounted());
        m.put("cashDiff", c.getCashDiff());
        m.put("wasteAmount", c.getWasteAmount());
        m.put("note", c.getNote());
        m.put("closedBy", c.getClosedBy());
        m.put("closedAt", c.getClosedAt());
        return m;
    }
}
