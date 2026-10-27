package com.github.antorof1.reorder.rule;

import com.github.antorof1.reorder.exception.ReorderRuleNotFoundException;
import com.github.antorof1.reorder.utils.ReorderUtils;
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.github.antorof1.reorder.utils.ReorderUtils.SKU;
import static com.github.antorof1.reorder.utils.ReorderUtils.WAREHOUSE_ID;
import static com.github.antorof1.reorder.utils.ReorderUtils.rule;
import static com.github.antorof1.reorder.utils.ReorderUtils.ruleId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(ReorderRuleController.class)
class ReorderRuleControllerTest {
    private static final String RULE_URI = "/api/v1/reorder-rules/{warehouseId}/{sku}";
    private static final int MAX_FIELD_LENGTH = 64;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ReorderRuleService reorderRuleService;

    @Test
    void listsRules() {
        given(reorderRuleService.findAll()).willReturn(List.of(rule(SKU, 10, 50), rule("SKU-2002", 3, 12)));

        MvcTestResult result = mvc.get().uri("/api/v1/reorder-rules").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$[0].sku").isEqualTo(SKU);
        assertThat(result).bodyJson().extractingPath("$[0].warehouseId").isEqualTo(WAREHOUSE_ID);
        assertThat(result).bodyJson().extractingPath("$[0].reorderPoint").isEqualTo(10);
        assertThat(result).bodyJson().extractingPath("$[0].reorderUpTo").isEqualTo(50);
        assertThat(result).bodyJson().extractingPath("$[1].sku").isEqualTo("SKU-2002");
    }

    @Test
    void listsNoRulesAsAnEmptyArray() {
        given(reorderRuleService.findAll()).willReturn(List.of());

        MvcTestResult result = mvc.get().uri("/api/v1/reorder-rules").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isEqualTo("[]");
    }

    @Test
    void returnsASingleRule() {
        given(reorderRuleService.find(ruleId(SKU))).willReturn(Optional.of(rule(SKU, 10, 50)));

        MvcTestResult result = mvc.get().uri(RULE_URI, WAREHOUSE_ID, SKU).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.sku").isEqualTo(SKU);
        assertThat(result).bodyJson().extractingPath("$.warehouseId").isEqualTo(WAREHOUSE_ID);
        assertThat(result).bodyJson().extractingPath("$.reorderPoint").isEqualTo(10);
        assertThat(result).bodyJson().extractingPath("$.reorderUpTo").isEqualTo(50);
    }

    @Test
    void reportsAMissingRuleAsProblemDetail() {
        given(reorderRuleService.find(ruleId(SKU))).willReturn(Optional.empty());

        MvcTestResult result = mvc.get().uri(RULE_URI, WAREHOUSE_ID, SKU).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(404);
    }

    @Test
    void returnsCreatedWhenTheRuleIsNew() {
        given(reorderRuleService.save(ruleId(SKU), 10, 50)).willReturn(true);

        MvcTestResult result = put(SKU, body("10", "50"));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.sku").isEqualTo(SKU);
        assertThat(result).bodyJson().extractingPath("$.warehouseId").isEqualTo(WAREHOUSE_ID);
        assertThat(result).bodyJson().extractingPath("$.reorderPoint").isEqualTo(10);
        assertThat(result).bodyJson().extractingPath("$.reorderUpTo").isEqualTo(50);
    }

    @Test
    void returnsOkWhenTheRuleIsUpdated() {
        given(reorderRuleService.save(ruleId(SKU), 5, 20)).willReturn(false);

        MvcTestResult result = put(SKU, body("5", "20"));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.reorderPoint").isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.reorderUpTo").isEqualTo(20);
    }

    @Test
    void acceptsAZeroReorderPoint() {
        given(reorderRuleService.save(ruleId(SKU), 0, 1)).willReturn(true);

        MvcTestResult result = put(SKU, body("0", "1"));

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void acceptsAPathSkuAtMaximumLength() {
        String sku = ReorderUtils.ofLength('S', MAX_FIELD_LENGTH);
        given(reorderRuleService.save(ruleId(sku), 10, 50)).willReturn(true);

        MvcTestResult result = put(sku, body("10", "50"));

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPayloads")
    void rejectsInvalidPayload(String description, String payload) {
        MvcTestResult result = put(SKU, payload);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(reorderRuleService);
    }

    @Test
    void reportsValidationFailureAsProblemDetail() {
        MvcTestResult result = put(SKU, body("50", "10"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
    }

    @Test
    void rejectsAnOverlongPathSku() {
        MvcTestResult result = put(ReorderUtils.ofLength('S', MAX_FIELD_LENGTH + 1), body("10", "50"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(reorderRuleService);
    }

    @Test
    void rejectsAnOverlongPathWarehouseId() {
        MvcTestResult result = mvc.put()
            .uri(RULE_URI, ReorderUtils.ofLength('W', MAX_FIELD_LENGTH + 1), SKU)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("10", "50"))
            .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(reorderRuleService);
    }

    @Test
    void rejectsUnsupportedContentType() {
        MvcTestResult result = mvc.put()
            .uri(RULE_URI, WAREHOUSE_ID, SKU)
            .contentType(MediaType.TEXT_PLAIN)
            .content("reorderPoint=10")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        verifyNoInteractions(reorderRuleService);
    }

    @Test
    void deletesARule() {
        MvcTestResult result = mvc.delete().uri(RULE_URI, WAREHOUSE_ID, SKU).exchange();

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(reorderRuleService).delete(ruleId(SKU));
    }

    @Test
    void reportsDeletingAMissingRuleAsNotFound() {
        willThrow(new ReorderRuleNotFoundException(ruleId(SKU))).given(reorderRuleService).delete(ruleId(SKU));

        MvcTestResult result = mvc.delete().uri(RULE_URI, WAREHOUSE_ID, SKU).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    static Stream<Arguments> invalidPayloads() {
        return Stream.of(
            arguments("missing reorder point", body("null", "50")),
            arguments("negative reorder point", body("-1", "50")),
            arguments("non numeric reorder point", body("\"ten\"", "50")),
            arguments("missing reorder up to", body("10", "null")),
            arguments("zero reorder up to", body("0", "0")),
            arguments("reorder up to below reorder point", body("50", "10")),
            arguments("reorder up to equal to reorder point", body("10", "10")),
            arguments("several violations at once", body("null", "null")),
            arguments("malformed json", "{\"reorderPoint\": "),
            arguments("empty body", "")
        );
    }

    private MvcTestResult put(String sku, String payload) {
        return mvc.put()
            .uri(RULE_URI, WAREHOUSE_ID, sku)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload)
            .exchange();
    }

    private static String body(String reorderPoint, String reorderUpTo) {
        return """
            {"reorderPoint": %s, "reorderUpTo": %s}""".formatted(reorderPoint, reorderUpTo);
    }
}
