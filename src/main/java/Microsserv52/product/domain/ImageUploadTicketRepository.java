package Microsserv52.product.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageUploadTicketRepository extends JpaRepository<ImageUploadTicket, UUID> {
    List<ImageUploadTicket> findByExpiresAtBefore(Instant instant);
}
