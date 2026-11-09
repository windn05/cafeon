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

    @DeleteMapping("/{menuNo}")
    public ResponseEntity<Void> delete(@PathVariable("menuNo") Long menuNo) {
        menuService.delete(menuNo);
        return ResponseEntity.noContent().build();
    }
}
