package com.example.docksystem_erp.controller.Store;

import com.example.docksystem_erp.dto.Store.StoreRequestDto;
import com.example.docksystem_erp.dto.Store.StoreResponseDto;
import com.example.docksystem_erp.service.Store.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/stores")
public class StoreController {
    private final StoreService storeService;

    @GetMapping
    public List<StoreResponseDto> getAll() {
        return storeService.getAll();
    }

    @PostMapping
    public StoreResponseDto create(@RequestBody StoreRequestDto dto) {
        return storeService.create(dto);
    }

    @PutMapping("/{storeNo}")
    public StoreResponseDto update(@PathVariable("storeNo") Long storeNo, @RequestBody StoreRequestDto dto) {
        return storeService.update(storeNo, dto);
    }

    @DeleteMapping("/{storeNo}")
    public ResponseEntity<Void> delete(@PathVariable("storeNo") Long storeNo) {
        storeService.delete(storeNo);
        return ResponseEntity.noContent().build();
    }
}
