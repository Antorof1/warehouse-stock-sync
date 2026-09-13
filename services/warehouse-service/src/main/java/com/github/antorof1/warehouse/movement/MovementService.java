package com.github.antorof1.warehouse.movement;

import org.springframework.stereotype.Service;

@Service
public class MovementService {
    public Movement createMovement() {
        return new Movement();
    }
}
