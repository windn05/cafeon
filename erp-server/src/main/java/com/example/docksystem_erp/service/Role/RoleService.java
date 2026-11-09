package com.example.docksystem_erp.service.Role;

import com.example.docksystem_erp.dto.Role.RoleCreateRequestDto;
import com.example.docksystem_erp.dto.Role.RoleResponseDto;
import com.example.docksystem_erp.dto.Role.RoleUpdateRequestDto;
import com.example.docksystem_erp.entity.Role.Role;
import com.example.docksystem_erp.repository.Role.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleService {
    private final RoleRepository roleRepository;
    @Autowired
    public RoleService(RoleRepository roleRepository){this.roleRepository = roleRepository;}

    //새로운 직급 정보 생성
    public Role createRole(RoleCreateRequestDto requestDto){
        Role role = new Role();
        role.setRoleName(requestDto.getRoleName());
        return roleRepository.save(role);
    }

    //db상 모든 창고 정보 조회
    public List<RoleResponseDto> getAllRole(){
        return roleRepository.findAll().stream()
                .map(RoleResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    //삭제
    public void deleteRole(Long roleNo){
        if(!roleRepository.existsById(roleNo)){
            throw new EntityNotFoundException("해당 No의 직급을 찾을 수 없습니다."+roleNo);
        }
        roleRepository.deleteById(roleNo);
    }

    //업데이트
    public Role updateRole(Long roleNo, RoleUpdateRequestDto requestDto){
        Role existingRole = roleRepository.findById(roleNo)
                .orElseThrow(()->new EntityNotFoundException("해당 No의 직급을 찾을 수 없습니다."+roleNo));
        existingRole.updateRole(requestDto);
        return existingRole;
    }
}
