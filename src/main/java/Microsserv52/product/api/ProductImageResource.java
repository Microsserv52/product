package Microsserv52.product.api;

import java.util.UUID;

import Microsserv52.product.service.ProductImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products/{id}/image")
public class ProductImageResource {
    private final ProductImageService imageService;

    public ProductImageResource(ProductImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadResponse createUpload(@PathVariable UUID id,
                                            @Valid @RequestBody ImageUploadRequest request) {
        return imageService.createUpload(id, request);
    }

    @PostMapping
    public ProductResponse attachImage(@PathVariable UUID id,
                                       @Valid @RequestBody ImageAttachRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return imageService.attachImage(id, request, jwt != null ? jwt.getSubject() : null);
    }

    @PatchMapping
    public ProductResponse updateAlt(@PathVariable UUID id,
                                     @Valid @RequestBody ImageAltRequest request) {
        return imageService.updateAlt(id, request);
    }

    @DeleteMapping
    public ProductResponse removeImage(@PathVariable UUID id) {
        return imageService.removeImage(id);
    }
}
