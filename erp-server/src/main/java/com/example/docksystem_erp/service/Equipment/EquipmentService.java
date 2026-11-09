package com.example.docksystem_erp.service.Equipment;

import com.example.docksystem_erp.dto.Equipment.*;
import com.example.docksystem_erp.entity.Equipment.Equipment;
import com.example.docksystem_erp.entity.Equipment.EquipmentType;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Equipment.EquipmentRepository;
import com.example.docksystem_erp.repository.Store.StoreRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final StoreRepository storeRepository;

    @Autowired
    public EquipmentService(EquipmentRepository equipmentRepository,
                            StoreRepository storeRepository){
        this.equipmentRepository = equipmentRepository;
        this.storeRepository = storeRepository;
    }

    private Store findStore(Long storeNo) {
        if (storeNo == null) return null;
        Store store = storeRepository.findById(storeNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 매장을 찾을 수 없습니다: " + storeNo));
        if ("가맹점".equals(store.getStoreType())) {
            throw new IllegalArgumentException("가맹점 자산은 점주 소유라 본사 자산으로 등록할 수 없습니다.");
        }
        return store;
    }

    private Integer usefulLife(Integer years) {
        if (years == null) return 5;
        if (years < 1 || years > 40) throw new IllegalArgumentException("내용연수는 1~40년 사이로 입력해 주세요.");
        return years;
    }
    //새로운 장비 정보 생성
    public Equipment createEquipment(EquipmentCreateRequestDto requestDto){
        Equipment equipment = new Equipment();
        equipment.setEquipCode(requestDto.getEquipCode());
        equipment.setEquipName(requestDto.getEquipName());
        equipment.setType(requestDto.getType());
        equipment.setEquipPrice(requestDto.getEquipPrice());
        equipment.setEquipPurchaseDate(requestDto.getEquipPurchaseDate());
        equipment.setUsefulLifeYears(usefulLife(requestDto.getUsefulLifeYears()));
        equipment.setEquipCategory(requestDto.getEquipCategory());
        equipment.setStore(findStore(requestDto.getStoreNo()));

        Equipment newEquipment = equipmentRepository.save(equipment);

        return newEquipment;
    }

    //db상 모든 창고 정보 조회
    public List<EquipmentResponseDto> getAllEquipment(){
        return equipmentRepository.findAll().stream()
                .map(EquipmentResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    //삭제
    public void deleteEquipment(Long equipNo){
        Equipment existingEquipment = equipmentRepository.findById(equipNo)
                .orElseThrow(()->new EntityNotFoundException("해당 No의 장비를 찾을 수 없습니다."+equipNo));

        equipmentRepository.delete(existingEquipment);
    }


    //업데이트
    public Equipment updateEquipment(Long equipNo, EquipmentUpdateRequestDto requestDto){
        Equipment existingEquipment = equipmentRepository.findById(equipNo)
                .orElseThrow(()->new EntityNotFoundException("해당 No의 장비를 찾을 수 없습니다."+equipNo));
        requestDto.setUsefulLifeYears(usefulLife(requestDto.getUsefulLifeYears()));
        existingEquipment.updateEquipment(requestDto);
        existingEquipment.setStore(findStore(requestDto.getStoreNo()));

        Equipment updateEquipment = equipmentRepository.save((existingEquipment));

        return existingEquipment;
    }

}
