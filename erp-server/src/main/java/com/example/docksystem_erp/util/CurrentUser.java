package com.example.docksystem_erp.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// 지금 요청을 보낸 로그인 사용자 아이디 (JWT의 subject)
public final class CurrentUser {
    private CurrentUser() {}

    public static String id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? "system" : auth.getName();
    }
}
