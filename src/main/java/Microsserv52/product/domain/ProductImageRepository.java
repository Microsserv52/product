package Microsserv52.product.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
}
