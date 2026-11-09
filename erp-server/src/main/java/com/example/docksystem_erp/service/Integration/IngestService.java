package com.example.docksystem_erp.service.Integration;

import com.example.docksystem_erp.dto.Ops.OpsRequests;
import com.example.docksystem_erp.entity.Material.Material;
import com.example.docksystem_erp.entity.Menu.Menu;
import com.example.docksystem_erp.entity.PurchaseOrder.PurchaseOrder;
import com.example.docksystem_erp.entity.PurchaseOrder.PurchaseOrderLine;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Material.MaterialRepository;
import com.example.docksystem_erp.repository.Menu.MenuRepository;
import com.example.docksystem_erp.repository.PurchaseOrder.PurchaseOrderRepository;
import com.example.docksystem_erp.repository.Store.StoreRepository;
import com.example.docksystem_erp.service.Closing.ClosingService;
import com.example.docksystem_erp.service.Inventory.InventoryService;
import com.example.docksystem_erp.service.OrderRequest.OrderRequestService;
import com.example.docksystem_erp.service.PurchaseOrder.PurchaseOrderService;
import com.example.docksystem_erp.service.Sales.SalesService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * 매장 시스템(POS · 매장 앱) → 본사 데이터 수집.
 * 매장은 연동 키로 자기 매장 데이터만 보낼 수 있고, 메뉴 · 식자재는 코드로 주고받는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class IngestService {
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final MaterialRepository materialRepository;
    private final PurchaseOrderRepository poRepository;
    private final SalesService salesService;
    private final ClosingService closingService;
    private final InventoryService inventoryService;
    private final OrderRequestService orderRequestService;
    private final PurchaseOrderService poService;

    // ----- 보내는 형식 -----
    public record ReceiptIn(String receiptNo, LocalDate saleDate, LocalTime saleTime, String paymentMethod, String channel, List<ItemIn> items) {}
    public record ItemIn(String menuCode, Integer quantity) {}
    public record ClosingIn(LocalDate date, Long cashCounted, String note) {}
    public record WasteIn(LocalDate date, String materialCode, Double quantity, String reason, String note) {}
    public record StocktakeIn(LocalDate date, String note, List<CountIn> lines) {}
    public record CountIn(String materialCode, Double countedQty) {}
    public record OrderIn(LocalDate neededDate, String note, List<PackIn> lines) {}
    public record PackIn(String materialCode, Integer packs) {}
    public record DeliveryIn(String poNumber, LocalDate receivedDate, String note, List<PackIn> lines) {}

    public static class StoreKeyException extends RuntimeException {
        public StoreKeyException(String message) { super(message); }
    }

    // 연동 키로 매장 확인. 이후 기록의 작성자는 '매장코드 POS'로 남는다.
    @Transactional(readOnly = true)
    public Store store(String key) {
        if (key == null || key.isBlank()) throw new StoreKeyException("X-Store-Key 헤더에 매장 연동 키를 넣어 주세요.");
        Store store = storeRepository.findByIntegrationKey(key.trim())
                .orElseThrow(() -> new StoreKeyException("등록되지 않은 매장 연동 키입니다."));
        if ("폐점".equals(store.getStatus())) throw new StoreKeyException(store.getStoreName() + "은(는) 폐점한 매장입니다.");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(store.getStoreCode() + " POS", "", List.of()));
        return store;
    }

    // 매장이 받아 가는 기준정보 (메뉴 · 가격 · 레시피 · 식자재)
    @Transactional(readOnly = true)
    public Map<String, Object> master(Store store) {
        List<Map<String, Object>> menus = new ArrayList<>();
        for (Menu m : menuRepository.findAll()) {
            if ("단종".equals(m.getStatus())) continue;
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("menuCode", m.getMenuCode());
            x.put("menuName", m.getMenuName());
            x.put("category", m.getCategory());
            x.put("price", m.getPrice());
            x.put("status", m.getStatus());
            x.put("recipe", m.getRecipeItems().stream().map(r -> Map.of(
                    "materialCode", r.getMaterial().getMaterialCode(), "quantity", r.getQuantity(), "unit", r.getMaterial().getMaterialUnit())).toList());
            menus.add(x);
        }
        List<Map<String, Object>> materials = materialRepository.findAll().stream().map(m -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("materialCode", m.getMaterialCode());
            x.put("materialName", m.getMaterialName());
            x.put("materialType", m.getMaterialType());
            x.put("unit", m.getMaterialUnit());
            x.put("packLabel", m.getMaterialSize());
            x.put("packQty", m.getPackQty());
            x.put("packPrice", m.getMaterialPrice());
            x.put("storageType", m.getStorageType());
            return x;
        }).toList();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("storeCode", store.getStoreCode());
        r.put("storeName", store.getStoreName());
        r.put("storeType", store.getStoreType());
        r.put("menus", menus);
        r.put("materials", materials);
        return r;
    }

    // 영수증 여러 장 (이미 받은 영수증 번호는 건너뜀)
    public Map<String, Object> receipts(Store store, List<ReceiptIn> list) {
        if (list == null || list.isEmpty()) throw new IllegalArgumentException("보낸 영수증이 없습니다.");
        int saved = 0, duplicated = 0;
        for (ReceiptIn r : list) {
            List<Map.Entry<Menu, Integer>> items = new ArrayList<>();
            for (ItemIn i : r.items() == null ? List.<ItemIn>of() : r.items()) {
                items.add(Map.entry(menu(i.menuCode()), i.quantity() == null ? 0 : i.quantity()));
            }
            if (salesService.importReceipt(store, r.receiptNo(), r.saleDate(), r.saleTime(), r.paymentMethod(), r.channel(), items)) saved++;
            else duplicated++;
        }
        return Map.of("saved", saved, "duplicated", duplicated);
    }

    public Map<String, Object> closing(Store store, ClosingIn in) {
        return closingService.close(new OpsRequests.Closing(store.getStoreNo(), in.date(), in.cashCounted(), in.note()));
    }

    public Map<String, Object> waste(Store store, WasteIn in) {
        return inventoryService.addWaste(new OpsRequests.Waste(store.getStoreNo(), in.date(), material(in.materialCode()).getMaterialNo(),
                in.quantity(), in.reason(), in.note()));
    }

    public Map<String, Object> stocktake(Store store, StocktakeIn in) {
        List<OpsRequests.CountLine> lines = new ArrayList<>();
        for (CountIn c : in.lines() == null ? List.<CountIn>of() : in.lines()) {
            lines.add(new OpsRequests.CountLine(material(c.materialCode()).getMaterialNo(), c.countedQty()));
        }
        return inventoryService.createStocktake(new OpsRequests.StocktakeReq(store.getStoreNo(), in.date(), in.note(), lines));
    }

    public Map<String, Object> orderRequest(Store store, OrderIn in) {
        List<Map.Entry<Material, Integer>> lines = new ArrayList<>();
        for (PackIn p : in.lines() == null ? List.<PackIn>of() : in.lines()) lines.add(Map.entry(material(p.materialCode()), p.packs() == null ? 0 : p.packs()));
        return orderRequestService.create(store, in.neededDate(), in.note(), lines);
    }

    // 입고 확인: 매장에 도착한 발주서의 받은 수량
    public Map<String, Object> delivery(Store store, DeliveryIn in) {
        PurchaseOrder po = poRepository.findByPoNumber(in.poNumber())
                .orElseThrow(() -> new EntityNotFoundException("해당 발주서를 찾을 수 없습니다: " + in.poNumber()));
        if (!po.getStore().getStoreNo().equals(store.getStoreNo())) throw new IllegalArgumentException("다른 매장의 발주서입니다.");
        Map<String, Integer> got = new HashMap<>();
        if (in.lines() != null) in.lines().forEach(p -> got.put(p.materialCode(), p.packs()));
        List<OpsRequests.ReceiveLine> lines = new ArrayList<>();
        for (PurchaseOrderLine l : po.getLines()) {
            Integer n = got.get(l.getMaterial().getMaterialCode());
            if (n != null) lines.add(new OpsRequests.ReceiveLine(l.getLineNo(), n));
        }
        return poService.receive(po.getPoNo(), new OpsRequests.PoReceive(in.receivedDate(), in.note(), lines));
    }

    // 매장 앱 화면용: 오늘 판매 · 마감 준비 · 재고 · 입고 예정 · 최근 요청
    @Transactional(readOnly = true)
    public Map<String, Object> today(Store store) {
        LocalDate today = LocalDate.now();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("storeName", store.getStoreName());
        r.put("date", today);
        r.put("closing", closingService.preview(store.getStoreNo(), today));
        r.put("stock", inventoryService.stock(store.getStoreNo()).stream().filter(s -> ((Number) s.get("txnCount")).longValue() > 0).toList());
        r.put("openOrders", poService.open(store.getStoreNo()));
        r.put("requests", orderRequestService.list(today.minusDays(14), today, null, store.getStoreNo()));
        return r;
    }

    private Menu menu(String code) {
        return menuRepository.findByMenuCode(code).orElseThrow(() -> new EntityNotFoundException("본사에 없는 메뉴 코드입니다: " + code));
    }

    private Material material(String code) {
        return materialRepository.findByMaterialCode(code).orElseThrow(() -> new EntityNotFoundException("본사에 없는 식자재 코드입니다: " + code));
    }
}
