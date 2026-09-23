package com.github.antorof1.warehouse.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.Topics;
import com.github.antorof1.warehouse.outbox.OutboxEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class MovementService {
    private final MovementRepository movementRepository;
    private final OutboxEventService outboxEventService;

    public MovementService(MovementRepository movementRepository, OutboxEventService outboxEventService) {
        this.movementRepository = movementRepository;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public Movement createMovement(String sku, String warehouseId, Integer quantityDelta, MovementReason reason) {
        if (quantityDelta == 0) throw new IllegalArgumentException("Quantity delta can't be zero");

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

        outboxEventService.record(saved.getId(), Topics.MOVEMENT_EVENTS, sku, event);

        return saved;
    }
}
