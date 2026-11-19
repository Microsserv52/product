package Microsserv52.product.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImageAltRequest(
    @NotBlank @Size(max = 200) String alt
) {}
