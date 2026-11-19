package Microsserv52.product.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String code, String message, List<FieldErrorDetail> details) {
    public ErrorResponse(String code, String message) {
        this(code, message, List.of());
    }
}
