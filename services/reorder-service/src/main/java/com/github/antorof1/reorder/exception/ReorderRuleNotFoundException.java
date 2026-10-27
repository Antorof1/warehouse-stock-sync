package com.github.antorof1.reorder.exception;

import com.github.antorof1.reorder.rule.ReorderRuleId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ReorderRuleNotFoundException extends ErrorResponseException {
    public ReorderRuleNotFoundException(ReorderRuleId id) {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "No reorder rule for SKU %s in warehouse %s".formatted(id.sku(), id.warehouseId())
        ), null);
    }
}
