package com.example.docksystem_erp.controller.Integration;

import com.example.docksystem_erp.util.BizClock;

import com.example.docksystem_erp.entity.Integration.IngestLog;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.service.Integration.IngestLogService;
import com.example.docksystem_erp.service.Integration.IngestService;
import com.example.docksystem_erp.service.Message.StoreMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 데이터 수집 API (매장 시스템 → 본사). 로그인 대신 X-Store-Key 헤더의 매장 연동 키로 확인한다.
 * 받을 때마다 수집 기록(성공 · 실패)을 남긴다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ingest/v1")
public class IngestController {
    public static final String HEADER = "X-Store-Key";

    private final IngestService ingest;
    private final IngestLogService logs;
    private final StoreMessageService messages;

    // 기준정보 받기 (메뉴 · 가격 · 레시피 · 식자재)
    @GetMapping("/master")
    public Map<String, Object> master(@RequestHeader(value = HEADER, required = false) String key) {
        return ingest.master(ingest.store(key));
    }

    // 매장 앱 화면용 오늘 현황
    @GetMapping("/today")
    public Map<String, Object> today(@RequestHeader(value = HEADER, required = false) String key) {
        return ingest.today(ingest.store(key));
    }

    // 본사 → 매장 메시지 받기 (after: 마지막으로 받은 번호)
    @GetMapping("/inbox")
    public List<Map<String, Object>> inbox(@RequestHeader(value = HEADER, required = false) String key, @RequestParam(defaultValue = "0") Long after) {
        return messages.inbox(ingest.store(key), after);
    }

    @PostMapping("/inbox/{id}/read")
    public Map<String, Object> read(@RequestHeader(value = HEADER, required = false) String key, @PathVariable Long id) {
        return messages.read(ingest.store(key), id);
    }

    public record ReplyIn(String reply) {}

    @PostMapping("/inbox/{id}/reply")
    public Map<String, Object> reply(@RequestHeader(value = HEADER, required = false) String key, @PathVariable Long id, @RequestBody ReplyIn body) {
        Store s = ingest.store(key);
        return run(s, IngestLog.REPLY, BizClock.today(), 1, () -> messages.reply(s, id, body.reply()));
    }

    @PostMapping("/sales")
    public Map<String, Object> sales(@RequestHeader(value = HEADER, required = false) String key, @RequestBody List<IngestService.ReceiptIn> body) {
        Store s = ingest.store(key);
        LocalDate day = body == null || body.isEmpty() || body.get(0).saleDate() == null ? BizClock.today() : body.get(0).saleDate();
        return run(s, IngestLog.SALES, day, body == null ? 0 : body.size(), () -> ingest.receipts(s, body));
    }

    @PostMapping("/closings")
    public Map<String, Object> closing(@RequestHeader(value = HEADER, required = false) String key, @RequestBody IngestService.ClosingIn body) {
        Store s = ingest.store(key);
        return run(s, IngestLog.CLOSING, body.date() == null ? BizClock.today() : body.date(), 1, () -> ingest.closing(s, body));
    }

    // 매장 마감 취소: 기록에는 사유를 남겨, 다시 마감할 때 재마감 이력으로 쓴다
    @PostMapping("/closings/cancel")
    public Map<String, Object> cancelClosing(@RequestHeader(value = HEADER, required = false) String key, @RequestBody IngestService.ClosingCancelIn body) {
        Store s = ingest.store(key);
        LocalDate day = body.date() == null ? BizClock.today() : body.date();
        try {
            Map<String, Object> result = ingest.cancelClosing(s, body);
            logs.log(s, IngestLog.CLOSING_CANCEL, day, 1, true, body.reason());
            return result;
        } catch (RuntimeException e) {
            logs.log(s, IngestLog.CLOSING_CANCEL, day, 1, false, e.getMessage());
            throw e;
        }
    }

    @PostMapping("/wastes")
    public Map<String, Object> waste(@RequestHeader(value = HEADER, required = false) String key, @RequestBody IngestService.WasteIn body) {
        Store s = ingest.store(key);
        return run(s, IngestLog.WASTE, body.date() == null ? BizClock.today() : body.date(), 1, () -> ingest.waste(s, body));
    }

    @PostMapping("/stocktakes")
    public Map<String, Object> stocktake(@RequestHeader(value = HEADER, required = false) String key, @RequestBody IngestService.StocktakeIn body) {
        Store s = ingest.store(key);
        return run(s, IngestLog.STOCKTAKE, body.date() == null ? BizClock.today() : body.date(),
                body.lines() == null ? 0 : body.lines().size(), () -> ingest.stocktake(s, body));
    }

    @PostMapping("/order-requests")
    public Map<String, Object> orderRequest(@RequestHeader(value = HEADER, required = false) String key, @RequestBody IngestService.OrderIn body) {
        Store s = ingest.store(key);
        return run(s, IngestLog.ORDER, BizClock.today(), body.lines() == null ? 0 : body.lines().size(), () -> ingest.orderRequest(s, body));
    }

    @PostMapping("/deliveries")
    public Map<String, Object> delivery(@RequestHeader(value = HEADER, required = false) String key, @RequestBody IngestService.DeliveryIn body) {
        Store s = ingest.store(key);
        return run(s, IngestLog.DELIVERY, body.receivedDate() == null ? BizClock.today() : body.receivedDate(),
                body.lines() == null ? 0 : body.lines().size(), () -> ingest.delivery(s, body));
    }

    private Map<String, Object> run(Store store, String type, LocalDate day, int count, Supplier<Map<String, Object>> work) {
        try {
            Map<String, Object> result = work.get();
            logs.log(store, type, day, count, true, null);
            return result;
        } catch (RuntimeException e) {
            logs.log(store, type, day, count, false, e.getMessage());
            throw e;
        }
    }
}
