package com.example.docksystem_erp.service.OrderRequest;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.entity.Client.Client;
import com.example.docksystem_erp.entity.Material.Material;
import com.example.docksystem_erp.entity.OrderRequest.OrderRequest;
import com.example.docksystem_erp.entity.OrderRequest.OrderRequestLine;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Material.MaterialRepository;
import com.example.docksystem_erp.repository.OrderRequest.OrderRequestRepository;
import com.example.docksystem_erp.service.Message.StoreMessageService;
import com.example.docksystem_erp.service.PurchaseOrder.PurchaseOrderService;
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
public class OrderRequestService {
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final OrderRequestRepository repository;
    private final MaterialRepository materialRepository;
    private final PurchaseOrderService poService;
    private final StoreMessageService messageService;

    public static String requestNumber(String storeCode, LocalDate date, long seq) {
        return String.format("RQ-%s-%s-%02d", storeCode, date.format(NO_DATE), seq);
    }

    // 본사: 기간 안 요청 목록 (status가 있으면 그 상태만)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(LocalDate from, LocalDate to, String status, Long storeNo) {
        return repository.findByRequestDateBetweenOrderByRequestDateDescRequestNoDesc(from, to).stream()
                .filter(r -> status == null || status.isBlank() || status.equals(r.getStatus()))
                .filter(r -> storeNo == null || r.getStore().getStoreNo().equals(storeNo))
                .map(this::toMap).toList();
    }

    // 본사: 처리 대기 중인 요청 (기간 상관없이)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> pending() {
        return repository.findByStatusOrderByRequestDateAscRequestNoAsc(OrderRequest.REQUESTED).stream().map(this::toMap).toList();
    }

    // 매장: 발주 요청 올리기 (수집 API에서 호출)
    public Map<String, Object> create(Store store, LocalDate neededDate, String note, List<Map.Entry<Material, Integer>> lines) {
        if (lines.isEmpty()) throw new IllegalArgumentException("요청할 식자재를 1개 이상 넣어 주세요.");
        LocalDate today = LocalDate.now();
        OrderRequest r = new OrderRequest();
        r.setStore(store);
        r.setRequestDate(today);
        r.setNeededDate(neededDate);
        r.setStatus(OrderRequest.REQUESTED);
        r.setNote(note);
        r.setCreatedAt(LocalDateTime.now());
        r.setRequestNumber(requestNumber(store.getStoreCode(), today, repository.countByStore_StoreNoAndRequestDate(store.getStoreNo(), today) + 1));
        Set<Long> seen = new HashSet<>();
        for (var e : lines) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            if (!seen.add(e.getKey().getMaterialNo())) throw new IllegalArgumentException("같은 식자재가 두 번 들어 있습니다: " + e.getKey().getMaterialName());
            OrderRequestLine l = new OrderRequestLine();
            l.setRequest(r);
            l.setMaterial(e.getKey());
            l.setRequestedPacks(e.getValue());
            r.getLines().add(l);
        }
        if (r.getLines().isEmpty()) throw new IllegalArgumentException("요청할 식자재를 1개 이상 넣어 주세요.");
        repository.save(r);
        return toMap(r);
    }

    // 본사: 승인 (수량을 고칠 수 있음) → 공급업체별 발주서 생성
    public Map<String, Object> approve(Long requestNo, List<OpsRequests.ReceiveLine> edits) {
        OrderRequest r = find(requestNo);
        if (!OrderRequest.REQUESTED.equals(r.getStatus())) throw new IllegalStateException("이미 " + r.getStatus() + " 처리한 요청입니다.");
        Map<Long, Integer> packs = new HashMap<>();
        if (edits != null) edits.forEach(e -> packs.put(e.lineNo(), e.receivedPacks()));

        Map<Client, List<OpsRequests.PoLine>> bySupplier = new LinkedHashMap<>();
        for (OrderRequestLine l : r.getLines()) {
            int n = packs.getOrDefault(l.getLineNo(), l.getRequestedPacks());
            if (n < 0) throw new IllegalArgumentException("승인 수량은 0 이상이어야 합니다.");
            l.setApprovedPacks(n);
            if (n == 0) continue;
            Client c = l.getMaterial().getClient();
            if (c == null) throw new IllegalStateException(l.getMaterial().getMaterialName() + "에 주 공급업체가 없습니다. 식자재 관리에서 지정해 주세요.");
            bySupplier.computeIfAbsent(c, k -> new ArrayList<>()).add(new OpsRequests.PoLine(l.getMaterial().getMaterialNo(), n));
        }
        if (bySupplier.isEmpty()) throw new IllegalArgumentException("승인할 수량이 없습니다. 전부 0이면 반려하세요.");
        List<String> poNumbers = new ArrayList<>();
        List<Map<String, Object>> pos = new ArrayList<>();
        for (var e : bySupplier.entrySet()) {
            Map<String, Object> po = poService.create(new OpsRequests.PoCreate(r.getStore().getStoreNo(), e.getKey().getClientNo(), null,
                    "매장 발주 요청 " + r.getRequestNumber(), e.getValue()));
            poNumbers.add((String) po.get("poNumber"));
            pos.add(po);
        }
        r.setStatus(OrderRequest.APPROVED);
        r.setDecidedBy(CurrentUser.id());
        r.setDecidedAt(LocalDateTime.now());
        r.setPoNumbers(String.join(", ", poNumbers));
        messageService.orderResult(r, pos); // 매장 POS에 승인 결과 · 도착 예정일 알림
        return toMap(r);
    }

    // 본사: 반려
    public Map<String, Object> reject(Long requestNo, String reason) {
        OrderRequest r = find(requestNo);
        if (!OrderRequest.REQUESTED.equals(r.getStatus())) throw new IllegalStateException("이미 " + r.getStatus() + " 처리한 요청입니다.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("반려 사유를 적어 주세요.");
        r.setStatus(OrderRequest.REJECTED);
        r.setRejectReason(reason);
        r.setDecidedBy(CurrentUser.id());
        r.setDecidedAt(LocalDateTime.now());
        messageService.orderResult(r, List.of()); // 매장 POS에 반려 사유 알림
        return toMap(r);
    }

    private OrderRequest find(Long no) {
        return repository.findById(no).orElseThrow(() -> new EntityNotFoundException("해당 발주 요청을 찾을 수 없습니다: " + no));
    }

    public Map<String, Object> toMap(OrderRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestNo", r.getRequestNo());
        m.put("requestNumber", r.getRequestNumber());
        m.put("storeNo", r.getStore().getStoreNo());
        m.put("storeName", r.getStore().getStoreName());
        m.put("storeType", r.getStore().getStoreType());
        m.put("requestDate", r.getRequestDate());
        m.put("neededDate", r.getNeededDate());
        m.put("status", r.getStatus());
        m.put("note", r.getNote());
        m.put("decidedBy", r.getDecidedBy());
        m.put("decidedAt", r.getDecidedAt());
        m.put("rejectReason", r.getRejectReason());
        m.put("poNumbers", r.getPoNumbers());
        long amount = 0;
        List<Map<String, Object>> lines = new ArrayList<>();
        for (OrderRequestLine l : r.getLines()) {
            Material mat = l.getMaterial();
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("lineNo", l.getLineNo());
            x.put("materialNo", mat.getMaterialNo());
            x.put("materialCode", mat.getMaterialCode());
            x.put("materialName", mat.getMaterialName());
            x.put("packLabel", mat.getMaterialSize());
            x.put("packPrice", mat.getMaterialPrice());
            x.put("clientName", mat.getClient() == null ? null : mat.getClient().getClientName());
            x.put("requestedPacks", l.getRequestedPacks());
            x.put("approvedPacks", l.getApprovedPacks());
            amount += (long) (l.getApprovedPacks() != null ? l.getApprovedPacks() : l.getRequestedPacks()) * mat.getMaterialPrice();
            lines.add(x);
        }
        m.put("lineCount", lines.size());
        m.put("amount", amount);
        m.put("lines", lines);
        return m;
    }
}
