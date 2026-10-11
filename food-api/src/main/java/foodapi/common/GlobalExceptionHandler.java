package foodapi.common;

import foodapi.food.DuplicateFoodCodeException;
import foodapi.food.FoodNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 404 - 식품 조회 실패
    @ExceptionHandler(FoodNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFoodNotFound(FoodNotFoundException e) {
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "FOOD_NOT_FOUND",
                e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // 2. 409 - 비즈니스 레벨 식품코드 중복
    @ExceptionHandler(DuplicateFoodCodeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateFoodCode(DuplicateFoodCodeException e) {
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "DUPLICATE_FOOD_CODE",
                e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // 3. 409 - DB 레벨 무결성 제약조건 위반
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("데이터 무결성 제약조건 위반 발생: {}", e.getMessage());
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "DUPLICATE_FOOD_CODE",
                "이미 존재하는 데이터이거나 무결성 제약조건을 위반했습니다."
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // 4. 400 - @Valid 바인딩 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(err -> new ErrorResponse.FieldError(
                        toSnakeCase(err.getField()),
                        err.getDefaultMessage()
                ))
                .toList();

        ErrorResponse response = ErrorResponse.ofValidation(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST",
                "요청 데이터 검증에 실패했습니다.",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 5. 400 - JSON 요청 본문 형식 오류 (예: 잘못된 JSON, 역직렬화 실패)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST",
                "요청 본문(JSON) 형식이 올바르지 않거나 역직렬화할 수 없습니다."
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 6. 400 - URL 경로 변수나 쿼리 파라미터 타입 불일치
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        String paramName = toSnakeCase(e.getName());
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST",
                String.format("'%s' 파라미터의 형식이 올바르지 않습니다.", paramName)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 7. 500 - 기타 처리되지 않은 모든 서버 오류
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        log.error("처리되지 않은 내부 서버 오류 발생", e);
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "서버 내부 오류가 발생했습니다."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // 8. 405 - 지원하지 않는 HTTP 메서드
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "METHOD_NOT_ALLOWED",
                "지원하지 않는 HTTP 메서드입니다: " + e.getMethod()
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    // 9. 415 - 지원하지 않는 미디어 타입 (Content-Type 불일치)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        String contentType = e.getContentType() != null ? e.getContentType().toString() : "알 수 없음";
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "UNSUPPORTED_MEDIA_TYPE",
                "지원하지 않는 Content-Type입니다: " + contentType
        );
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    // 10. 404 - 정적 리소스 또는 매핑되지 않은 URL 경로 요청 (Spring Boot 3.2+)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                "요청한 경로를 찾을 수 없습니다."
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

   // 유틸리티 메서드: camelCase를 snake_case로 변환
    private String toSnakeCase(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}