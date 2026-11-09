package com.example.docksystem_erp.controller.Role;

import com.example.docksystem_erp.dto.Role.RoleCreateRequestDto;
import com.example.docksystem_erp.dto.Role.RoleResponseDto;
import com.example.docksystem_erp.dto.Role.RoleUpdateRequestDto;
import com.example.docksystem_erp.entity.Role.Role;
import com.example.docksystem_erp.service.Role.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/roles")
public class RoleController {
    private final RoleService roleService;

    //All 직급 조회
    @GetMapping
    public ResponseEntity<List<RoleResponseDto>> getAllRole(){
        List<RoleResponseDto> roles = roleService.getAllRole();
        return ResponseEntity.ok(roles);
    }
    //Create
    @PostMapping
    public Role createRole(@RequestBody RoleCreateRequestDto requestDto){
        return roleService.createRole(requestDto);
    }
    //Delete

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable("id")Long roleNo){
        roleService.deleteRole(roleNo);
        return ResponseEntity.noContent().build();
    }
    //Update
    @PutMapping("/{roleNo}")
    public RoleResponseDto updateRole(@PathVariable("roleNo")Long roleNo, @RequestBody RoleUpdateRequestDto requestDto){
        Role updateRole = roleService.updateRole(roleNo,requestDto);
        return RoleResponseDto.fromEntity(updateRole);
    }
}
