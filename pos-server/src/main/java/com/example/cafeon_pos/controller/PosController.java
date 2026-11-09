package com.example.cafeon_pos.controller;

import com.example.cafeon_pos.config.PosAuthFilter;
import com.example.cafeon_pos.service.PosService;
import com.example.cafeon_pos.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 매장 POS 화면이 부르는 API. 기기 등록(/auth/register) 뒤에는 POS 토큰으로 매장을 구분한다.
 * 저장하는 요청은 끝나자마자 본사 전송을 한 번 시도한다 (실패하면 대기열에 남아 자동으로 다시 보냄).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pos/v1")
public class PosController {
    private static final String STORE = PosAuthFilter.STORE_ATTR;

    private final PosService pos;
    private final SyncService sync;

    public record RegisterReq(String storeKey) {}

    public record ReplyReq(String reply) {}

    @PostMapping("/auth/register")
    public Map<String, Object> register(@RequestBody RegisterReq req) {
        return pos.register(req.storeKey());
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestAttribute(STORE) String store) {
        return pos.me(store);
    }

    @GetMapping("/master")
    public Map<String, Object> master(@RequestAttribute(STORE) String store) {
        return pos.master(store);
    }

    @GetMapping("/today")
    public Map<String, Object> today(@RequestAttribute(STORE) String store) {
        return pos.today(store);
    }

    @PostMapping("/sync")
    public Map<String, Object> syncNow(@RequestAttribute(STORE) String store) {
        sync.syncNow(store);
        return pos.me(store);
    }

    @PostMapping("/sales")
    public Map<String, Object> sale(@RequestAttribute(STORE) String store, @RequestBody PosService.SaleReq req) {
        return send(store, pos.sale(store, req));
    }

    @GetMapping("/sales")
    public List<Map<String, Object>> receipts(@RequestAttribute(STORE) String store,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return pos.receipts(store, date == null ? LocalDate.now() : date);
    }

    @PostMapping("/closings")
    public Map<String, Object> close(@RequestAttribute(STORE) String store, @RequestBody PosService.CloseReq req) {
        return send(store, pos.close(store, req));
    }

    @PostMapping("/wastes")
    public Map<String, Object> waste(@RequestAttribute(STORE) String store, @RequestBody PosService.WasteReq req) {
        return send(store, pos.waste(store, req));
    }

    @PostMapping("/stocktakes")
    public Map<String, Object> stocktake(@RequestAttribute(STORE) String store, @RequestBody PosService.StocktakeReq req) {
        return send(store, pos.stocktake(store, req));
    }

    @PostMapping("/order-requests")
    public Map<String, Object> orderRequest(@RequestAttribute(STORE) String store, @RequestBody PosService.OrderReq req) {
        return send(store, pos.orderRequest(store, req));
    }

    @PostMapping("/deliveries")
    public Map<String, Object> delivery(@RequestAttribute(STORE) String store, @RequestBody PosService.DeliveryReq req) {
        return send(store, pos.delivery(store, req));
    }

    @GetMapping("/messages")
    public List<Map<String, Object>> messages(@RequestAttribute(STORE) String store) {
        return pos.messages(store);
    }

    @PostMapping("/messages/{id}/read")
    public Map<String, Object> read(@RequestAttribute(STORE) String store, @PathVariable Long id) {
        return send(store, pos.read(store, id));
    }

    @PostMapping("/messages/{id}/reply")
    public Map<String, Object> reply(@RequestAttribute(STORE) String store, @PathVariable Long id, @RequestBody ReplyReq req) {
        return send(store, pos.reply(store, id, req.reply()));
    }

    @GetMapping("/outbox")
    public List<Map<String, Object>> outbox(@RequestAttribute(STORE) String store) {
        return pos.outbox(store);
    }

    @PostMapping("/outbox/{id}/retry")
    public Map<String, Object> retry(@RequestAttribute(STORE) String store, @PathVariable Long id) {
        return send(store, pos.retry(store, id));
    }

    @PostMapping("/outbox/{id}/skip")
    public Map<String, Object> skip(@RequestAttribute(STORE) String store, @PathVariable Long id) {
        return pos.skip(store, id);
    }

    private <T> T send(String store, T result) {
        sync.sendSoon(store);
        return result;
    }
}
