package Microsserv52.product.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    String name,
    BigDecimal price,
    String unit,
    ProductImageResponse image
) implements Serializable {}
