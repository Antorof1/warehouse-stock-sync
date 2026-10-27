package com.github.antorof1.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static com.github.antorof1.idempotency.utils.IdempotencyUtils.TOPIC;
import static com.github.antorof1.idempotency.utils.IdempotencyUtils.newEventId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProcessedEventServiceTest {
    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private ProcessedEventService processedEventService;

    @Test
    void claimsAnEventWhoseIdWasInserted() {
        UUID id = newEventId();
        given(processedEventRepository.insertIgnoreConflict(id, TOPIC)).willReturn(1);

        assertThat(processedEventService.tryMarkProcessed(id, TOPIC)).isTrue();
    }

    @Test
    void doesNotClaimAnEventWhoseIdWasAlreadyStored() {
        UUID id = newEventId();
        given(processedEventRepository.insertIgnoreConflict(id, TOPIC)).willReturn(0);

        assertThat(processedEventService.tryMarkProcessed(id, TOPIC))
            .as("the insert lost the race, so another consumer owns this event")
            .isFalse();
    }
}
