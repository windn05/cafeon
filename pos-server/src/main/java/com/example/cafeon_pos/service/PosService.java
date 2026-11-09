package com.example.cafeon_pos.service;

import com.example.cafeon_pos.config.PosTokens;
import com.example.cafeon_pos.entity.*;
import com.example.cafeon_pos.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 매장 POS 업무. 판매 · 마감은 POS DB가 원본이고, 본사로 보낼 것은 모두 전송 대기열에 넣는다.
 * 폐기 · 실사 · 발주 요청 · 입고 확인은 본사 재고에 반영되는 신고라서 대기열 기록이 곧 매장 기록이다.
 */
@Service
@RequiredArgsConstructor
public class PosService {
    public static final List<String> PAYMENTS = List.of("카드", "간편결제", "현금");
    public static final List<String> CHANNELS = List.of("매장", "포장", "배달");
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final PosStoreRepository storeRepository;
    private final PosMenuRepository menuRepository;
    private final PosMaterialRepository materialRepository;
    private final PosReceiptRepository receiptRepository;
    private final PosClosingRepository closingRepository;
    private final PosOutboxRepository outboxRepository;
    private final PosMessageRepository messageRepository;
    private final SyncService sync;
    private final HqClient hq;
    private final PosTokens tokens;
    private final ObjectMapper json;

    @Value("${pos.cash-float:200000}")
    private long cashFloat;

    // 이 POS에 등록 정보가 없음 (DB 초기화 등) → 화면이 기기 등록으로 돌아가게 401
    public static class NotRegistered extends RuntimeException {
        public NotRegistered(String message) { super(message); }
    }

    // ----- 요청 형식 -----
    public record ItemReq(String menuCode, Integer quantity) {}
    public record SaleReq(String paymentMethod, String channel, List<ItemReq> items) {}
    public record CloseReq(Long cashCounted, String note) {}
    public record WasteReq(String materialCode, Double quantity, String reason, String note) {}
    public record CountReq(String materialCode, Double countedQty) {}
    public record StocktakeReq(String note, List<CountReq> lines) {}
    public record PackReq(String materialCode, Integer packs) {}
    public record OrderReq(LocalDate neededDate, String note, List<PackReq> lines) {}
    public record DeliveryReq(String poNumber, String note, List<PackReq> lines) {}

    // ----- 기기 등록 -----

    // 본사가 발급한 매장 연동 키로 이 POS를 매장에 연결한다 (키가 바뀌었으면 새 키로 바꿈)
    public Map<String, Object> register(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("매장 연동 키를 넣어 주세요.");
        String k = key.trim();
        JsonNode master = hq.get(k, "/master"); // 본사가 키를 확인해 준다
        String code = master.path("storeCode").asText();
        PosStore store = storeRepository.findByStoreCode(code).orElseGet(PosStore::new);
        boolean isNew = store.getId() == null;
        store.setStoreCode(code);
        store.setStoreName(master.path("storeName").asText());
        store.setStoreType(master.path("storeType").asText());
        store.setIntegrationKey(k);
        store.setKeyRejected(false);
        store.setOnline(true);
        store.setLastContactAt(LocalDateTime.now());
        store.setLastError(null);
        if (isNew) store.setRegisteredAt(LocalDateTime.now());
        storeRepository.save(store);
        sync.syncNow(code); // 메뉴 · 메시지 · 재고 받기 + 쌓인 것 보내기
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("token", tokens.issue(code));
        r.put("store", me(code));
        return r;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> me(String storeCode) {
        PosStore s = store(storeCode);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("storeCode", s.getStoreCode());
        m.put("storeName", s.getStoreName());
        m.put("storeType", s.getStoreType());
        m.put("registeredAt", s.getRegisteredAt());
        m.put("sync", sync.status(s));
        return m;
    }

    // ----- 메뉴 · 식자재 -----

    @Transactional(readOnly = true)
    public Map<String, Object> master(String storeCode) {
        List<Map<String, Object>> menus = menuRepository.findByStoreCodeOrderBySortOrderAsc(storeCode).stream().map(m -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("menuCode", m.getMenuCode());
            x.put("menuName", m.getMenuName());
            x.put("category", m.getCategory());
            x.put("price", m.getPrice());
            x.put("status", m.getStatus());
            return x;
        }).toList();
        List<Map<String, Object>> materials = materialRepository.findByStoreCodeOrderByMaterialCodeAsc(storeCode).stream().map(m -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("materialCode", m.getMaterialCode());
            x.put("materialName", m.getMaterialName());
            x.put("materialType", m.getMaterialType());
            x.put("unit", m.getUnit());
            x.put("packLabel", m.getPackLabel());
            x.put("packQty", m.getPackQty());
            x.put("packPrice", m.getPackPrice());
            x.put("storageType", m.getStorageType());
            return x;
        }).toList();
        return Map.of("menus", menus, "materials", materials);
    }

    // ----- 오늘 현황 -----

    @Transactional(readOnly = true)
    public Map<String, Object> today(String storeCode) {
        PosStore s = store(storeCode);
        LocalDate today = LocalDate.now();
        List<PosReceipt> receipts = receiptRepository.findByStoreCodeAndSaleDateOrderByIdDesc(storeCode, today);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", today);
        m.put("sales", totals(receipts));
        m.put("cashFloat", cashFloat);
        m.put("closing", closingRepository.findByStoreCodeAndClosingDate(storeCode, today).map(this::closingMap).orElse(null));
        m.put("recentReceipts", receipts.stream().limit(30).map(this::receiptMap).toList());

        // 본사 기준 재고 · 입고 예정 · 발주 요청 (마지막으로 받은 것)
        JsonNode snap = parse(s.getSnapshotJson());
        m.put("snapshotAt", s.getSnapshotAt());
        m.put("stock", snap == null ? List.of() : snap.path("stock"));
        m.put("requests", snap == null ? List.of() : snap.path("requests"));
        // 입고 확인을 이미 보낸(또는 보내는 중인) 발주서는 빼고 보여 준다
        Set<String> confirmed = new HashSet<>();
        List<Map<String, Object>> queuedRequests = new ArrayList<>();
        for (PosOutbox o : outboxRepository.findTop100ByStoreCodeOrderByIdDesc(storeCode)) {
            if ("입고".equals(o.getDataType()) && !PosOutbox.SKIPPED.equals(o.getStatus()) && !PosOutbox.FAILED.equals(o.getStatus())) confirmed.add(o.getRefNo());
            if ("발주요청".equals(o.getDataType()) && (PosOutbox.WAITING.equals(o.getStatus()) || PosOutbox.FAILED.equals(o.getStatus()))) {
                queuedRequests.add(Map.of("id", o.getId(), "summary", o.getSummary(), "status", o.getStatus(), "createdAt", o.getCreatedAt(),
                        "error", o.getLastError() == null ? "" : o.getLastError()));
            }
        }
        List<JsonNode> open = new ArrayList<>();
        if (snap != null) snap.path("openOrders").forEach(po -> {
            if (!confirmed.contains(po.path("poNumber").asText())) open.add(po);
        });
        m.put("openOrders", open);
        m.put("queuedRequests", queuedRequests);
        m.put("unreadMessages", messageRepository.countByStoreCodeAndReadAtIsNull(storeCode));
        m.put("sync", sync.status(s));
        return m;
    }

    // ----- 판매 -----

    @Transactional
    public synchronized Map<String, Object> sale(String storeCode, SaleReq req) {
        store(storeCode);
        LocalDate today = LocalDate.now();
        requireOpen(storeCode, today);
        if (!PAYMENTS.contains(req.paymentMethod())) throw new IllegalArgumentException("결제 수단을 골라 주세요.");
        if (!CHANNELS.contains(req.channel())) throw new IllegalArgumentException("판매 채널을 골라 주세요.");
        if (req.items() == null || req.items().isEmpty()) throw new IllegalArgumentException("메뉴를 담아 주세요.");

        PosReceipt r = new PosReceipt();
        r.setStoreCode(storeCode);
        r.setSaleDate(today);
        r.setSaleTime(LocalTime.now().withNano(0));
        r.setPaymentMethod(req.paymentMethod());
        r.setChannel(req.channel());
        r.setReceiptNo(String.format("%s-%s-%04d", storeCode, today.format(NO_DATE), receiptRepository.countByStoreCodeAndSaleDate(storeCode, today) + 1));
        long total = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemReq i : req.items()) {
            int qty = i.quantity() == null ? 0 : i.quantity();
            if (qty <= 0) continue;
            PosMenu menu = menuRepository.findByStoreCodeAndMenuCode(storeCode, i.menuCode())
                    .orElseThrow(() -> new IllegalArgumentException("POS에 없는 메뉴입니다: " + i.menuCode() + " (메뉴를 다시 받아 주세요)"));
            if (!"판매중".equals(menu.getStatus())) throw new IllegalStateException(menu.getMenuName() + "은(는) 지금 " + menu.getStatus() + " 상태입니다.");
            PosReceiptLine l = new PosReceiptLine();
            l.setReceipt(r);
            l.setMenuCode(menu.getMenuCode());
            l.setMenuName(menu.getMenuName());
            l.setUnitPrice(menu.getPrice());
            l.setQuantity(qty);
            l.setAmount(menu.getPrice() * qty);
            r.getLines().add(l);
            total += l.getAmount();
            items.add(Map.of("menuCode", menu.getMenuCode(), "quantity", qty));
        }
        if (r.getLines().isEmpty()) throw new IllegalArgumentException("메뉴를 담아 주세요.");
        r.setTotalAmount(total);
        r.setSendStatus(PosOutbox.WAITING);
        receiptRepository.save(r);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiptNo", r.getReceiptNo());
        body.put("saleDate", today);
        body.put("saleTime", r.getSaleTime());
        body.put("paymentMethod", r.getPaymentMethod());
        body.put("channel", r.getChannel());
        body.put("items", items);
        String first = r.getLines().get(0).getMenuName() + (r.getLines().size() > 1 ? " 외 " + (r.getLines().size() - 1) + "개" : "");
        sync.queue(storeCode, "판매", r.getReceiptNo(), first + " · " + won(total) + " · " + r.getPaymentMethod(), "/sales", List.of(body));
        return receiptMap(r);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> receipts(String storeCode, LocalDate date) {
        return receiptRepository.findByStoreCodeAndSaleDateOrderByIdDesc(storeCode, date).stream().map(this::receiptMap).toList();
    }

    // ----- 일 마감 -----

    @Transactional
    public Map<String, Object> close(String storeCode, CloseReq req) {
        store(storeCode);
        LocalDate today = LocalDate.now();
        requireOpen(storeCode, today);
        if (req.cashCounted() == null || req.cashCounted() < 0) throw new IllegalArgumentException("금고에서 센 현금을 넣어 주세요.");
        List<PosReceipt> receipts = receiptRepository.findByStoreCodeAndSaleDateOrderByIdDesc(storeCode, today);
        Map<String, Object> t = totals(receipts);
        long cash = (Long) t.get("cashAmount");
        long expected = cashFloat + cash;
        long diff = req.cashCounted() - expected;
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        if (diff != 0 && note == null) throw new IllegalArgumentException("시재 차이가 있으면 사유를 적어 주세요.");

        PosClosing c = new PosClosing();
        c.setStoreCode(storeCode);
        c.setClosingDate(today);
        c.setTotalAmount((Long) t.get("amount"));
        c.setReceipts(((Long) t.get("receipts")).intValue());
        c.setCardAmount((Long) t.get("cardAmount"));
        c.setEasyPayAmount((Long) t.get("easyPayAmount"));
        c.setCashAmount(cash);
        c.setCashFloat(cashFloat);
        c.setExpectedCash(expected);
        c.setCashCounted(req.cashCounted());
        c.setCashDiff(diff);
        c.setNote(note);
        c.setClosedAt(LocalDateTime.now());
        c.setSendStatus(PosOutbox.WAITING);
        closingRepository.save(c);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", today);
        body.put("cashCounted", req.cashCounted());
        body.put("note", note);
        sync.queue(storeCode, "일마감", today.toString(), today.format(DateTimeFormatter.ofPattern("M/d")) + " 마감 · 매출 " + won(c.getTotalAmount())
                + (diff == 0 ? " · 시재 일치" : " · 시재 " + (diff > 0 ? "+" : "") + won(diff)), "/closings", body);
        return closingMap(c);
    }

    // ----- 폐기 · 실사 · 발주 요청 · 입고 확인 -----

    @Transactional
    public Map<String, Object> waste(String storeCode, WasteReq req) {
        PosMaterial m = material(storeCode, req.materialCode());
        if (req.quantity() == null || req.quantity() <= 0) throw new IllegalArgumentException("폐기 수량은 0보다 커야 합니다.");
        if (req.reason() == null || req.reason().isBlank()) throw new IllegalArgumentException("폐기 사유를 골라 주세요.");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", LocalDate.now());
        body.put("materialCode", m.getMaterialCode());
        body.put("quantity", req.quantity());
        body.put("reason", req.reason());
        body.put("note", blank(req.note()));
        PosOutbox o = sync.queue(storeCode, "폐기", m.getMaterialCode(), m.getMaterialName() + " " + num(req.quantity()) + m.getUnit() + " · " + req.reason(), "/wastes", body);
        return outboxMap(o);
    }

    @Transactional
    public Map<String, Object> stocktake(String storeCode, StocktakeReq req) {
        List<Map<String, Object>> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CountReq c : req.lines() == null ? List.<CountReq>of() : req.lines()) {
            if (c.countedQty() == null) continue;
            if (c.countedQty() < 0) throw new IllegalArgumentException("센 수량은 0 이상이어야 합니다.");
            material(storeCode, c.materialCode());
            if (!seen.add(c.materialCode())) throw new IllegalArgumentException("같은 식자재가 두 번 들어 있습니다.");
            lines.add(Map.of("materialCode", c.materialCode(), "countedQty", c.countedQty()));
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("센 수량을 1개 품목 이상 넣어 주세요.");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", LocalDate.now());
        body.put("note", blank(req.note()) == null ? "매장 실사" : req.note().trim());
        body.put("lines", lines);
        PosOutbox o = sync.queue(storeCode, "실사", LocalDate.now().toString(), "재고 실사 " + lines.size() + "개 품목", "/stocktakes", body);
        return outboxMap(o);
    }

    @Transactional
    public Map<String, Object> orderRequest(String storeCode, OrderReq req) {
        List<Map<String, Object>> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String first = null;
        for (PackReq p : req.lines() == null ? List.<PackReq>of() : req.lines()) {
            if (p.packs() == null || p.packs() <= 0) continue;
            PosMaterial m = material(storeCode, p.materialCode());
            if (!seen.add(p.materialCode())) throw new IllegalArgumentException("같은 식자재가 두 번 들어 있습니다: " + m.getMaterialName());
            if (first == null) first = m.getMaterialName() + " " + p.packs() + "개";
            lines.add(Map.of("materialCode", p.materialCode(), "packs", p.packs()));
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("요청할 식자재를 1개 이상 넣어 주세요.");
        if (req.neededDate() != null && req.neededDate().isBefore(LocalDate.now())) throw new IllegalArgumentException("필요한 날짜는 오늘 이후로 골라 주세요.");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("neededDate", req.neededDate());
        body.put("note", blank(req.note()));
        body.put("lines", lines);
        PosOutbox o = sync.queue(storeCode, "발주요청", null, first + (lines.size() > 1 ? " 외 " + (lines.size() - 1) + "품목" : ""), "/order-requests", body);
        return outboxMap(o);
    }

    @Transactional
    public Map<String, Object> delivery(String storeCode, DeliveryReq req) {
        if (req.poNumber() == null || req.poNumber().isBlank()) throw new IllegalArgumentException("발주서 번호가 없습니다.");
        if (outboxRepository.existsByStoreCodeAndDataTypeAndRefNoAndStatusIn(storeCode, "입고", req.poNumber(), List.of(PosOutbox.WAITING, PosOutbox.DONE))) {
            throw new IllegalStateException("이미 입고 확인을 보낸 발주서입니다: " + req.poNumber());
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        int total = 0;
        for (PackReq p : req.lines() == null ? List.<PackReq>of() : req.lines()) {
            int n = p.packs() == null ? 0 : p.packs();
            if (n < 0) throw new IllegalArgumentException("받은 수량은 0 이상이어야 합니다.");
            lines.add(Map.of("materialCode", p.materialCode(), "packs", n));
            total += n;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("poNumber", req.poNumber());
        body.put("receivedDate", LocalDate.now());
        body.put("note", blank(req.note()));
        body.put("lines", lines);
        PosOutbox o = sync.queue(storeCode, "입고", req.poNumber(), req.poNumber() + " 입고 확인 · " + total + "개", "/deliveries", body);
        return outboxMap(o);
    }

    // ----- 본사 메시지 -----

    @Transactional(readOnly = true)
    public List<Map<String, Object>> messages(String storeCode) {
        return messageRepository.findTop200ByStoreCodeOrderByHqIdDesc(storeCode).stream().map(this::messageMap).toList();
    }

    @Transactional
    public Map<String, Object> read(String storeCode, Long id) {
        PosMessage m = message(storeCode, id);
        if (m.getReadAt() == null) {
            m.setReadAt(LocalDateTime.now());
            messageRepository.save(m);
            sync.queue(storeCode, "공지확인", String.valueOf(m.getHqId()), "확인 · " + m.getTitle(), "/inbox/" + m.getHqId() + "/read", Map.of());
        }
        return messageMap(m);
    }

    @Transactional
    public Map<String, Object> reply(String storeCode, Long id, String reply) {
        PosMessage m = message(storeCode, id);
        if (!m.isNeedsReply()) throw new IllegalArgumentException("회신이 필요 없는 메시지입니다.");
        if (m.getRepliedAt() != null) throw new IllegalStateException("이미 회신했습니다.");
        if (reply == null || reply.isBlank()) throw new IllegalArgumentException("회신 내용을 적어 주세요.");
        LocalDateTime now = LocalDateTime.now();
        if (m.getReadAt() == null) m.setReadAt(now);
        m.setReply(SyncService.cut(reply.trim(), 500));
        m.setRepliedAt(now);
        messageRepository.save(m);
        sync.queue(storeCode, "지시회신", String.valueOf(m.getHqId()), "회신 · " + m.getTitle(), "/inbox/" + m.getHqId() + "/reply", Map.of("reply", m.getReply()));
        return messageMap(m);
    }

    // ----- 전송 기록 -----

    @Transactional(readOnly = true)
    public List<Map<String, Object>> outbox(String storeCode) {
        return outboxRepository.findTop100ByStoreCodeOrderByIdDesc(storeCode).stream().map(this::outboxMap).toList();
    }

    // 실패한 건을 고쳐서 (또는 본사 쪽을 정리한 뒤) 다시 보내기
    @Transactional
    public Map<String, Object> retry(String storeCode, Long id) {
        PosOutbox o = outbox(storeCode, id);
        if (!PosOutbox.FAILED.equals(o.getStatus())) throw new IllegalStateException("실패한 건만 다시 보낼 수 있습니다.");
        o.setStatus(PosOutbox.WAITING);
        o.setLastError(null);
        return outboxMap(outboxRepository.save(o));
    }

    // 실패한 건을 보내지 않기로 함
    @Transactional
    public Map<String, Object> skip(String storeCode, Long id) {
        PosOutbox o = outbox(storeCode, id);
        if (!PosOutbox.FAILED.equals(o.getStatus())) throw new IllegalStateException("실패한 건만 제외할 수 있습니다.");
        o.setStatus(PosOutbox.SKIPPED);
        return outboxMap(outboxRepository.save(o));
    }

    // ----- 내부 -----

    private PosStore store(String storeCode) {
        return storeRepository.findByStoreCode(storeCode).orElseThrow(() -> new NotRegistered("이 POS에 등록되지 않은 매장입니다. 다시 등록해 주세요."));
    }

    private PosMaterial material(String storeCode, String code) {
        return materialRepository.findByStoreCodeAndMaterialCode(storeCode, code)
                .orElseThrow(() -> new IllegalArgumentException("POS에 없는 식자재입니다: " + code));
    }

    private PosMessage message(String storeCode, Long id) {
        PosMessage m = messageRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("메시지를 찾을 수 없습니다."));
        if (!m.getStoreCode().equals(storeCode)) throw new EntityNotFoundException("메시지를 찾을 수 없습니다.");
        return m;
    }

    private PosOutbox outbox(String storeCode, Long id) {
        PosOutbox o = outboxRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("전송 기록을 찾을 수 없습니다."));
        if (!o.getStoreCode().equals(storeCode)) throw new EntityNotFoundException("전송 기록을 찾을 수 없습니다.");
        return o;
    }

    private void requireOpen(String storeCode, LocalDate day) {
        if (closingRepository.findByStoreCodeAndClosingDate(storeCode, day).isPresent()) {
            throw new IllegalStateException("오늘은 이미 마감했습니다. 판매 · 마감을 더 할 수 없습니다.");
        }
    }

    private Map<String, Object> totals(List<PosReceipt> receipts) {
        long amount = 0, card = 0, easy = 0, cash = 0, dineIn = 0, takeout = 0, delivery = 0;
        for (PosReceipt r : receipts) {
            amount += r.getTotalAmount();
            switch (r.getPaymentMethod()) {
                case "카드" -> card += r.getTotalAmount();
                case "간편결제" -> easy += r.getTotalAmount();
                default -> cash += r.getTotalAmount();
            }
            switch (r.getChannel()) {
                case "포장" -> takeout += r.getTotalAmount();
                case "배달" -> delivery += r.getTotalAmount();
                default -> dineIn += r.getTotalAmount();
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", amount);
        m.put("receipts", (long) receipts.size());
        m.put("avgTicket", receipts.isEmpty() ? 0 : Math.round((double) amount / receipts.size()));
        m.put("cardAmount", card);
        m.put("easyPayAmount", easy);
        m.put("cashAmount", cash);
        m.put("dineInAmount", dineIn);
        m.put("takeoutAmount", takeout);
        m.put("deliveryAmount", delivery);
        m.put("expectedCash", cashFloat + cash);
        return m;
    }

    private Map<String, Object> receiptMap(PosReceipt r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("receiptNo", r.getReceiptNo());
        m.put("saleDate", r.getSaleDate());
        m.put("saleTime", r.getSaleTime());
        m.put("paymentMethod", r.getPaymentMethod());
        m.put("channel", r.getChannel());
        m.put("totalAmount", r.getTotalAmount());
        m.put("sendStatus", r.getSendStatus());
        m.put("lines", r.getLines().stream().map(l -> Map.of("menuName", l.getMenuName(), "quantity", l.getQuantity(), "amount", l.getAmount())).toList());
        return m;
    }

    private Map<String, Object> closingMap(PosClosing c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("closingDate", c.getClosingDate());
        m.put("totalAmount", c.getTotalAmount());
        m.put("receipts", c.getReceipts());
        m.put("cashAmount", c.getCashAmount());
        m.put("expectedCash", c.getExpectedCash());
        m.put("cashCounted", c.getCashCounted());
        m.put("cashDiff", c.getCashDiff());
        m.put("note", c.getNote());
        m.put("closedAt", c.getClosedAt());
        m.put("sendStatus", c.getSendStatus());
        return m;
    }

    private Map<String, Object> outboxMap(PosOutbox o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("dataType", o.getDataType());
        m.put("refNo", o.getRefNo());
        m.put("summary", o.getSummary());
        m.put("status", o.getStatus());
        m.put("attempts", o.getAttempts());
        m.put("lastError", o.getLastError());
        m.put("createdAt", o.getCreatedAt());
        m.put("sentAt", o.getSentAt());
        return m;
    }

    private Map<String, Object> messageMap(PosMessage x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", x.getId());
        m.put("hqId", x.getHqId());
        m.put("kind", x.getKind());
        m.put("title", x.getTitle());
        m.put("body", x.getBody());
        m.put("payload", parse(x.getPayloadJson()));
        m.put("important", x.isImportant());
        m.put("needsReply", x.isNeedsReply());
        m.put("dueDate", x.getDueDate());
        m.put("sentBy", x.getSentBy());
        m.put("sentAt", x.getSentAt());
        m.put("receivedAt", x.getReceivedAt());
        m.put("readAt", x.getReadAt());
        m.put("reply", x.getReply());
        m.put("repliedAt", x.getRepliedAt());
        return m;
    }

    private JsonNode parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return json.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String won(long v) {
        return String.format("%,d원", v);
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.format("%,d", (long) v) : String.valueOf(v);
    }
}
