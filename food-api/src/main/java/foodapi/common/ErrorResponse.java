package foodapi.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String code,
        String message,
        List<FieldError> errors
) {
    public record FieldError(
            String field,
            String message
    ) {}

    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(status, code, message, null);
    }

    public static ErrorResponse ofValidation(int status, String code, String message, List<FieldError> errors) {
        return new ErrorResponse(status, code, message, errors);
    }
}