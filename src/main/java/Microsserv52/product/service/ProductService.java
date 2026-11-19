package Microsserv52.product.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import Microsserv52.product.api.ProductRequest;
import Microsserv52.product.api.ProductResponse;
import Microsserv52.product.api.ProductImageResponse;
import Microsserv52.product.domain.IdempotencyRecord;
import Microsserv52.product.domain.IdempotencyRepository;
import Microsserv52.product.domain.Product;
import Microsserv52.product.domain.ProductImage;
import Microsserv52.product.domain.ProductImageRepository;
import Microsserv52.product.domain.ProductRepository;
import Microsserv52.product.service.image.ImageStorage;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProductService {
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    public static final String CACHE_PRODUCT = "product";
    public static final String CACHE_LIST = "product-list";

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final ImageStorage storage;

    public ProductService(ProductRepository productRepository,
                          ProductImageRepository imageRepository,
                          IdempotencyRepository idempotencyRepository,
                          TransactionTemplate transactionTemplate,
                          MeterRegistry meterRegistry,
                          ImageStorage storage) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistry;
        this.storage = storage;
    }

    @Cacheable(cacheNames = CACHE_PRODUCT, key = "#id")
    public ProductResponse findById(UUID id) {
        return productRepository.findById(id)
            .map(product -> toResponse(product, imageRepository.findById(id).orElse(null)))
            .orElseThrow(ProductNotFoundException::new);
    }

    @Cacheable(cacheNames = CACHE_LIST, key = "'all'")
    public List<ProductResponse> findAll() {
        Map<UUID, ProductImage> images = imageRepository.findAll().stream()
            .collect(Collectors.toMap(
                ProductImage::getProductId,
                image -> image,
                (first, second) -> first));
        return new ArrayList<>(productRepository.findAllByOrderByNameAsc().stream()
            .map(product -> toResponse(product, images.get(product.getId())))
            .toList());
    }

    @CacheEvict(cacheNames = {CACHE_PRODUCT, CACHE_LIST}, allEntries = true)
    public ProductResponse create(ProductRequest request, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        String requestHash = requestHash(request);
        try {
            ProductResponse response = transactionTemplate.execute(
                status -> createInTransaction(request, key, requestHash));
            meterRegistry.counter("product.created").increment();
            return Objects.requireNonNull(response);
        } catch (DataIntegrityViolationException exception) {
            if (key == null) {
                throw exception;
            }
            return replayAfterConcurrentInsert(key, requestHash);
        }
    }

    @CacheEvict(cacheNames = {CACHE_PRODUCT, CACHE_LIST}, allEntries = true)
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
            .orElseThrow(ProductNotFoundException::new);
        productRepository.delete(product);
        meterRegistry.counter("product.deleted").increment();
    }

    private ProductResponse createInTransaction(ProductRequest request, String key, String requestHash) {
        if (key != null) {
            Optional<IdempotencyRecord> existing = idempotencyRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return replay(existing.get(), requestHash);
            }
        }
        UUID id = UUID.randomUUID();
        Product product = productRepository.saveAndFlush(new Product(
            id,
            request.name().trim(),
            request.price().setScale(2, RoundingMode.UNNECESSARY),
            request.unit().trim()));
        if (key != null) {
            idempotencyRepository.saveAndFlush(new IdempotencyRecord(key, requestHash, id));
        }
        return toResponse(product, null);
    }

    private ProductResponse replayAfterConcurrentInsert(String key, String requestHash) {
        try {
            ProductResponse response = transactionTemplate.execute(status ->
                idempotencyRepository.findByIdempotencyKey(key)
                    .map(record -> replay(record, requestHash))
                    .orElseThrow(() -> new DependencyUnavailableException(
                        "Concurrent idempotent request is not committed yet")));
            return Objects.requireNonNull(response);
        } catch (DataAccessException exception) {
            throw new DependencyUnavailableException("Product persistence is unavailable");
        }
    }

    private ProductResponse replay(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException();
        }
        return productRepository.findById(record.getProductId())
            .map(product -> toResponse(product, imageRepository.findById(product.getId()).orElse(null)))
            .orElseThrow(ProductNotFoundException::new);
    }

    private String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String key = idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new InvalidRequestException(
                "Idempotency-Key must be 1-128 characters from [A-Za-z0-9._:-]");
        }
        return key;
    }

    private String requestHash(ProductRequest request) {
        BigDecimal price = request.price().setScale(2, RoundingMode.UNNECESSARY);
        String canonical = request.name().trim() + '\u0000' + price.toPlainString() + '\u0000'
            + request.unit().trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private ProductImageResponse toImageResponse(ProductImage image) {
        return new ProductImageResponse(
            storage.displayUrl(image.getObjectKey()),
            image.getAlt(),
            image.getWidth(),
            image.getHeight());
    }

    private ProductResponse toResponse(Product product, ProductImage image) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice(), product.getUnit(),
            image == null ? null : toImageResponse(image));
    }
}
