package foodapi.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.slf4j.MDC;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String code,
        String message,
        String requestId,
        List<FieldError> errors
) {
    public record FieldError(
            String field,
            String message
    ) {}

    // 요청 ID는 로그와 연결하기 위해 현재 요청의 MDC에서 가져온다 (없으면 응답에서 생략)
    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(status, code, message, MDC.get(RequestLoggingFilter.MDC_KEY), null);
    }

    public static ErrorResponse ofValidation(int status, String code, String message, List<FieldError> errors) {
        return new ErrorResponse(status, code, message, MDC.get(RequestLoggingFilter.MDC_KEY), errors);
    }
}
