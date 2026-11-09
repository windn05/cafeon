package com.example.docksystem_erp.controller.Material;

import com.example.docksystem_erp.dto.Material.MaterialCreateRequestDto;
import com.example.docksystem_erp.dto.Material.MaterialResponseDto;
import com.example.docksystem_erp.service.Material.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/materials")
public class MaterialController {
    private final MaterialService materialService;

    @GetMapping
    public ResponseEntity<List<MaterialResponseDto>> getAllMaterial(){
        return ResponseEntity.ok(materialService.getAllMaterial());
    }

    @PostMapping
    public MaterialResponseDto createMaterial(@RequestBody MaterialCreateRequestDto requestDto){
        return materialService.createMaterial(requestDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMaterial(@PathVariable("id") Long materialNo){
        materialService.deleteMaterial(materialNo);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{materialNo}")
    public MaterialResponseDto updateMaterial(@PathVariable("materialNo") Long materialNo,
                                              @RequestBody MaterialCreateRequestDto requestDto){
        return materialService.updateMaterial(materialNo, requestDto);
    }
}
