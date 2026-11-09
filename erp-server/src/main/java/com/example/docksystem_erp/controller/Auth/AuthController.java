package com.example.docksystem_erp.controller.Auth;

import com.example.docksystem_erp.dto.Auth.LoginRequestDto;
import com.example.docksystem_erp.dto.Auth.LoginResponseDto;
import com.example.docksystem_erp.service.Auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// 별도 인증 서버(9090) 없이 ERP가 직접 로그인을 처리한다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDto requestDto) {
        return authService.login(requestDto)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "아이디 또는 비밀번호가 올바르지 않습니다.")));
    }
}
