package Microsserv52.product.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import Microsserv52.product.api.ImageAltRequest;
import Microsserv52.product.api.ImageAttachRequest;
import Microsserv52.product.api.ImageUploadRequest;
import Microsserv52.product.api.ImageUploadResponse;
import Microsserv52.product.api.ProductResponse;
import Microsserv52.product.api.ProductImageResponse;
import Microsserv52.product.domain.ImageUploadTicket;
import Microsserv52.product.domain.ImageUploadTicketRepository;
import Microsserv52.product.domain.Product;
import Microsserv52.product.domain.ProductImage;
import Microsserv52.product.domain.ProductImageRepository;
import Microsserv52.product.domain.ProductRepository;
import Microsserv52.product.service.image.ImageStorage;
import Microsserv52.product.service.image.ImageStorageProperties;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProductImageService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_PIXELS = 16_000_000;
    private static final Map<String, String> REPLY = Map.of("image/jpeg", "jpg", "image/png", "png");

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ImageUploadTicketRepository ticketRepository;
    private final ImageStorage storage;
    private final ImageStorageProperties properties;
    private final TransactionTemplate transactionTemplate;

    public ProductImageService(ProductRepository productRepository,
                               ProductImageRepository imageRepository,
                               ImageUploadTicketRepository ticketRepository,
                               ImageStorage storage,
                               ImageStorageProperties properties,
                               TransactionTemplate transactionTemplate) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.ticketRepository = ticketRepository;
        this.storage = storage;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    public ImageUploadResponse createUpload(UUID productId, ImageUploadRequest request) {
        productRepository.findById(productId)
            .orElseThrow(ProductNotFoundException::new);
        validateRequest(request);
        String extension = REPLY.get(request.contentType());
        UUID assetId = UUID.randomUUID();
        String key = "pending/products/" + productId + "/" + assetId + "/original." + extension;
        Instant expiresAt = Instant.now().plus(properties.getUploadExpiry());
        ImageUploadTicket ticket = ticketRepository.save(new ImageUploadTicket(
            UUID.randomUUID(), productId, assetId, key, request.contentType(),
            request.size(), request.width(), request.height(), expiresAt));
        ImageStorage.PresignedPut put = storage.presignPut(key, request.contentType(), request.size());
        return new ImageUploadResponse(ticket.getId(), put.url(), put.headers(), expiresAt);
    }

    @CacheEvict(cacheNames = {ProductService.CACHE_PRODUCT, ProductService.CACHE_LIST}, allEntries = true)
    public ProductResponse attachImage(UUID productId, ImageAttachRequest request, String uploader) {
        ProductResponse response = transactionTemplate.execute(status ->
            attachInTransaction(productId, request, uploader));
        if (response == null) {
            throw new DependencyUnavailableException("Image attachment did not complete");
        }
        return response;
    }

    @CacheEvict(cacheNames = {ProductService.CACHE_PRODUCT, ProductService.CACHE_LIST}, allEntries = true)
    public ProductResponse updateAlt(UUID productId, ImageAltRequest request) {
        ProductResponse response = transactionTemplate.execute(status -> {
            Product product = productRepository.findById(productId)
                .orElseThrow(ProductNotFoundException::new);
            ProductImage image = imageRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException());
            image.setAlt(request.alt().trim());
            imageRepository.save(image);
            return toResponse(product, image);
        });
        if (response == null) {
            throw new DependencyUnavailableException("Image update did not complete");
        }
        return response;
    }

    @CacheEvict(cacheNames = {ProductService.CACHE_PRODUCT, ProductService.CACHE_LIST}, allEntries = true)
    public ProductResponse removeImage(UUID productId) {
        ProductResponse response = transactionTemplate.execute(status -> {
            Product product = productRepository.findById(productId)
                .orElseThrow(ProductNotFoundException::new);
            imageRepository.findById(productId).ifPresent(image -> {
                storage.delete(image.getObjectKey());
                imageRepository.delete(image);
            });
            return toResponse(product, null);
        });
        if (response == null) {
            throw new DependencyUnavailableException("Image removal did not complete");
        }
        return response;
    }

    public ProductImageResponse toImageResponse(ProductImage image) {
        return new ProductImageResponse(
            storage.displayUrl(image.getObjectKey()),
            image.getAlt(),
            image.getWidth(),
            image.getHeight());
    }

    private ProductResponse attachInTransaction(UUID productId, ImageAttachRequest request, String uploader) {
        Product product = productRepository.findById(productId)
            .orElseThrow(ProductNotFoundException::new);
        ImageUploadTicket ticket = ticketRepository.findById(request.uploadId())
            .filter(candidate -> candidate.getProductId().equals(productId))
            .orElseThrow(() -> new InvalidRequestException("image upload ticket is not valid for this product"));
        if (ticket.getConsumedAt() != null) {
            throw new InvalidRequestException("image upload ticket was already consumed");
        }
        if (!ticket.getExpiresAt().isAfter(Instant.now())) {
            throw new InvalidRequestException("image upload ticket has expired");
        }
        byte[] bytes = storage.fetch(ticket.getObjectKey());
        validateContent(ticket, bytes);
        String mediaKey = "media/products/" + productId + "/" + ticket.getAssetId()
            + "/original." + extensionFor(ticket.getContentType());
        storage.saveMedia(mediaKey, bytes, ticket.getContentType());
        imageRepository.findById(productId).ifPresent(previous -> {
            storage.delete(previous.getObjectKey());
            imageRepository.delete(previous);
        });
        ProductImage image = imageRepository.save(new ProductImage(
            productId,
            ticket.getAssetId(),
            mediaKey,
            ticket.getContentType(),
            bytes.length,
            ticket.getWidth(),
            ticket.getHeight(),
            request.alt().trim(),
            uploader));
        ticket.consume();
        ticketRepository.save(ticket);
        return toResponse(product, image);
    }

    private void validateRequest(ImageUploadRequest request) {
        String contentType = request.contentType().trim().toLowerCase();
        if (!REPLY.containsKey(contentType)) {
            throw new InvalidRequestException("contentType must be image/jpeg or image/png");
        }
        if ((long) request.width() * (long) request.height() > MAX_PIXELS) {
            throw new InvalidRequestException("image must be at most 16 million pixels");
        }
        if (request.size() > MAX_BYTES) {
            throw new InvalidRequestException("image must be at most 5 MiB");
        }
        String name = request.fileName().trim().toLowerCase();
        String extension = name.substring(name.lastIndexOf('.') + 1);
        boolean jpegOk = ("jpg".equals(extension) || "jpeg".equals(extension)) && contentType.equals("image/jpeg");
        boolean pngOk = "png".equals(extension) && contentType.equals("image/png");
        if (!jpegOk && !pngOk) {
            throw new InvalidRequestException("fileName extension must match contentType");
        }
    }

    private void validateContent(ImageUploadTicket ticket, byte[] bytes) {
        if (bytes == null || bytes.length != ticket.getBytes()) {
            throw new InvalidRequestException("uploaded content length does not match the upload ticket");
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new InvalidRequestException("uploaded content is not a supported JPEG or PNG image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                String format = reader.getFormatName().toLowerCase();
                String expected = ticket.getContentType().equals("image/jpeg") ? "jpeg" : "png";
                if (!(format.equals(expected) || (expected.equals("jpeg") && format.equals("jpg")))) {
                    throw new InvalidRequestException("uploaded content does not match contentType");
                }
                if (width != ticket.getWidth() || height != ticket.getHeight()) {
                    throw new InvalidRequestException("uploaded image dimensions do not match the upload ticket");
                }
                if ((long) width * (long) height > MAX_PIXELS) {
                    throw new InvalidRequestException("image must be at most 16 million pixels");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof InvalidRequestException invalid) {
                throw invalid;
            }
            throw new InvalidRequestException("uploaded content could not be decoded as a JPEG or PNG image");
        }
    }

    private String extensionFor(String contentType) {
        String extension = REPLY.get(contentType);
        if (extension == null) {
            throw new InvalidRequestException("contentType must be image/jpeg or image/png");
        }
        return extension;
    }

    private ProductResponse toResponse(Product product, ProductImage image) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getUnit(),
            image == null ? null : toImageResponse(image));
    }
}
