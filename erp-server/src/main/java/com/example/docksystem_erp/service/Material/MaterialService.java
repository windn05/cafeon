package com.example.docksystem_erp.service.Material;

import com.example.docksystem_erp.dto.Material.MaterialCreateRequestDto;
import com.example.docksystem_erp.dto.Material.MaterialResponseDto;
import com.example.docksystem_erp.entity.Material.Material;
import com.example.docksystem_erp.entity.Menu.RecipeItem;
import com.example.docksystem_erp.repository.Client.ClientRepository;
import com.example.docksystem_erp.repository.Inventory.InventoryTxnRepository;
import com.example.docksystem_erp.repository.Material.MaterialRepository;
import com.example.docksystem_erp.repository.Menu.RecipeItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final ClientRepository clientRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final InventoryTxnRepository txnRepository;

    @Transactional(readOnly = true)
    public List<MaterialResponseDto> getAllMaterial(){
        return materialRepository.findAll().stream().map(this::toDto).toList();
    }

    public MaterialResponseDto createMaterial(MaterialCreateRequestDto dto){
        if (materialRepository.existsByMaterialCode(dto.getMaterialCode())) {
            throw new IllegalStateException("이미 사용 중인 식자재 코드입니다: " + dto.getMaterialCode());
        }
        Material material = new Material();
        apply(material, dto);
        Material saved = materialRepository.save(material);
        return toDto(saved);
    }

    public MaterialResponseDto updateMaterial(Long materialNo, MaterialCreateRequestDto dto){
        Material material = find(materialNo);
        if (!material.getMaterialCode().equals(dto.getMaterialCode())
                && materialRepository.existsByMaterialCode(dto.getMaterialCode())) {
            throw new IllegalStateException("이미 사용 중인 식자재 코드입니다: " + dto.getMaterialCode());
        }
        apply(material, dto);
        return toDto(material);
    }

    public void deleteMaterial(Long materialNo){
        Material material = find(materialNo);
        List<RecipeItem> used = recipeItemRepository.findByMaterial_MaterialNo(materialNo);
        if (!used.isEmpty()) {
            String first = used.get(0).getMenu().getMenuName();
            String others = used.size() > 1 ? " 외 " + (used.size() - 1) + "개" : "";
            throw new IllegalStateException("레시피에서 쓰고 있어 삭제할 수 없습니다 (" + first + others + " 메뉴).");
        }
        if (txnRepository.existsByMaterial_MaterialNo(materialNo)) {
            throw new IllegalStateException("매장 재고 기록이 있는 식자재는 삭제할 수 없습니다.");
        }
        materialRepository.delete(material);
    }

    private Material find(Long materialNo) {
        return materialRepository.findById(materialNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 식자재를 찾을 수 없습니다: " + materialNo));
    }

    private void apply(Material material, MaterialCreateRequestDto dto) {
        if (dto.getPackQty() == null || dto.getPackQty() <= 0) {
            throw new IllegalArgumentException("포장 1개 용량은 0보다 커야 합니다.");
        }
        material.setMaterialCode(dto.getMaterialCode());
        material.setMaterialName(dto.getMaterialName());
        material.setMaterialType(dto.getMaterialType());
        material.setMaterialSize(dto.getMaterialSize());
        material.setMaterialPrice(dto.getMaterialPrice());
        material.setMaterialUnit(dto.getMaterialUnit());
        material.setPackQty(dto.getPackQty());
        material.setStorageType(dto.getStorageType());
        material.setShelfLifeDays(dto.getShelfLifeDays());
        material.setClient(dto.getClientNo() == null ? null : clientRepository.findById(dto.getClientNo())
                .orElseThrow(() -> new EntityNotFoundException("해당 공급업체를 찾을 수 없습니다: " + dto.getClientNo())));
    }

    private MaterialResponseDto toDto(Material material) {
        MaterialResponseDto dto = MaterialResponseDto.fromEntity(material);
        dto.setMenuCount(recipeItemRepository.countByMaterial_MaterialNo(material.getMaterialNo()));
        return dto;
    }

}
