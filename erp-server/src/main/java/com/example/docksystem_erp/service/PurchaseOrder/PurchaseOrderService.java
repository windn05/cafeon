package com.example.docksystem_erp.service.PurchaseOrder;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.entity.Client.Client;
import com.example.docksystem_erp.entity.Inventory.InventoryTxn;
import com.example.docksystem_erp.entity.Material.Material;
import com.example.docksystem_erp.entity.PurchaseOrder.PurchaseOrder;
import com.example.docksystem_erp.entity.PurchaseOrder.PurchaseOrderLine;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Client.ClientRepository;
import com.example.docksystem_erp.repository.Inventory.InventoryTxnRepository;
import com.example.docksystem_erp.repository.Material.MaterialRepository;
import com.example.docksystem_erp.repository.PurchaseOrder.PurchaseOrderRepository;
import com.example.docksystem_erp.service.Inventory.InventoryService;
import com.example.docksystem_erp.service.Sales.OpsGuard;
import com.example.docksystem_erp.util.CurrentUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class PurchaseOrderService {
    // 발주 제안: 다음 발주까지 + 납품 소요일 + 안전 재고 일수만큼 확보
    public static final int SAFETY_DAYS = 2;
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final PurchaseOrderRepository poRepository;
    private final ClientRepository clientRepository;
    private final MaterialRepository materialRepository;
    private final InventoryTxnRepository txnRepository;
    private final InventoryService inventoryService;
    private final OpsGuard guard;

    public static String poNumber(String storeCode, LocalDate date, long seq) {
        return String.format("PO-%s-%s-%02d", storeCode, date.format(NO_DATE), seq);
    }

    public static int leadDays(Client client) {
        return client == null || client.getLeadTimeDays() == null ? 1 : Math.max(0, client.getLeadTimeDays());
    }

    // 발주 목록
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long storeNo, LocalDate from, LocalDate to, String status) {
        guard.store(storeNo);
        return poRepository.findByStore_StoreNoAndOrderDateBetweenOrderByOrderDateDescPoNoDesc(storeNo, from, to).stream()
                .filter(po -> status == null || status.isBlank() || status.equals(po.getStatus()))
                .map(this::toMap)
                .toList();
    }

    // 아직 입고되지 않은 발주 (기간 상관없이)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> open(Long storeNo) {
        guard.store(storeNo);
        return poRepository.findByStore_StoreNoAndStatus(storeNo, PurchaseOrder.ORDERED).stream()
                .sorted(Comparator.comparing(PurchaseOrder::getExpectedDate))
                .map(this::toMap).toList();
    }

    // 주간 발주 제안 (공급업체별로 묶음)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> suggestion(Long storeNo, int cycleDays) {
        Store store = guard.store(storeNo);
        int cycle = Math.max(1, Math.min(cycleDays, 14));
        Map<Long, Material> materials = new HashMap<>();
        materialRepository.findAll().forEach(m -> materials.put(m.getMaterialNo(), m));

        Map<String, Map<String, Object>> groups = new TreeMap<>();
        for (Map<String, Object> s : inventoryService.stock(store.getStoreNo())) {
            double avg = ((Number) s.get("avgDaily")).doubleValue();
            if (avg <= 0) continue; // 이 매장에서 쓰지 않는 식자재
            Material m = materials.get(((Number) s.get("materialNo")).longValue());
            Client client = m.getClient();
            int lead = leadDays(client);
            double stock = ((Number) s.get("quantity")).doubleValue();
            double onOrder = ((Number) s.get("onOrder")).doubleValue();
            double target = avg * (cycle + lead + SAFETY_DAYS);
            double need = target - stock - onOrder;
            int packs = need > 0 ? (int) Math.ceil(need / m.getPackQty()) : 0;

            String key = client == null ? "~미지정" : client.getClientName();
            Map<String, Object> g = groups.computeIfAbsent(key, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("clientNo", client == null ? null : client.getClientNo());
                x.put("clientName", client == null ? "공급업체 미지정" : client.getClientName());
                x.put("clientType", client == null ? null : client.getType().getLabel());
                x.put("leadTimeDays", lead);
                x.put("expectedDate", LocalDate.now().plusDays(lead));
                x.put("lines", new ArrayList<Map<String, Object>>());
                return x;
            });
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("materialNo", m.getMaterialNo());
            line.put("materialCode", m.getMaterialCode());
            line.put("materialName", m.getMaterialName());
            line.put("unit", m.getMaterialUnit());
            line.put("packLabel", m.getMaterialSize());
            line.put("packQty", m.getPackQty());
            line.put("packPrice", m.getMaterialPrice());
            line.put("stock", stock);
            line.put("onOrder", onOrder);
            line.put("avgDaily", avg);
            line.put("coverDays", s.get("coverDays"));
            line.put("targetQty", Math.round(target));
            line.put("suggestedPacks", packs);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lines = (List<Map<String, Object>>) g.get("lines");
            lines.add(line);
        }
        return new ArrayList<>(groups.values());
    }

    // 발주서 만들기
    public Map<String, Object> create(OpsRequests.PoCreate req) {
        Store store = guard.store(req.storeNo());
        if ("폐점".equals(store.getStatus())) throw new IllegalStateException("폐점한 매장은 발주할 수 없습니다.");
        if (req.clientNo() == null) throw new IllegalArgumentException("공급업체를 선택해 주세요.");
        Client client = clientRepository.findById(req.clientNo())
                .orElseThrow(() -> new EntityNotFoundException("해당 공급업체를 찾을 수 없습니다: " + req.clientNo()));
        LocalDate today = LocalDate.now();
        LocalDate expected = req.expectedDate() == null ? today.plusDays(leadDays(client)) : req.expectedDate();
        if (expected.isBefore(today)) throw new IllegalArgumentException("입고 예정일은 오늘 이후여야 합니다.");

        PurchaseOrder po = new PurchaseOrder();
        po.setStore(store);
        po.setClient(client);
        po.setOrderDate(today);
        po.setExpectedDate(expected);
        po.setStatus(PurchaseOrder.ORDERED);
        po.setNote(req.note());
        po.setOrderedBy(CurrentUser.id());
        po.setCreatedAt(LocalDateTime.now());
        po.setPoNumber(poNumber(store.getStoreCode(), today, poRepository.countByStore_StoreNoAndOrderDate(store.getStoreNo(), today) + 1));
        Set<Long> seen = new HashSet<>();
        for (OpsRequests.PoLine l : req.lines() == null ? List.<OpsRequests.PoLine>of() : req.lines()) {
            if (l.packs() == null || l.packs() <= 0) continue;
            if (!seen.add(l.materialNo())) throw new IllegalArgumentException("같은 식자재가 두 번 들어 있습니다.");
            Material m = materialRepository.findById(l.materialNo())
                    .orElseThrow(() -> new EntityNotFoundException("해당 식자재를 찾을 수 없습니다: " + l.materialNo()));
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setPurchaseOrder(po);
            line.setMaterial(m);
            line.setOrderPacks(l.packs());
            line.setPackPrice(m.getMaterialPrice());
            line.setPackQty(m.getPackQty());
            po.getLines().add(line);
        }
        if (po.getLines().isEmpty()) throw new IllegalArgumentException("발주할 식자재를 1개 이상 넣어 주세요.");
        poRepository.save(po);
        return toMap(po);
    }

    // 입고 검수: 받은 수량만큼 재고에 더한다
    public Map<String, Object> receive(Long poNo, OpsRequests.PoReceive req) {
        PurchaseOrder po = find(poNo);
        if (!PurchaseOrder.ORDERED.equals(po.getStatus())) {
            throw new IllegalStateException("이미 " + po.getStatus() + " 상태인 발주입니다.");
        }
        LocalDate date = req.receivedDate() == null ? LocalDate.now() : req.receivedDate();
        if (date.isAfter(LocalDate.now())) throw new IllegalArgumentException("미래 날짜로 입고할 수 없습니다.");
        if (date.isBefore(po.getOrderDate())) throw new IllegalArgumentException("발주일보다 이른 날짜로 입고할 수 없습니다.");
        guard.requireOpenMonth(po.getStore(), date);

        Map<Long, Integer> received = new HashMap<>();
        if (req.lines() != null) req.lines().forEach(l -> {
            if (l.receivedPacks() != null && l.receivedPacks() < 0) throw new IllegalArgumentException("받은 수량은 0 이상이어야 합니다.");
            received.put(l.lineNo(), l.receivedPacks());
        });
        boolean shortage = po.getLines().stream().anyMatch(line -> {
            Integer got = received.get(line.getLineNo());
            return got != null && got < line.getOrderPacks();
        });
        if (shortage && (req.note() == null || req.note().isBlank())) {
            throw new IllegalArgumentException("발주보다 적게 받은 품목이 있습니다. 사유를 적어 주세요.");
        }
        for (PurchaseOrderLine line : po.getLines()) {
            Integer got = received.get(line.getLineNo());
            int packs = got == null ? line.getOrderPacks() : got;
            line.setReceivedPacks(packs);
            if (packs == 0) continue;
            InventoryTxn txn = new InventoryTxn();
            txn.setStore(po.getStore());
            txn.setMaterial(line.getMaterial());
            txn.setTxnDate(date);
            txn.setTxnType(InventoryTxn.RECEIPT);
            txn.setQuantity(packs * line.getPackQty());
            txn.setUnitCost(line.getPackPrice() / line.getPackQty());
            txn.setNote(po.getPoNumber() + " · " + po.getClient().getClientName());
            txn.setRefType("PO");
            txn.setRefNo(po.getPoNo());
            txn.setCreatedAt(LocalDateTime.now());
            txn.setCreatedBy(CurrentUser.id());
            txnRepository.save(txn);
        }
        po.setStatus(PurchaseOrder.RECEIVED);
        po.setReceivedDate(date);
        po.setReceiveNote(req.note());
        po.setReceivedBy(CurrentUser.id());
        return toMap(po);
    }

    public Map<String, Object> cancel(Long poNo) {
        PurchaseOrder po = find(poNo);
        if (!PurchaseOrder.ORDERED.equals(po.getStatus())) {
            throw new IllegalStateException("입고 전 발주만 취소할 수 있습니다. (지금: " + po.getStatus() + ")");
        }
        po.setStatus(PurchaseOrder.CANCELED);
        return toMap(po);
    }

    private PurchaseOrder find(Long poNo) {
        return poRepository.findById(poNo).orElseThrow(() -> new EntityNotFoundException("해당 발주를 찾을 수 없습니다: " + poNo));
    }

    public Map<String, Object> toMap(PurchaseOrder po) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("poNo", po.getPoNo());
        m.put("poNumber", po.getPoNumber());
        m.put("storeNo", po.getStore().getStoreNo());
        m.put("storeName", po.getStore().getStoreName());
        m.put("clientNo", po.getClient().getClientNo());
        m.put("clientName", po.getClient().getClientName());
        m.put("orderDate", po.getOrderDate());
        m.put("expectedDate", po.getExpectedDate());
        m.put("receivedDate", po.getReceivedDate());
        m.put("status", po.getStatus());
        m.put("overdue", PurchaseOrder.ORDERED.equals(po.getStatus()) && po.getExpectedDate().isBefore(LocalDate.now()));
        m.put("note", po.getNote());
        m.put("receiveNote", po.getReceiveNote());
        m.put("orderedBy", po.getOrderedBy());
        m.put("receivedBy", po.getReceivedBy());
        long receivedAmount = 0;
        boolean shortage = false;
        List<Map<String, Object>> lines = new ArrayList<>();
        for (PurchaseOrderLine l : po.getLines()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("lineNo", l.getLineNo());
            x.put("materialNo", l.getMaterial().getMaterialNo());
            x.put("materialCode", l.getMaterial().getMaterialCode());
            x.put("materialName", l.getMaterial().getMaterialName());
            x.put("packLabel", l.getMaterial().getMaterialSize());
            x.put("unit", l.getMaterial().getMaterialUnit());
            x.put("packQty", l.getPackQty());
            x.put("packPrice", l.getPackPrice());
            x.put("orderPacks", l.getOrderPacks());
            x.put("receivedPacks", l.getReceivedPacks());
            x.put("amount", l.getOrderPacks() * l.getPackPrice());
            if (l.getReceivedPacks() != null) {
                receivedAmount += l.getReceivedPacks() * l.getPackPrice();
                if (l.getReceivedPacks() < l.getOrderPacks()) shortage = true;
            }
            lines.add(x);
        }
        m.put("lineCount", lines.size());
        m.put("orderAmount", po.getOrderAmount());
        m.put("receivedAmount", receivedAmount);
        m.put("shortage", shortage);
        m.put("lines", lines);
        return m;
    }
}
