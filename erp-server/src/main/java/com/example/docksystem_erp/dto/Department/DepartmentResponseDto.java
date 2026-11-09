package com.example.docksystem_erp.dto.Department;

import com.example.docksystem_erp.entity.Department.Department;
import lombok.Data;

@Data
public class DepartmentResponseDto {
    private Long departmentNo;
    private String departmentName;

    public static DepartmentResponseDto fromEntity(Department department){
        DepartmentResponseDto dto = new DepartmentResponseDto();
        dto.setDepartmentNo(department.getDepartmentNo());
        dto.setDepartmentName(department.getDepartmentName());
        return dto;
    }
}
