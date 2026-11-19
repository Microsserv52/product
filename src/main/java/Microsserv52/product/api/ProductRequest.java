package Microsserv52.product.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(
    @NotBlank @Size(max = 120) String name,
    @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
    @NotBlank @Size(max = 32) String unit
) {}
