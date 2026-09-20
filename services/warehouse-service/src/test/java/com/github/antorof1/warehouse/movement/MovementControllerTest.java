package com.github.antorof1.warehouse.movement;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.warehouse.utils.MovementUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;
import java.util.stream.Stream;

import static com.github.antorof1.warehouse.utils.MovementUtils.MAX_FIELD_LENGTH;
import static com.github.antorof1.warehouse.utils.MovementUtils.SKU;
import static com.github.antorof1.warehouse.utils.MovementUtils.WAREHOUSE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(MovementController.class)
class MovementControllerTest {
    private static final UUID MOVEMENT_ID = UUID.fromString("0199a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MovementService movementService;

    @Test
    void returnsCreatedMovement() {
        given(movementService.createMovement(SKU, WAREHOUSE_ID, 5, MovementReason.RECEIPT))
            .willReturn(MovementUtils.savedMovement(MOVEMENT_ID));

        MvcTestResult result = post(body(quoted(SKU), quoted(WAREHOUSE_ID), "5", quoted("RECEIPT")));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(MOVEMENT_ID.toString());
        assertThat(result).bodyJson().extractingPath("$.sku").isEqualTo(SKU);
        assertThat(result).bodyJson().extractingPath("$.warehouseId").isEqualTo(WAREHOUSE_ID);
        assertThat(result).bodyJson().extractingPath("$.quantityDelta").isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo("RECEIPT");
        assertThat(result).bodyJson().extractingPath("$.occurredAt").isNotNull();
    }

    @Test
    void acceptsFieldsAtMaximumLength() {
        String sku = MovementUtils.ofLength('S', MAX_FIELD_LENGTH);
        String warehouseId = MovementUtils.ofLength('W', MAX_FIELD_LENGTH);
        given(movementService.createMovement(anyString(), anyString(), anyInt(), any(MovementReason.class)))
            .willReturn(MovementUtils.savedMovement(MOVEMENT_ID));

        MvcTestResult result = post(body(quoted(sku), quoted(warehouseId), "5", quoted("RECEIPT")));

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void acceptsNegativeQuantityDelta() {
        given(movementService.createMovement(SKU, WAREHOUSE_ID, -3, MovementReason.PICK))
            .willReturn(MovementUtils.savedMovement(MOVEMENT_ID));

        MvcTestResult result = post(body(quoted(SKU), quoted(WAREHOUSE_ID), "-3", quoted("PICK")));

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPayloads")
    void rejectsInvalidPayload(String description, String payload) {
        MvcTestResult result = post(payload);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(movementService);
    }

    @Test
    void reportsValidationFailureAsProblemDetail() {
        MvcTestResult result = post(body(quoted(""), quoted(WAREHOUSE_ID), "5", quoted("RECEIPT")));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
    }

    @Test
    void rejectsUnsupportedContentType() {
        MvcTestResult result = mvc.post()
            .uri("/api/v1/movements")
            .contentType(MediaType.TEXT_PLAIN)
            .content("sku=SKU-1001")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        verifyNoInteractions(movementService);
    }

    static Stream<Arguments> invalidPayloads() {
        String tooLong = MovementUtils.ofLength('X', MAX_FIELD_LENGTH + 1);

        return Stream.of(
            arguments("blank sku", body(quoted(""), quoted(WAREHOUSE_ID), "5", quoted("RECEIPT"))),
            arguments("missing sku", body("null", quoted(WAREHOUSE_ID), "5", quoted("RECEIPT"))),
            arguments("whitespace sku", body(quoted("   "), quoted(WAREHOUSE_ID), "5", quoted("RECEIPT"))),
            arguments("sku over 64 characters", body(quoted(tooLong), quoted(WAREHOUSE_ID), "5", quoted("RECEIPT"))),
            arguments("blank warehouse id", body(quoted(SKU), quoted(""), "5", quoted("RECEIPT"))),
            arguments("missing warehouse id", body(quoted(SKU), "null", "5", quoted("RECEIPT"))),
            arguments("warehouse id over 64 characters", body(quoted(SKU), quoted(tooLong), "5", quoted("RECEIPT"))),
            arguments("missing quantity delta", body(quoted(SKU), quoted(WAREHOUSE_ID), "null", quoted("RECEIPT"))),
            arguments("non numeric quantity delta", body(quoted(SKU), quoted(WAREHOUSE_ID), quoted("five"), quoted("RECEIPT"))),
            arguments("missing reason", body(quoted(SKU), quoted(WAREHOUSE_ID), "5", "null")),
            arguments("unknown reason", body(quoted(SKU), quoted(WAREHOUSE_ID), "5", quoted("SHRINKAGE"))),
            arguments("several violations at once", body(quoted(""), "null", "null", "null")),
            arguments("malformed json", "{\"sku\": "),
            arguments("empty body", "")
        );
    }

    private MvcTestResult post(String payload) {
        return mvc.post()
            .uri("/api/v1/movements")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload)
            .exchange();
    }

    private static String body(String sku, String warehouseId, String quantityDelta, String reason) {
        return """
            {"sku": %s, "warehouseId": %s, "quantityDelta": %s, "reason": %s}""".formatted(sku, warehouseId, quantityDelta, reason);
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }
}
