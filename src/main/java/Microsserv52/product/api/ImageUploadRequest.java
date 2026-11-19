package Microsserv52.product.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ImageUploadRequest(
    @NotBlank @Size(max = 120) String fileName,
    @NotBlank @Size(max = 64) String contentType,
    @NotNull @Min(1) @Max(5242880) Long size,
    @NotNull @Min(1) @Max(4096) Integer width,
    @NotNull @Min(1) @Max(4096) Integer height
) {}
