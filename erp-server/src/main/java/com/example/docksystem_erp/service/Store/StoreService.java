package com.example.docksystem_erp.service.Store;

import com.example.docksystem_erp.dto.Store.StoreRequestDto;
import com.example.docksystem_erp.dto.Store.StoreResponseDto;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Equipment.EquipmentRepository;
import com.example.docksystem_erp.repository.Inventory.InventoryTxnRepository;
import com.example.docksystem_erp.repository.Sales.SaleRepository;
import com.example.docksystem_erp.repository.Store.StoreRepository;
import com.example.docksystem_erp.repository.User.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class StoreService {
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;
    private final SaleRepository saleRepository;
    private final InventoryTxnRepository txnRepository;

    @Transactional(readOnly = true)
    public List<StoreResponseDto> getAll() {
        return storeRepository.findAll().stream().map(this::toDto).toList();
    }

    public StoreResponseDto create(StoreRequestDto dto) {
        if (storeRepository.existsByStoreCode(dto.getStoreCode())) {
            throw new IllegalStateException("이미 사용 중인 매장 코드입니다: " + dto.getStoreCode());
        }
        Store store = new Store();
        store.apply(dto);
        return toDto(storeRepository.save(store));
    }

    public StoreResponseDto update(Long storeNo, StoreRequestDto dto) {
        Store store = find(storeNo);
        if (!store.getStoreCode().equals(dto.getStoreCode()) && storeRepository.existsByStoreCode(dto.getStoreCode())) {
            throw new IllegalStateException("이미 사용 중인 매장 코드입니다: " + dto.getStoreCode());
        }
        store.apply(dto);
        return toDto(store);
    }

    public void delete(Long storeNo) {
        Store store = find(storeNo);
        long staff = userRepository.countByStore_StoreNo(storeNo);
        long equipment = equipmentRepository.countByStore_StoreNo(storeNo);
        if (staff > 0 || equipment > 0) {
            throw new IllegalStateException(String.format(
                    "이 매장에 소속 직원 %d명, 자산 %d개가 있어 삭제할 수 없습니다. 먼저 소속을 옮기거나 '폐점'으로 바꿔 주세요.", staff, equipment));
        }
        if (saleRepository.existsByStore_StoreNo(storeNo) || txnRepository.existsByStore_StoreNo(storeNo)) {
            throw new IllegalStateException("판매·재고 기록이 있는 매장은 삭제할 수 없습니다. 영업 상태를 '폐점'으로 바꿔 주세요.");
        }
        storeRepository.delete(store);
    }

    public Store find(Long storeNo) {
        return storeRepository.findById(storeNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 매장을 찾을 수 없습니다: " + storeNo));
    }

    private StoreResponseDto toDto(Store store) {
        StoreResponseDto dto = StoreResponseDto.fromEntity(store);
        dto.setStaffCount(userRepository.countByStore_StoreNo(store.getStoreNo()));
        dto.setEquipmentCount(equipmentRepository.countByStore_StoreNo(store.getStoreNo()));
        return dto;
    }
}
