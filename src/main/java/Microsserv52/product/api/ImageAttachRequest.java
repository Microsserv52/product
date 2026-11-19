package Microsserv52.product.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ImageAttachRequest(
    @NotNull UUID uploadId,
    @NotBlank @Size(max = 200) String alt
) {}
