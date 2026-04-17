package com.evernex.bms.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", e.getMessage());
        if (e.getDetail() != null) body.put("detail", e.getDetail());
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> bad(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "잘못된 요청" : e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> any(Exception e) {
        return ResponseEntity.status(500).body(Map.of(
            "error", "서버 오류",
            "detail", e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage())
        ));
    }
}
