package com.example.cafeon_pos.service;

import com.example.cafeon_pos.entity.*;
import com.example.cafeon_pos.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 본사 ERP와 주고받기.
 *  · 보내기: 전송 대기열(pos_outbox)을 쌓인 순서대로 본사 수집 API에 보낸다. 본사가 꺼져 있으면 멈췄다가 다음에 이어서 보낸다.
 *  · 받기: 본사 메시지(공지 · 메뉴·가격 · 발주 결과 · 지시), 메뉴 · 식자재, 본사 기준 재고 · 입고 예정.
 * 한 매장의 보내기 · 받기는 동시에 돌지 않게 매장별로 잠근다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {
    public static final String MENU_KIND = "메뉴·가격";
    private static final int MASTER_REFRESH_MINUTES = 30;

    private final PosStoreRepository storeRepository;
    private final PosOutboxRepository outboxRepository;
    private final PosMessageRepository messageRepository;
    private final PosMenuRepository menuRepository;
    private final PosMaterialRepository materialRepository;
    private final PosReceiptRepository receiptRepository;
    private final PosClosingRepository closingRepository;
    private final HqClient hq;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pos-sync");
        t.setDaemon(true);
        return t;
    });

    // ----- 대기열에 넣기 -----

    public PosOutbox queue(String storeCode, String dataType, String refNo, String summary, String path, Object body) {
        PosOutbox o = new PosOutbox();
        o.setStoreCode(storeCode);
        o.setDataType(dataType);
        o.setRefNo(refNo);
        o.setSummary(cut(summary, 200));
        o.setPath(path);
        o.setPayload(write(body));
        o.setStatus(PosOutbox.WAITING);
        o.setCreatedAt(LocalDateTime.now());
        return outboxRepository.save(o);
    }

    // 저장 직후 바로 보내 보기 (화면은 기다리지 않음)
    public void sendSoon(String storeCode) {
        background.submit(() -> {
            try {
                flush(storeCode);
            } catch (RuntimeException e) {
                log.warn("본사 전송 실패 ({}): {}", storeCode, e.getMessage());
            }
        });
    }

    // ----- 주기 작업 -----

    @Scheduled(fixedDelayString = "${pos.sync.send-ms:5000}", initialDelay = 4000)
    public void sendAll() {
        for (PosStore s : storeRepository.findAll()) {
            if (outboxRepository.countByStoreCodeAndStatus(s.getStoreCode(), PosOutbox.WAITING) > 0) flush(s.getStoreCode());
        }
    }

    @Scheduled(fixedDelayString = "${pos.sync.pull-ms:20000}", initialDelay = 6000)
    public void pullAll() {
        for (PosStore s : storeRepository.findAll()) pull(s.getStoreCode(), false);
    }

    // 화면의 [지금 동기화]
    public void syncNow(String storeCode) {
        flush(storeCode);
        pull(storeCode, true);
    }

    // ----- 보내기 -----

    public int flush(String storeCode) {
        synchronized (lock(storeCode)) {
            PosStore store = storeRepository.findByStoreCode(storeCode).orElse(null);
            if (store == null || store.isKeyRejected()) return 0;
            int sent = 0;
            for (PosOutbox o : outboxRepository.findByStoreCodeAndStatusOrderByIdAsc(storeCode, PosOutbox.WAITING)) {
                o.setAttempts(o.getAttempts() + 1);
                try {
                    JsonNode r = hq.post(store.getIntegrationKey(), o.getPath(), o.getPayload());
                    o.setStatus(PosOutbox.DONE);
                    o.setSentAt(LocalDateTime.now());
                    o.setLastError(null);
                    o.setResponse(cut(r.toString(), 4000));
                    outboxRepository.save(o);
                    afterSend(o);
                    contact(store, null);
                    sent++;
                } catch (HqClient.Rejected e) {
                    if (e.status() == 401) {
                        // 키가 바뀌었거나 매장이 막힘: 이 건은 남겨 두고, 다시 등록하면 이어서 보낸다
                        o.setLastError(cut(e.getMessage(), 300));
                        outboxRepository.save(o);
                        store.setKeyRejected(true);
                        offline(store, e.getMessage());
                        break;
                    }
                    o.setStatus(PosOutbox.FAILED);
                    o.setLastError(cut(e.getMessage(), 300));
                    outboxRepository.save(o);
                    afterSend(o);
                    contact(store, null);
                } catch (HqClient.Unavailable e) {
                    o.setLastError(cut("본사 연결 안 됨 · " + e.getMessage(), 300));
                    outboxRepository.save(o);
                    offline(store, "본사 서버에 연결할 수 없습니다.");
                    break;
                }
            }
            storeRepository.save(store);
            return sent;
        }
    }

    // 보낸 결과를 원래 기록에도 표시 (영수증 · 마감)
    private void afterSend(PosOutbox o) {
        if ("판매".equals(o.getDataType())) {
            receiptRepository.findByReceiptNo(o.getRefNo()).ifPresent(r -> {
                r.setSendStatus(o.getStatus());
                receiptRepository.save(r);
            });
        } else if ("일마감".equals(o.getDataType())) {
            closingRepository.findByStoreCodeAndClosingDate(o.getStoreCode(), LocalDate.parse(o.getRefNo())).ifPresent(c -> {
                c.setSendStatus(o.getStatus());
                closingRepository.save(c);
            });
        }
    }

    // ----- 받기 -----

    public void pull(String storeCode, boolean forceMaster) {
        synchronized (lock(storeCode)) {
            PosStore store = storeRepository.findByStoreCode(storeCode).orElse(null);
            if (store == null || store.isKeyRejected()) return;
            try {
                boolean menuChanged = pullInbox(store);
                LocalDateTime m = store.getMasterSyncedAt();
                if (forceMaster || menuChanged || m == null || m.isBefore(LocalDateTime.now().minusMinutes(MASTER_REFRESH_MINUTES))) {
                    pullMaster(store);
                }
                store.setSnapshotJson(hq.get(store.getIntegrationKey(), "/today").toString());
                store.setSnapshotAt(LocalDateTime.now());
                contact(store, null);
            } catch (HqClient.Rejected e) {
                if (e.status() == 401) store.setKeyRejected(true);
                offline(store, e.getMessage());
            } catch (HqClient.Unavailable e) {
                offline(store, "본사 서버에 연결할 수 없습니다.");
            }
            storeRepository.save(store);
        }
    }

    // 새 메시지 저장. 메뉴·가격 변경이 있었으면 true
    private boolean pullInbox(PosStore store) {
        long cursor = store.getInboxCursor() == null ? 0 : store.getInboxCursor();
        JsonNode list = hq.get(store.getIntegrationKey(), "/inbox?after=" + cursor);
        boolean menuChanged = false;
        for (JsonNode n : list) {
            long id = n.get("id").asLong();
            cursor = Math.max(cursor, id);
            if (messageRepository.existsByStoreCodeAndHqId(store.getStoreCode(), id)) continue;
            PosMessage m = new PosMessage();
            m.setStoreCode(store.getStoreCode());
            m.setHqId(id);
            m.setKind(text(n, "kind"));
            m.setTitle(cut(text(n, "title"), 120));
            m.setBody(text(n, "body"));
            m.setPayloadJson(n.hasNonNull("payload") ? n.get("payload").toString() : null);
            m.setImportant(n.path("important").asBoolean());
            m.setNeedsReply(n.path("needsReply").asBoolean());
            m.setDueDate(n.hasNonNull("dueDate") ? LocalDate.parse(n.get("dueDate").asText()) : null);
            m.setSentBy(text(n, "sentBy"));
            m.setSentAt(n.hasNonNull("sentAt") ? LocalDateTime.parse(n.get("sentAt").asText()) : null);
            m.setReceivedAt(LocalDateTime.now());
            messageRepository.save(m);
            if (MENU_KIND.equals(m.getKind())) menuChanged = true;
        }
        store.setInboxCursor(cursor);
        store.setInboxSyncedAt(LocalDateTime.now());
        return menuChanged;
    }

    // 메뉴 · 식자재를 본사 것으로 통째로 바꾼다
    public void pullMaster(PosStore store) {
        JsonNode master = hq.get(store.getIntegrationKey(), "/master");
        tx.executeWithoutResult(s -> {
            menuRepository.deleteByStoreCode(store.getStoreCode());
            materialRepository.deleteByStoreCode(store.getStoreCode());
            menuRepository.flush();
            int order = 0;
            for (JsonNode n : master.path("menus")) {
                PosMenu m = new PosMenu();
                m.setStoreCode(store.getStoreCode());
                m.setMenuCode(text(n, "menuCode"));
                m.setMenuName(text(n, "menuName"));
                m.setCategory(text(n, "category"));
                m.setPrice(n.path("price").asLong());
                m.setStatus(text(n, "status"));
                m.setRecipeJson(n.path("recipe").toString());
                m.setSortOrder(order++);
                menuRepository.save(m);
            }
            for (JsonNode n : master.path("materials")) {
                PosMaterial m = new PosMaterial();
                m.setStoreCode(store.getStoreCode());
                m.setMaterialCode(text(n, "materialCode"));
                m.setMaterialName(text(n, "materialName"));
                m.setMaterialType(text(n, "materialType"));
                m.setUnit(text(n, "unit"));
                m.setPackLabel(text(n, "packLabel"));
                m.setPackQty(n.hasNonNull("packQty") ? n.get("packQty").asDouble() : null);
                m.setPackPrice(n.hasNonNull("packPrice") ? n.get("packPrice").asLong() : null);
                m.setStorageType(text(n, "storageType"));
                materialRepository.save(m);
            }
        });
        store.setStoreName(text(master, "storeName"));
        store.setStoreType(text(master, "storeType"));
        store.setMasterSyncedAt(LocalDateTime.now());
    }

    // ----- 상태 -----

    public Map<String, Object> status(PosStore s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("online", s.isOnline());
        m.put("keyRejected", s.isKeyRejected());
        m.put("lastContactAt", s.getLastContactAt());
        m.put("lastError", s.getLastError());
        m.put("pending", outboxRepository.countByStoreCodeAndStatus(s.getStoreCode(), PosOutbox.WAITING));
        m.put("failed", outboxRepository.countByStoreCodeAndStatus(s.getStoreCode(), PosOutbox.FAILED));
        m.put("masterSyncedAt", s.getMasterSyncedAt());
        m.put("inboxSyncedAt", s.getInboxSyncedAt());
        m.put("snapshotAt", s.getSnapshotAt());
        return m;
    }

    private void contact(PosStore s, String error) {
        s.setOnline(true);
        s.setLastContactAt(LocalDateTime.now());
        s.setLastError(error);
    }

    private void offline(PosStore s, String error) {
        s.setOnline(false);
        s.setLastError(cut(error, 300));
    }

    private Object lock(String storeCode) {
        return locks.computeIfAbsent(storeCode, k -> new Object());
    }

    private String write(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("보낼 데이터를 만들지 못했습니다.", e);
        }
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
