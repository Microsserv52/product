package Microsserv52.product.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ImageUploadResponse(
    UUID uploadId,
    String uploadUrl,
    Map<String, String> uploadHeaders,
    Instant expiresAt
) {}
