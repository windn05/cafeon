package com.example.cafeon_pos.config;

import com.example.cafeon_pos.service.HqClient;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// 예외를 화면에서 읽을 수 있는 {message}로 바꾼다.
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(EntityNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(com.example.cafeon_pos.service.PosService.NotRegistered.class)
    public ResponseEntity<Map<String, String>> notRegistered(RuntimeException e) {
        return body(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    // 기기 등록 때 본사가 키를 거절함
    @ExceptionHandler(HqClient.Rejected.class)
    public ResponseEntity<Map<String, String>> hqRejected(HqClient.Rejected e) {
        return body(e.status() == 401 ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HqClient.Unavailable.class)
    public ResponseEntity<Map<String, String>> hqDown(HqClient.Unavailable e) {
        return body(HttpStatus.SERVICE_UNAVAILABLE, "본사 서버에 연결할 수 없습니다. 인터넷 · 본사 서버 상태를 확인하세요.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> other(Exception e) {
        log.error("POS 처리 오류", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "처리 중 오류가 났습니다: " + e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message == null ? status.getReasonPhrase() : message));
    }
}
