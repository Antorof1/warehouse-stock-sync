package com.github.antorof1.warehouse.movement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

public interface MovementRepository extends JpaRepository<Movement, UUID> {
}
