package com.chris64233.cc.satellite.web;

import com.chris64233.cc.satellite.service.ConflictException;
import com.chris64233.cc.satellite.service.NotFoundException;
import com.chris64233.cc.satellite.service.UnschedulableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(String error, String message) {
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(UnschedulableException.class)
    public ResponseEntity<ErrorResponse> unschedulable(UnschedulableException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .findFirst()
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", message));
    }

    /** 并发下幂等键唯一约束被触发的兜底：视为冲突。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> dataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", "请求与现有数据冲突"));
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, RuntimeException ex) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.name(), ex.getMessage()));
    }
}
