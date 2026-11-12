package com.example.docksystem_erp.controller.Menu;

import com.example.docksystem_erp.dto.Menu.MenuRequestDto;
import com.example.docksystem_erp.dto.Menu.MenuResponseDto;
import com.example.docksystem_erp.service.Menu.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/menus")
public class MenuController {
    private final MenuService menuService;

    @GetMapping
    public List<MenuResponseDto> getAll() {
        return menuService.getAll();
    }

    @PostMapping
    public MenuResponseDto create(@RequestBody MenuRequestDto dto) {
        return menuService.create(dto);
    }

    @PutMapping("/{menuNo}")
    public MenuResponseDto update(@PathVariable("menuNo") Long menuNo, @RequestBody MenuRequestDto dto) {
        return menuService.update(menuNo, dto);
    }

    // 레시피만 저장: 식자재와 1개당 사용량 목록을 통째로 보내면 바뀐 줄만 반영
    @PutMapping("/{menuNo}/recipe")
    public MenuResponseDto updateRecipe(@PathVariable("menuNo") Long menuNo, @RequestBody java.util.List<MenuRequestDto.Line> lines) {
        return menuService.updateRecipe(menuNo, lines);
    }

    @DeleteMapping("/{menuNo}")
    public ResponseEntity<Void> delete(@PathVariable("menuNo") Long menuNo) {
        menuService.delete(menuNo);
        return ResponseEntity.noContent().build();
    }
}
