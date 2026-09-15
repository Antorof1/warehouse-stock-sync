package com.github.antorof1.warehouse.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class MovementService {
    private final MovementRepository movementRepository;
    private final ApplicationEventPublisher publisher;

    public MovementService(MovementRepository movementRepository, ApplicationEventPublisher publisher) {
        this.movementRepository = movementRepository;
        this.publisher = publisher;
    }

    @Transactional
    public Movement createMovement(String sku, String warehouseId, Integer quantityDelta, MovementReason reason) {
        Instant occurredAt = Instant.now();

        Movement movement = new Movement(sku, warehouseId, quantityDelta, reason, occurredAt);

        Movement saved = movementRepository.save(movement);

        MovementEvent event = new MovementEvent(
            saved.getId(),
            saved.getSku(),
            saved.getWarehouseId(),
            saved.getQuantityDelta(),
            saved.getReason(),
            saved.getOccurredAt()
        );

        publisher.publishEvent(event);

        return saved;
    }
}
