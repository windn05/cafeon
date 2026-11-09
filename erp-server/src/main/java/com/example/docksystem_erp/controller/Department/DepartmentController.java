package com.example.docksystem_erp.controller.Department;

import com.example.docksystem_erp.dto.Department.DepartmentCreateRequestDto;
import com.example.docksystem_erp.dto.Department.DepartmentResponseDto;
import com.example.docksystem_erp.dto.Department.DepartmentUpdateRequestDto;
import com.example.docksystem_erp.entity.Department.Department;
import com.example.docksystem_erp.service.Department.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/departments")
public class DepartmentController {
    private final DepartmentService departmentService;

    //All 부서 조회
    @GetMapping
    public ResponseEntity<List<DepartmentResponseDto>> getAllDepartment(){
        List<DepartmentResponseDto> departments = departmentService.getAllDepartment();
        return ResponseEntity.ok(departments);
    }

    //Create
    @PostMapping
    public Department createDepartment(@RequestBody DepartmentCreateRequestDto requestDto){
        return  departmentService.createDepartment(requestDto);
    }

    //Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable("id")Long departmentNo){
        departmentService.deleteDepartment(departmentNo);
        return ResponseEntity.noContent().build();
    }

    //Update
    @PutMapping("/{departmentNo}")
    public DepartmentResponseDto updateDepartment(@PathVariable("departmentNo")Long departmentNo, @RequestBody DepartmentUpdateRequestDto requestDto){
        Department updateDepartment = departmentService.updateDepartment(departmentNo,requestDto);
        return DepartmentResponseDto.fromEntity(updateDepartment);
    }
}
