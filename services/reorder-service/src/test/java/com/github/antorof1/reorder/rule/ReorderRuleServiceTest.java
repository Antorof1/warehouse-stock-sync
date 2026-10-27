package com.github.antorof1.reorder.rule;

import com.github.antorof1.reorder.exception.ReorderRuleNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.github.antorof1.reorder.utils.ReorderUtils.SKU;
import static com.github.antorof1.reorder.utils.ReorderUtils.rule;
import static com.github.antorof1.reorder.utils.ReorderUtils.ruleId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReorderRuleServiceTest {
    @Mock
    private ReorderRuleRepository reorderRuleRepository;

    @InjectMocks
    private ReorderRuleService reorderRuleService;

    @Test
    void createsARuleThatDoesNotExistYet() {
        given(reorderRuleRepository.findAndLockById(ruleId(SKU))).willReturn(Optional.empty());

        boolean created = reorderRuleService.save(ruleId(SKU), 10, 50);

        assertThat(created).isTrue();
        ArgumentCaptor<ReorderRule> captor = ArgumentCaptor.forClass(ReorderRule.class);
        verify(reorderRuleRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(ruleId(SKU));
        assertThat(captor.getValue().getReorderPoint()).isEqualTo(10);
        assertThat(captor.getValue().getReorderUpTo()).isEqualTo(50);
    }

    @Test
    void updatesTheLockedExistingRuleInPlace() {
        ReorderRule existing = rule(SKU, 10, 50);
        given(reorderRuleRepository.findAndLockById(ruleId(SKU))).willReturn(Optional.of(existing));

        boolean created = reorderRuleService.save(ruleId(SKU), 5, 20);

        assertThat(created).isFalse();
        assertThat(existing.getReorderPoint()).isEqualTo(5);
        assertThat(existing.getReorderUpTo()).isEqualTo(20);
        verify(reorderRuleRepository, never()).save(any());
    }

    @Test
    void rejectsATargetThatIsNotAboveTheReorderPoint() {
        assertThatThrownBy(() -> reorderRuleService.save(ruleId(SKU), 10, 10))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(reorderRuleRepository);
    }

    @Test
    void deletesTheLockedExistingRule() {
        ReorderRule existing = rule(SKU);
        given(reorderRuleRepository.findAndLockById(ruleId(SKU))).willReturn(Optional.of(existing));

        reorderRuleService.delete(ruleId(SKU));

        verify(reorderRuleRepository).delete(existing);
    }

    @Test
    void refusesToDeleteAMissingRule() {
        given(reorderRuleRepository.findAndLockById(ruleId(SKU))).willReturn(Optional.empty());

        assertThatThrownBy(() -> reorderRuleService.delete(ruleId(SKU)))
            .isInstanceOf(ReorderRuleNotFoundException.class);

        verify(reorderRuleRepository, never()).delete(any());
    }
}
