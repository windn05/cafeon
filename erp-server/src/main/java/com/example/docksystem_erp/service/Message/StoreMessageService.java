package com.example.docksystem_erp.service.Message;

import com.example.docksystem_erp.util.BizClock;

import com.example.docksystem_erp.entity.Message.StoreMessage;
import com.example.docksystem_erp.entity.Message.StoreMessageTarget;
import com.example.docksystem_erp.entity.OrderRequest.OrderRequest;
import com.example.docksystem_erp.entity.OrderRequest.OrderRequestLine;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Message.StoreMessageRepository;
import com.example.docksystem_erp.repository.Message.StoreMessageTargetRepository;
import com.example.docksystem_erp.repository.Store.StoreRepository;
import com.example.docksystem_erp.service.Sales.OpsGuard;
import com.example.docksystem_erp.util.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 본사 → 매장 POS 메시지.
 * 본사가 직접 쓰는 공지 · 지시와, 업무에서 자동으로 나가는 메뉴·가격 변경 · 발주 처리 결과가 있다.
 * 매장 POS는 수집 API(/api/ingest/v1/inbox)로 받아 가고, 확인 · 회신을 다시 보낸다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class StoreMessageService {
    private final StoreMessageRepository repository;
    private final StoreMessageTargetRepository targetRepository;
    private final StoreRepository storeRepository;
    private final ObjectMapper objectMapper;

    public record SendReq(String kind, String title, String body, Boolean important, Boolean needsReply,
                          LocalDate dueDate, List<Long> storeNos, String refKey) {}

    // ----- 본사 화면 -----

    // 본사가 직접 보내는 공지 · 지시 (storeNos가 비어 있으면 전 매장)
    public Map<String, Object> send(SendReq req) {
        String kind = req.kind() == null ? StoreMessage.NOTICE : req.kind();
        if (!StoreMessage.NOTICE.equals(kind) && !StoreMessage.TASK.equals(kind)) {
            throw new IllegalArgumentException("직접 보낼 수 있는 종류는 공지 · 지시입니다.");
        }
        if (req.title() == null || req.title().isBlank()) throw new IllegalArgumentException("제목을 적어 주세요.");
        boolean task = StoreMessage.TASK.equals(kind);
        if (task && req.dueDate() != null && req.dueDate().isBefore(BizClock.today())) {
            throw new IllegalArgumentException("회신 기한은 오늘 이후로 정해 주세요.");
        }
        List<Store> stores = req.storeNos() == null || req.storeNos().isEmpty() ? shops() : req.storeNos().stream()
                .map(no -> storeRepository.findById(no).orElseThrow(() -> new EntityNotFoundException("해당 매장을 찾을 수 없습니다: " + no)))
                .toList();
        StoreMessage m = post(kind, req.title().trim(), req.body(), null, Boolean.TRUE.equals(req.important()),
                task || Boolean.TRUE.equals(req.needsReply()), req.dueDate(), stores, req.refKey(),
                req.storeNos() == null || req.storeNos().isEmpty());
        return detail(m);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(LocalDate from, LocalDate to, String kind) {
        return repository.findBySentAtBetweenOrderBySentAtDesc(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                .filter(m -> kind == null || kind.isBlank() || kind.equals(m.getKind()))
                .map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long messageNo) {
        return detail(repository.findById(messageNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 메시지를 찾을 수 없습니다: " + messageNo)));
    }

    // 어떤 일(예: 시재 차이)로 이미 보낸 메시지
    @Transactional(readOnly = true)
    public List<Map<String, Object>> byRef(String refKey) {
        return repository.findByRefKeyOrderBySentAtDesc(refKey).stream().map(this::detail).toList();
    }

    // 회신 기한이 지났는데 답이 없는 매장 수 (홈 할 일)
    @Transactional(readOnly = true)
    public List<String> overdueReplies() {
        LocalDate today = BizClock.today();
        List<String> list = new ArrayList<>();
        for (StoreMessage m : repository.findBySentAtBetweenOrderBySentAtDesc(BizClock.now().minusDays(60), BizClock.now())) {
            if (!m.isNeedsReply() || m.getDueDate() == null || !m.getDueDate().isBefore(today)) continue;
            for (StoreMessageTarget t : m.getTargets()) {
                // 휴점 매장은 POS가 꺼져 있어 답할 수 없으니 기한 지남으로 세지 않는다
                if (t.getRepliedAt() == null && !"휴점".equals(t.getStore().getStatus())) list.add(t.getStore().getStoreName() + " · " + m.getTitle());
            }
        }
        return list;
    }

    // ----- 매장 POS (수집 API) -----

    // 받은 편지함: after 다음 번호부터. 처음 받아 가면 수신 시각을 남긴다.
    public List<Map<String, Object>> inbox(Store store, Long after) {
        LocalDateTime now = BizClock.now();
        List<Map<String, Object>> list = new ArrayList<>();
        for (StoreMessageTarget t : targetRepository.findTop100ByStore_StoreNoAndTargetNoGreaterThanOrderByTargetNoAsc(store.getStoreNo(), after == null ? 0L : after)) {
            if (t.getDeliveredAt() == null) t.setDeliveredAt(now);
            StoreMessage m = t.getMessage();
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", t.getTargetNo());
            x.put("messageNo", m.getMessageNo());
            x.put("kind", m.getKind());
            x.put("title", m.getTitle());
            x.put("body", m.getBody());
            x.put("payload", parse(m.getPayload()));
            x.put("important", m.isImportant());
            x.put("needsReply", m.isNeedsReply());
            x.put("dueDate", m.getDueDate());
            x.put("sentBy", m.getSentBy());
            x.put("sentAt", m.getSentAt());
            list.add(x);
        }
        return list;
    }

    public Map<String, Object> read(Store store, Long targetNo) {
        StoreMessageTarget t = target(store, targetNo);
        if (t.getReadAt() == null) t.setReadAt(BizClock.now());
        return Map.of("id", t.getTargetNo(), "readAt", t.getReadAt());
    }

    public Map<String, Object> reply(Store store, Long targetNo, String reply) {
        StoreMessageTarget t = target(store, targetNo);
        if (!t.getMessage().isNeedsReply()) throw new IllegalArgumentException("회신이 필요 없는 메시지입니다.");
        if (reply == null || reply.isBlank()) throw new IllegalArgumentException("회신 내용을 적어 주세요.");
        LocalDateTime now = BizClock.now();
        if (t.getReadAt() == null) t.setReadAt(now);
        t.setReply(reply.trim().length() > 500 ? reply.trim().substring(0, 500) : reply.trim());
        t.setRepliedAt(now);
        return Map.of("id", t.getTargetNo(), "repliedAt", now);
    }

    // ----- 업무에서 자동으로 나가는 메시지 -----

    // 발주 요청 승인 · 반려 결과 → 요청한 매장
    public void orderResult(OrderRequest r, List<Map<String, Object>> pos) {
        boolean approved = OrderRequest.APPROVED.equals(r.getStatus());
        List<Map<String, Object>> lines = new ArrayList<>();
        int cut = 0;
        for (OrderRequestLine l : r.getLines()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("materialCode", l.getMaterial().getMaterialCode());
            x.put("materialName", l.getMaterial().getMaterialName());
            x.put("requestedPacks", l.getRequestedPacks());
            x.put("approvedPacks", l.getApprovedPacks());
            if (approved && l.getApprovedPacks() != null && l.getApprovedPacks() < l.getRequestedPacks()) cut++;
            lines.add(x);
        }
        List<Map<String, Object>> orders = new ArrayList<>();
        for (Map<String, Object> po : pos) {
            orders.add(Map.of("poNumber", po.get("poNumber"), "clientName", po.get("clientName"), "expectedDate", String.valueOf(po.get("expectedDate"))));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestNumber", r.getRequestNumber());
        payload.put("status", r.getStatus());
        payload.put("rejectReason", r.getRejectReason());
        payload.put("lines", lines);
        payload.put("orders", orders);

        String title;
        StringBuilder body = new StringBuilder();
        if (approved) {
            title = "발주 요청 승인 · " + r.getRequestNumber();
            body.append("요청하신 발주가 승인되어 공급업체에 발주했습니다.");
            if (cut > 0) body.append(" ").append(cut).append("개 품목은 매장 재고를 보고 수량을 줄였습니다.");
            for (Map<String, Object> o : orders) {
                body.append("\n· ").append(o.get("clientName")).append(" ").append(o.get("poNumber")).append(" — ").append(o.get("expectedDate")).append(" 도착 예정");
            }
        } else {
            title = "발주 요청 반려 · " + r.getRequestNumber();
            body.append("요청이 반려되었습니다. 사유: ").append(r.getRejectReason());
        }
        post(StoreMessage.ORDER, title, body.toString(), payload, !approved, false, null, List.of(r.getStore()),
                "request:" + r.getRequestNumber(), false);
    }

    // 메뉴 · 가격 변경 → 전 매장 (POS는 받으면 메뉴를 다시 받아 감)
    public void menuChanged(String title, String body, Map<String, Object> payload, boolean important) {
        post(StoreMessage.MENU, title, body, payload, important, false, null, shops(), null, true);
    }

    // 가상 데이터: 최근 공지 · 지시 몇 건과 매장별 확인 · 회신 상태
    public int seedSamples(LocalDate today) {
        List<Store> shops = shops();
        if (shops.isEmpty()) return 0;
        LocalDateTime base = today.atTime(9, 0);
        StoreMessage n1 = post(StoreMessage.NOTICE, "11월 겨울 시즌 메뉴 운영 안내",
                "11/15부터 겨울 시즌 음료 판매를 시작합니다. 시럽·파우더 재고를 미리 확인하고, 매장 POP는 11/14 영업 종료 후 교체해 주세요.",
                null, true, false, null, shops, null, true);
        StoreMessage n2 = post(StoreMessage.NOTICE, "카드 단말기 점검 안내 (11/9 새벽)",
                "11/9(월) 02:00~04:00 카드사 전산 점검으로 카드 결제가 잠시 안 될 수 있습니다. 24시간 매장은 현금·간편결제로 안내해 주세요.",
                null, false, false, null, shops, null, true);
        StoreMessage t1 = post(StoreMessage.TASK, "겨울철 제빙기 · 온수기 위생 점검",
                "제빙기 필터와 온수기 배관 청소 후 점검 결과를 회신해 주세요. 이상이 있으면 사진과 함께 설비 담당에게 연락 바랍니다.",
                null, false, true, today.minusDays(1), shops, null, true);
        Random rnd = new Random(today.toEpochDay());
        int i = 0;
        for (StoreMessage m : List.of(n1, n2, t1)) {
            LocalDateTime sent = base.minusDays(3 - i).minusHours(i);
            m.setSentAt(sent);
            for (StoreMessageTarget t : m.getTargets()) {
                if ("휴점".equals(t.getStore().getStatus())) continue; // 휴점 매장은 POS가 꺼져 있음
                t.setDeliveredAt(sent.plusMinutes(1));
                if (rnd.nextDouble() < 0.8) t.setReadAt(sent.plusMinutes(20 + rnd.nextInt(300)));
                if (m.isNeedsReply() && t.getReadAt() != null && rnd.nextDouble() < 0.6) {
                    t.setReply("제빙기 필터 교체, 온수기 배관 청소 완료했습니다. 이상 없음.");
                    t.setRepliedAt(t.getReadAt().plusHours(2));
                }
            }
            i++;
        }
        return 3;
    }

    // ----- 내부 -----

    private StoreMessage post(String kind, String title, String body, Object payload, boolean important, boolean needsReply,
                              LocalDate due, List<Store> stores, String refKey, boolean all) {
        if (stores.isEmpty()) throw new IllegalArgumentException("받을 매장이 없습니다.");
        StoreMessage m = new StoreMessage();
        m.setKind(kind);
        m.setTitle(title.length() > 120 ? title.substring(0, 120) : title);
        m.setBody(body);
        m.setPayload(payload == null ? null : json(payload));
        m.setImportant(important);
        m.setNeedsReply(needsReply);
        m.setDueDate(needsReply ? due : null);
        m.setRefKey(refKey);
        m.setTargetLabel(all ? "전 매장" : stores.size() == 1 ? stores.get(0).getStoreName()
                : stores.get(0).getStoreName() + " 외 " + (stores.size() - 1) + "곳");
        m.setSentBy(CurrentUser.id());
        m.setSentAt(BizClock.now());
        Set<Long> seen = new HashSet<>();
        for (Store s : stores) {
            if (!seen.add(s.getStoreNo())) continue;
            StoreMessageTarget t = new StoreMessageTarget();
            t.setMessage(m);
            t.setStore(s);
            m.getTargets().add(t);
        }
        return repository.save(m);
    }

    private List<Store> shops() {
        return storeRepository.findAll().stream()
                .filter(s -> OpsGuard.SHOP_TYPES.contains(s.getStoreType()) && !"폐점".equals(s.getStatus()))
                .sorted(Comparator.comparing(Store::getStoreCode)).toList();
    }

    private StoreMessageTarget target(Store store, Long targetNo) {
        StoreMessageTarget t = targetRepository.findById(targetNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 메시지를 찾을 수 없습니다: " + targetNo));
        if (!t.getStore().getStoreNo().equals(store.getStoreNo())) throw new IllegalArgumentException("다른 매장의 메시지입니다.");
        return t;
    }

    private Map<String, Object> summary(StoreMessage m) {
        LocalDate today = BizClock.today();
        int delivered = 0, read = 0, replied = 0, overdue = 0;
        for (StoreMessageTarget t : m.getTargets()) {
            if (t.getDeliveredAt() != null) delivered++;
            if (t.getReadAt() != null) read++;
            if (t.getRepliedAt() != null) replied++;
            else if (m.isNeedsReply() && m.getDueDate() != null && m.getDueDate().isBefore(today) && !"휴점".equals(t.getStore().getStatus())) overdue++;
        }
        Map<String, Object> x = new LinkedHashMap<>();
        x.put("messageNo", m.getMessageNo());
        x.put("kind", m.getKind());
        x.put("title", m.getTitle());
        x.put("body", m.getBody());
        x.put("important", m.isImportant());
        x.put("needsReply", m.isNeedsReply());
        x.put("dueDate", m.getDueDate());
        x.put("targetLabel", m.getTargetLabel());
        x.put("refKey", m.getRefKey());
        x.put("sentBy", m.getSentBy());
        x.put("sentAt", m.getSentAt());
        x.put("targetCount", m.getTargets().size());
        x.put("deliveredCount", delivered);
        x.put("readCount", read);
        x.put("repliedCount", replied);
        x.put("overdueCount", overdue);
        return x;
    }

    private Map<String, Object> detail(StoreMessage m) {
        Map<String, Object> x = summary(m);
        x.put("payload", parse(m.getPayload()));
        List<Map<String, Object>> targets = new ArrayList<>();
        for (StoreMessageTarget t : m.getTargets()) {
            Map<String, Object> y = new LinkedHashMap<>();
            y.put("targetNo", t.getTargetNo());
            y.put("storeNo", t.getStore().getStoreNo());
            y.put("storeName", t.getStore().getStoreName());
            y.put("storeType", t.getStore().getStoreType());
            y.put("storeStatus", t.getStore().getStatus());
            y.put("deliveredAt", t.getDeliveredAt());
            y.put("readAt", t.getReadAt());
            y.put("reply", t.getReply());
            y.put("repliedAt", t.getRepliedAt());
            y.put("status", t.getRepliedAt() != null ? "회신 완료" : t.getReadAt() != null ? "확인" : t.getDeliveredAt() != null ? "수신" : "전송 대기");
            targets.add(y);
        }
        x.put("targets", targets);
        return x;
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("메시지 내용을 저장하지 못했습니다.", e);
        }
    }

    private Object parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return objectMapper.readValue(s, Object.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
