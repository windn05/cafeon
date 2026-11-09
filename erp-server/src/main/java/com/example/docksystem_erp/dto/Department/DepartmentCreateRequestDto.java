package com.example.docksystem_erp.dto.Department;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DepartmentCreateRequestDto {
    @NotBlank(message = "부서명은 필수입니다.")
    private String departmentName;
}
