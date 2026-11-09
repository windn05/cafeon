package com.example.docksystem_erp.config;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// 서비스에서 던진 예외를 화면에서 읽을 수 있는 {message} 형태로 바꿔 준다.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(EntityNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // 매장 연동 키가 없거나 틀림 (데이터 수집 API)
    @ExceptionHandler(com.example.docksystem_erp.service.Integration.IngestService.StoreKeyException.class)
    public ResponseEntity<Map<String, String>> storeKey(RuntimeException e) {
        return body(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    // 다른 데이터에서 사용 중이라 지울 수 없는 경우 등
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    // 입력값이 잘못된 경우
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // DB 제약 조건 위반 (중복 코드, 참조 중인 데이터 삭제 등)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {
        log.warn("DB 제약 조건 위반: {}", e.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, "중복된 값이 있거나, 다른 데이터에서 사용 중이라 처리할 수 없습니다.");
    }

    private ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message == null ? status.getReasonPhrase() : message));
    }
}
