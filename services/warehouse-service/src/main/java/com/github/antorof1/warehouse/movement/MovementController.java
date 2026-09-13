package com.github.antorof1.warehouse.movement;

import com.github.antorof1.warehouse.movement.dto.CreateMovementRequest;
import com.github.antorof1.warehouse.movement.dto.MovementResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movements")
public class MovementController {
    private final MovementService movementService;

    public MovementController(MovementService movementService) {
        this.movementService = movementService;
    }

    @PostMapping
    public ResponseEntity<MovementResponse> createMovement(@RequestBody CreateMovementRequest request) {
        Movement movement = movementService.createMovement(
            request.sku(),
            request.warehouseId(),
            request.quantityDelta(),
            request.reason()
        );

        MovementResponse response = MovementResponse.fromEntity(movement);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
