package com.github.antorof1.reorder.rule;

import com.github.antorof1.reorder.exception.ReorderRuleNotFoundException;
import com.github.antorof1.reorder.rule.dto.ReorderRuleResponse;
import com.github.antorof1.reorder.rule.dto.UpsertReorderRuleRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reorder-rules")
public class ReorderRuleController {
    private final ReorderRuleService reorderRuleService;

    public ReorderRuleController(ReorderRuleService reorderRuleService) {
        this.reorderRuleService = reorderRuleService;
    }

    @GetMapping
    public List<ReorderRuleResponse> listRules() {
        return reorderRuleService.findAll().stream()
            .map(ReorderRuleResponse::fromEntity)
            .toList();
    }

    @GetMapping("/{warehouseId}/{sku}")
    public ReorderRuleResponse getRule(
        @PathVariable("warehouseId") @NotBlank @Size(max = 64, message = "Warehouse ID is too long") String warehouseId,
        @PathVariable("sku") @NotBlank @Size(max = 64, message = "SKU is too long") String sku
    ) {
        ReorderRuleId id = new ReorderRuleId(sku, warehouseId);

        return reorderRuleService.find(id)
            .map(ReorderRuleResponse::fromEntity)
            .orElseThrow(() -> new ReorderRuleNotFoundException(id));
    }

    @PutMapping("/{warehouseId}/{sku}")
    public ResponseEntity<ReorderRuleResponse> upsertRule(
        @PathVariable("warehouseId") @NotBlank @Size(max = 64, message = "Warehouse ID is too long") String warehouseId,
        @PathVariable("sku") @NotBlank @Size(max = 64, message = "SKU is too long") String sku,
        @RequestBody @Valid UpsertReorderRuleRequest request
    ) {
        boolean created = reorderRuleService.save(
            new ReorderRuleId(sku, warehouseId),
            request.reorderPoint(),
            request.reorderUpTo()
        );

        ReorderRuleResponse response = new ReorderRuleResponse(
            sku,
            warehouseId,
            request.reorderPoint(),
            request.reorderUpTo()
        );

        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }

    @DeleteMapping("/{warehouseId}/{sku}")
    public ResponseEntity<Void> deleteRule(
        @PathVariable("warehouseId") @NotBlank @Size(max = 64, message = "Warehouse ID is too long") String warehouseId,
        @PathVariable("sku") @NotBlank @Size(max = 64, message = "SKU is too long") String sku
    ) {
        reorderRuleService.delete(new ReorderRuleId(sku, warehouseId));

        return ResponseEntity.noContent().build();
    }
}
