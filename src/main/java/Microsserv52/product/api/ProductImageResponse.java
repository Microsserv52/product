package Microsserv52.product.api;

public record ProductImageResponse(
    String url,
    String alt,
    int width,
    int height
) {}
