package com.example.docksystem_erp.dto.Auth;

import lombok.Data;

@Data
public class LoginRequestDto {
    private String userId;
    private String password;
}
