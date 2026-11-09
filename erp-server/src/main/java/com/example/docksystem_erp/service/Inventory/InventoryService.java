package com.example.docksystem_erp.service.Inventory;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.entity.Inventory.InventoryTxn;
import com.example.docksystem_erp.entity.Inventory.Stocktake;
import com.example.docksystem_erp.entity.Inventory.StocktakeLine;
import com.example.docksystem_erp.entity.Material.Material;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Inventory.InventoryTxnRepository;
import com.example.docksystem_erp.repository.Inventory.StocktakeRepository;
import com.example.docksystem_erp.repository.Material.MaterialRepository;
import com.example.docksystem_erp.service.Sales.OpsGuard;
import com.example.docksystem_erp.util.CurrentUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class InventoryService {
    public static final List<String> WASTE_REASONS = List.of("유통기한 경과", "당일 미판매", "제조 실수", "파손", "품질 불량", "기타");
    // 일평균 사용량을 계산할 기간
    private static final int USAGE_DAYS = 14;

    private final JdbcTemplate jdbc;
    private final OpsGuard guard;
    private final MaterialRepository materialRepository;
    private final InventoryTxnRepository txnRepository;
    private final StocktakeRepository stocktakeRepository;

    // 매장 현재고: 식자재별 수량 · 금액 · 최근 14일 일평균 사용량 · 남은 일수
    @Transactional(readOnly = true)
    public List<Map<String, Object>> stock(Long storeNo) {
        guard.store(storeNo);
        LocalDate since = LocalDate.now().minusDays(USAGE_DAYS);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select m.material_no as materialNo, m.material_code as materialCode, m.material_name as materialName, " +
                "m.material_type as materialType, m.material_unit as unit, m.material_size as packLabel, m.pack_qty as packQty, " +
                "m.storage_type as storageType, m.material_price as materialPrice, " +
                "coalesce(sum(t.quantity), 0) as quantity, " +
                "coalesce(sum(case when t.txn_type = ? and t.txn_date > ? then -t.quantity else 0 end), 0) as used, " +
                "count(t.txn_no) as txnCount, max(t.txn_date) as lastTxnDate, coalesce(max(o.qty), 0) as onOrder " +
                "from materials m left join inventory_txns t on t.material_no = m.material_no and t.store_no = ? " +
                // 입고 대기 중인 발주 수량 (사용 단위)
                "left join (select l.material_no, sum(l.order_packs * l.pack_qty) as qty from purchase_order_lines l " +
                "join purchase_orders p on p.po_no = l.po_no where p.store_no = ? and p.status = '발주완료' group by l.material_no) o " +
                "on o.material_no = m.material_no " +
                "group by m.material_no, m.material_code, m.material_name, m.material_type, m.material_unit, m.material_size, " +
                "m.pack_qty, m.storage_type, m.material_price order by m.material_type, m.material_code",
                InventoryTxn.CONSUME, Date.valueOf(since), storeNo, storeNo);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            double qty = ((Number) r.get("quantity")).doubleValue();
            double packQty = ((Number) r.get("packQty")).doubleValue();
            double unitCost = ((Number) r.get("materialPrice")).doubleValue() / packQty;
            double avgDaily = ((Number) r.get("used")).doubleValue() / USAGE_DAYS;
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.remove("used");
            m.put("quantity", round1(qty));
            m.put("packs", round1(qty / packQty));
            m.put("unitCost", unitCost);
            m.put("value", Math.round(qty * unitCost));
            m.put("avgDaily", round1(avgDaily));
            Double cover = avgDaily > 0 ? round1(qty / avgDaily) : null;
            m.put("coverDays", cover);
            // 남은 일수로 상태 판단 (사용 기록이 없으면 '미사용')
            String status = avgDaily <= 0 ? (qty > 0 ? "미사용" : "없음") : cover < 2 ? "부족" : cover < 4 ? "주의" : "정상";
            if (qty < 0) status = "부족";
            m.put("status", status);
            out.add(m);
        }
        return out;
    }

    // 재고 변동 기록
    @Transactional(readOnly = true)
    public List<Map<String, Object>> txns(Long storeNo, LocalDate from, LocalDate to, String type, Long materialNo) {
        guard.store(storeNo);
        StringBuilder sql = new StringBuilder(
                "select t.txn_no as txnNo, t.txn_date as txnDate, t.txn_type as txnType, t.material_no as materialNo, " +
                "m.material_name as materialName, m.material_unit as unit, t.quantity, round(t.quantity * t.unit_cost) as amount, " +
                "t.reason, t.note, t.ref_type as refType, t.created_by as createdBy, t.created_at as createdAt " +
                "from inventory_txns t join materials m on m.material_no = t.material_no " +
                "where t.store_no = ? and t.txn_date between ? and ?");
        List<Object> args = new ArrayList<>(List.of(storeNo, Date.valueOf(from), Date.valueOf(to)));
        if (type != null && !type.isBlank()) {
            sql.append(" and t.txn_type = ?");
            args.add(type);
        }
        if (materialNo != null) {
            sql.append(" and t.material_no = ?");
            args.add(materialNo);
        }
        sql.append(" order by t.txn_date desc, t.txn_no desc limit 2000");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    // 폐기 등록
    public Map<String, Object> addWaste(OpsRequests.Waste req) {
        Store store = guard.store(req.storeNo());
        LocalDate date = req.date() == null ? LocalDate.now() : req.date();
        guard.requireOpenDay(store, date);
        if (req.quantity() == null || req.quantity() <= 0) throw new IllegalArgumentException("폐기 수량은 0보다 커야 합니다.");
        if (req.reason() == null || !WASTE_REASONS.contains(req.reason())) throw new IllegalArgumentException("폐기 사유를 선택해 주세요.");
        Material material = material(req.materialNo());
        InventoryTxn txn = newTxn(store, material, date, InventoryTxn.WASTE, -req.quantity());
        txn.setReason(req.reason());
        txn.setNote(req.note());
        txnRepository.save(txn);
        return Map.of("txnNo", txn.getTxnNo(), "amount", Math.round(req.quantity() * material.getUnitCost()));
    }

    // 폐기 기록 삭제 (마감 전만)
    public void deleteWaste(Long txnNo) {
        InventoryTxn txn = txnRepository.findById(txnNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 폐기 기록을 찾을 수 없습니다: " + txnNo));
        if (!InventoryTxn.WASTE.equals(txn.getTxnType())) throw new IllegalArgumentException("폐기 기록만 삭제할 수 있습니다.");
        guard.requireOpenDay(txn.getStore(), txn.getTxnDate());
        txnRepository.delete(txn);
    }

    // 실사 목록
    @Transactional(readOnly = true)
    public List<Map<String, Object>> stocktakes(Long storeNo) {
        guard.store(storeNo);
        return stocktakeRepository.findByStore_StoreNoOrderByCountDateDescStocktakeNoDesc(storeNo).stream().map(this::toMap).toList();
    }

    // 재고 실사 등록: 장부 재고와 센 수량의 차이만큼 재고를 맞춘다
    public Map<String, Object> createStocktake(OpsRequests.StocktakeReq req) {
        Store store = guard.store(req.storeNo());
        LocalDate date = req.date() == null ? LocalDate.now() : req.date();
        if (date.isAfter(LocalDate.now())) throw new IllegalArgumentException("미래 날짜로 실사할 수 없습니다.");
        guard.requireOpenMonth(store, date);
        if (req.lines() == null || req.lines().isEmpty()) throw new IllegalArgumentException("실사 수량을 하나 이상 입력해 주세요.");

        Map<Long, Double> current = new HashMap<>();
        jdbc.queryForList("select material_no, sum(quantity) as qty from inventory_txns where store_no = ? group by material_no", store.getStoreNo())
                .forEach(r -> current.put(((Number) r.get("material_no")).longValue(), ((Number) r.get("qty")).doubleValue()));

        Stocktake st = new Stocktake();
        st.setStore(store);
        st.setCountDate(date);
        st.setNote(req.note());
        st.setCreatedBy(CurrentUser.id());
        st.setCreatedAt(LocalDateTime.now());
        Set<Long> seen = new HashSet<>();
        for (OpsRequests.CountLine l : req.lines()) {
            if (l.materialNo() == null || l.countedQty() == null) continue;
            if (l.countedQty() < 0) throw new IllegalArgumentException("실사 수량은 0 이상이어야 합니다.");
            if (!seen.add(l.materialNo())) throw new IllegalArgumentException("같은 식자재가 두 번 들어 있습니다.");
            Material material = material(l.materialNo());
            double expected = round1(current.getOrDefault(l.materialNo(), 0.0));
            StocktakeLine line = new StocktakeLine();
            line.setStocktake(st);
            line.setMaterial(material);
            line.setExpectedQty(expected);
            line.setCountedQty(l.countedQty());
            line.setDiffQty(round1(l.countedQty() - expected));
            line.setUnitCost(material.getUnitCost());
            st.getLines().add(line);
        }
        if (st.getLines().isEmpty()) throw new IllegalArgumentException("실사 수량을 하나 이상 입력해 주세요.");
        stocktakeRepository.save(st);

        for (StocktakeLine line : st.getLines()) {
            if (line.getDiffQty() == 0) continue;
            InventoryTxn txn = newTxn(store, line.getMaterial(), date, InventoryTxn.ADJUST, line.getDiffQty());
            txn.setReason(line.getDiffQty() < 0 ? "실사 부족" : "실사 초과");
            txn.setRefType("STOCKTAKE");
            txn.setRefNo(st.getStocktakeNo());
            txnRepository.save(txn);
        }
        return toMap(st);
    }

    private Map<String, Object> toMap(Stocktake st) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stocktakeNo", st.getStocktakeNo());
        m.put("countDate", st.getCountDate());
        m.put("note", st.getNote());
        m.put("createdBy", st.getCreatedBy());
        m.put("createdAt", st.getCreatedAt());
        List<Map<String, Object>> lines = new ArrayList<>();
        double diffAmount = 0;
        int diffCount = 0;
        for (StocktakeLine l : st.getLines()) {
            double amount = l.getDiffQty() * l.getUnitCost();
            diffAmount += amount;
            if (l.getDiffQty() != 0) diffCount++;
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("materialNo", l.getMaterial().getMaterialNo());
            line.put("materialName", l.getMaterial().getMaterialName());
            line.put("unit", l.getMaterial().getMaterialUnit());
            line.put("expectedQty", l.getExpectedQty());
            line.put("countedQty", l.getCountedQty());
            line.put("diffQty", l.getDiffQty());
            line.put("diffAmount", Math.round(amount));
            lines.add(line);
        }
        m.put("lineCount", lines.size());
        m.put("diffCount", diffCount);
        m.put("diffAmount", Math.round(diffAmount));
        m.put("lines", lines);
        return m;
    }

    private InventoryTxn newTxn(Store store, Material material, LocalDate date, String type, double qty) {
        InventoryTxn txn = new InventoryTxn();
        txn.setStore(store);
        txn.setMaterial(material);
        txn.setTxnDate(date);
        txn.setTxnType(type);
        txn.setQuantity(qty);
        txn.setUnitCost(material.getUnitCost());
        txn.setCreatedAt(LocalDateTime.now());
        txn.setCreatedBy(CurrentUser.id());
        return txn;
    }

    private Material material(Long materialNo) {
        if (materialNo == null) throw new IllegalArgumentException("식자재를 선택해 주세요.");
        return materialRepository.findById(materialNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 식자재를 찾을 수 없습니다: " + materialNo));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
