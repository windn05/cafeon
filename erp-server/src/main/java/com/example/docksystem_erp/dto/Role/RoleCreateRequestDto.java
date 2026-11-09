package com.example.docksystem_erp.dto.Role;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoleCreateRequestDto {

    @NotBlank(message = "직급의 명은 필수입니다.")
    private String roleName;
}
