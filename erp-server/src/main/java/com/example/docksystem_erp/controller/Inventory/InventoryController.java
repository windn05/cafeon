package com.example.docksystem_erp.controller.Inventory;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.service.Inventory.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    // 매장 현재고
    @GetMapping("/stock")
    public List<Map<String, Object>> stock(@RequestParam Long storeNo) {
        return inventoryService.stock(storeNo);
    }

    // 재고 변동 기록 (type: 입고 · 판매소진 · 폐기 · 실사조정 · 기초재고)
    @GetMapping("/txns")
    public List<Map<String, Object>> txns(@RequestParam Long storeNo,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) Long materialNo) {
        return inventoryService.txns(storeNo, from, to, type, materialNo);
    }

    @PostMapping("/wastes")
    public Map<String, Object> addWaste(@RequestBody OpsRequests.Waste req) {
        return inventoryService.addWaste(req);
    }

    @DeleteMapping("/wastes/{txnNo}")
    public ResponseEntity<Void> deleteWaste(@PathVariable("txnNo") Long txnNo) {
        inventoryService.deleteWaste(txnNo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stocktakes")
    public List<Map<String, Object>> stocktakes(@RequestParam Long storeNo) {
        return inventoryService.stocktakes(storeNo);
    }

    @PostMapping("/stocktakes")
    public Map<String, Object> createStocktake(@RequestBody OpsRequests.StocktakeReq req) {
        return inventoryService.createCenterStocktake(req);
    }
}
