package com.example.docksystem_erp.controller.Equipment;

import com.example.docksystem_erp.dto.Equipment.EquipmentCreateRequestDto;
import com.example.docksystem_erp.dto.Equipment.EquipmentResponseDto;
import com.example.docksystem_erp.dto.Equipment.EquipmentUpdateRequestDto;
import com.example.docksystem_erp.entity.Equipment.Equipment;
import com.example.docksystem_erp.service.Equipment.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/equipments")
public class EquipmentController {
    private final EquipmentService equipmentService;

    //All 장비 정보 조회
    @GetMapping
    public ResponseEntity<List<EquipmentResponseDto>> getAllEquipment(){
        List<EquipmentResponseDto> equipments = equipmentService.getAllEquipment();
        return ResponseEntity.ok(equipments);
    }
    //Create
    @PostMapping
    public Equipment createEquipment(@RequestBody EquipmentCreateRequestDto requestDto){
        return equipmentService.createEquipment(requestDto);
    }
    //Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEquipment(@PathVariable("id") Long equipNo){
        equipmentService.deleteEquipment(equipNo);
        return ResponseEntity.noContent().build();
    }
    //Update
    @PutMapping("/{equipNo}")
    public EquipmentResponseDto updateEquipment(@PathVariable("equipNo") Long equipNo, @RequestBody EquipmentUpdateRequestDto requestDto){
        Equipment updateEquipment = equipmentService.updateEquipment(equipNo,requestDto);
        return EquipmentResponseDto.fromEntity(updateEquipment);
    }

}
