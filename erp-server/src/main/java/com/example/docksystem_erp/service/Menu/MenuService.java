package com.example.docksystem_erp.service.Menu;

import com.example.docksystem_erp.dto.Menu.MenuRequestDto;
import com.example.docksystem_erp.dto.Menu.MenuResponseDto;
import com.example.docksystem_erp.entity.Material.Material;
import com.example.docksystem_erp.entity.Menu.Menu;
import com.example.docksystem_erp.entity.Menu.RecipeItem;
import com.example.docksystem_erp.repository.Material.MaterialRepository;
import com.example.docksystem_erp.repository.Menu.MenuRepository;
import com.example.docksystem_erp.repository.Sales.SaleRepository;
import com.example.docksystem_erp.service.Message.StoreMessageService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class MenuService {
    private final MenuRepository menuRepository;
    private final MaterialRepository materialRepository;
    private final SaleRepository saleRepository;
    private final StoreMessageService messageService;

    @Transactional(readOnly = true)
    public List<MenuResponseDto> getAll() {
        return menuRepository.findAll().stream().map(MenuResponseDto::fromEntity).toList();
    }

    public MenuResponseDto create(MenuRequestDto dto) {
        if (menuRepository.existsByMenuCode(dto.getMenuCode())) {
            throw new IllegalStateException("이미 사용 중인 메뉴 코드입니다: " + dto.getMenuCode());
        }
        Menu menu = new Menu();
        apply(menu, dto);
        Menu saved = menuRepository.save(menu);
        // 매장 POS에 신메뉴 알림 (POS는 받으면 메뉴를 다시 받아 감)
        messageService.menuChanged("신메뉴 · " + saved.getMenuName() + " " + won(saved.getPrice()),
                saved.getMenuName() + " (" + saved.getCategory() + ") " + won(saved.getPrice()) + " · " + saved.getStatus()
                        + ". POS 메뉴에 바로 추가됩니다.",
                payload(saved, List.of("신메뉴")), true);
        return MenuResponseDto.fromEntity(saved);
    }

    public MenuResponseDto update(Long menuNo, MenuRequestDto dto) {
        Menu menu = menuRepository.findById(menuNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 메뉴를 찾을 수 없습니다: " + menuNo));
        if (!menu.getMenuCode().equals(dto.getMenuCode()) && menuRepository.existsByMenuCode(dto.getMenuCode())) {
            throw new IllegalStateException("이미 사용 중인 메뉴 코드입니다: " + dto.getMenuCode());
        }
        String oldName = menu.getMenuName(), oldStatus = menu.getStatus(), oldCategory = menu.getCategory();
        Long oldPrice = menu.getPrice();
        Map<Long, Double> oldRecipe = recipe(menu);
        apply(menu, dto);

        // 매장에 알릴 만한 변경만 POS로 보낸다 (설명 · 코드 변경은 제외)
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(oldPrice, menu.getPrice())) changes.add("가격 " + won(oldPrice) + " → " + won(menu.getPrice()));
        if (!Objects.equals(oldName, menu.getMenuName())) changes.add("이름 " + oldName + " → " + menu.getMenuName());
        if (!Objects.equals(oldStatus, menu.getStatus())) changes.add("판매 상태 " + oldStatus + " → " + menu.getStatus());
        if (!Objects.equals(oldCategory, menu.getCategory())) changes.add("분류 " + oldCategory + " → " + menu.getCategory());
        if (!oldRecipe.equals(recipe(menu))) changes.add(menu.isFinished() ? "납품 품목 변경" : "레시피 변경");
        if (!changes.isEmpty()) {
            messageService.menuChanged("메뉴 변경 · " + menu.getMenuName() + " " + changes.get(0),
                    menu.getMenuName() + ": " + String.join(", ", changes) + ". POS에 바로 반영됩니다.",
                    payload(menu, changes), !Objects.equals(oldPrice, menu.getPrice()) || !Objects.equals(oldStatus, menu.getStatus()));
        }
        return MenuResponseDto.fromEntity(menu);
    }

    public void delete(Long menuNo) {
        if (!menuRepository.existsById(menuNo)) {
            throw new EntityNotFoundException("해당 메뉴를 찾을 수 없습니다: " + menuNo);
        }
        if (saleRepository.existsByMenu_MenuNo(menuNo)) {
            throw new IllegalStateException("판매 기록이 있는 메뉴는 삭제할 수 없습니다. 판매 상태를 '단종'으로 바꿔 주세요.");
        }
        Menu menu = menuRepository.findById(menuNo).orElseThrow();
        String name = menu.getMenuName();
        Map<String, Object> payload = payload(menu, List.of("삭제"));
        menuRepository.delete(menu);
        messageService.menuChanged("메뉴 삭제 · " + name, name + " 메뉴가 삭제되어 POS 메뉴에서 빠집니다.", payload, false);
    }

    private static String won(Long v) {
        return v == null ? "-" : String.format("%,d원", v);
    }

    private static Map<Long, Double> recipe(Menu menu) {
        Map<Long, Double> m = new HashMap<>();
        menu.getRecipeItems().forEach(i -> m.put(i.getMaterial().getMaterialNo(), i.getQuantity()));
        return m;
    }

    private static Map<String, Object> payload(Menu menu, List<String> changes) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("menuCode", menu.getMenuCode());
        p.put("menuName", menu.getMenuName());
        p.put("price", menu.getPrice());
        p.put("status", menu.getStatus());
        p.put("changes", changes);
        return p;
    }

    private void apply(Menu menu, MenuRequestDto dto) {
        if (dto.getPrice() == null || dto.getPrice() <= 0) {
            throw new IllegalArgumentException("판매가는 0원보다 커야 합니다.");
        }
        menu.setMenuCode(dto.getMenuCode());
        menu.setMenuName(dto.getMenuName());
        menu.setCategory(dto.getCategory());
        menu.setPrice(dto.getPrice());
        menu.setStatus(dto.getStatus() == null ? "판매중" : dto.getStatus());
        menu.setDescription(dto.getDescription());
        boolean wasFinished = menu.isFinished();
        String type = dto.getProductType() == null || dto.getProductType().isBlank() ? menu.productTypeOrDefault() : dto.getProductType();
        if (!Menu.MADE.equals(type) && !Menu.FINISHED.equals(type)) throw new IllegalArgumentException("제공 방식은 매장 제조 · 완제품 중에서 골라 주세요.");
        menu.setProductType(type);
        if (menu.isFinished()) {
            // 완제품: 납품 품목 1개를 팔면 1개 차감 (레시피 대신)
            if (dto.getProductMaterialNo() == null) throw new IllegalArgumentException("완제품 메뉴는 납품 품목을 골라 주세요.");
            Material m = materialRepository.findById(dto.getProductMaterialNo())
                    .orElseThrow(() -> new EntityNotFoundException("해당 식자재를 찾을 수 없습니다: " + dto.getProductMaterialNo()));
            if (!"EA".equals(m.getMaterialUnit())) throw new IllegalArgumentException("납품 품목은 개(EA) 단위 식자재여야 합니다: " + m.getMaterialName());
            MenuRequestDto.Line one = new MenuRequestDto.Line();
            one.setMaterialNo(m.getMaterialNo());
            one.setQuantity(1.0);
            syncRecipe(menu, List.of(one));
        } else if (wasFinished) {
            syncRecipe(menu, List.of()); // 완제품 → 매장 제조: 납품 품목을 떼고 레시피는 레시피 화면에서
        } else if (dto.getRecipe() != null) {
            syncRecipe(menu, dto.getRecipe());
        }
    }

    // 레시피만 저장 (레시피 화면). 바뀌었으면 매장 POS에 알린다
    public MenuResponseDto updateRecipe(Long menuNo, List<MenuRequestDto.Line> lines) {
        Menu menu = menuRepository.findById(menuNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 메뉴를 찾을 수 없습니다: " + menuNo));
        if (menu.isFinished()) {
            throw new IllegalStateException(menu.getMenuName() + "은(는) 완제품이라 레시피가 없습니다. 납품 품목은 메뉴 화면에서 바꾸세요.");
        }
        Map<Long, Double> old = recipe(menu);
        syncRecipe(menu, lines == null ? List.of() : lines);
        if (!old.equals(recipe(menu))) {
            messageService.menuChanged("메뉴 변경 · " + menu.getMenuName() + " 레시피 변경",
                    menu.getMenuName() + ": 레시피가 바뀌었습니다. 식자재 사용량 계산에 바로 반영됩니다.",
                    payload(menu, List.of("레시피 변경")), false);
        }
        return MenuResponseDto.fromEntity(menu);
    }

    // 레시피는 지우고 다시 넣지 않고 바뀐 줄만 반영한다 (같은 식자재 유니크 제약 충돌 방지)
    private void syncRecipe(Menu menu, List<MenuRequestDto.Line> lines) {
        Set<Long> seen = new HashSet<>();
        for (MenuRequestDto.Line line : lines) {
            if (line.getMaterialNo() == null) throw new IllegalArgumentException("레시피에 식자재를 선택해 주세요.");
            if (line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new IllegalArgumentException("레시피 사용량은 0보다 커야 합니다.");
            }
            if (!seen.add(line.getMaterialNo())) {
                throw new IllegalArgumentException("같은 식자재가 레시피에 두 번 들어 있습니다.");
            }
        }

        Map<Long, RecipeItem> current = menu.getRecipeItems().stream()
                .collect(Collectors.toMap(i -> i.getMaterial().getMaterialNo(), Function.identity()));
        menu.getRecipeItems().removeIf(i -> !seen.contains(i.getMaterial().getMaterialNo()));

        for (MenuRequestDto.Line line : lines) {
            RecipeItem item = current.get(line.getMaterialNo());
            if (item != null) {
                item.setQuantity(line.getQuantity());
                continue;
            }
            Material material = materialRepository.findById(line.getMaterialNo())
                    .orElseThrow(() -> new EntityNotFoundException("해당 식자재를 찾을 수 없습니다: " + line.getMaterialNo()));
            RecipeItem added = new RecipeItem();
            added.setMenu(menu);
            added.setMaterial(material);
            added.setQuantity(line.getQuantity());
            menu.getRecipeItems().add(added);
        }
    }
}
