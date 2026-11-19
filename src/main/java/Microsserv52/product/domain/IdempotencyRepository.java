package Microsserv52.product.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, java.util.UUID> {
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}
