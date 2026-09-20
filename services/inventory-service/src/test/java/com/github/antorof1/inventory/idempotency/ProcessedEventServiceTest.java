package com.github.antorof1.inventory.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static com.github.antorof1.inventory.utils.StockUtils.newEventId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProcessedEventServiceTest {
    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private ProcessedEventService processedEventService;

    @Captor
    private ArgumentCaptor<ProcessedEvent> processedEventCaptor;

    @Test
    void reportsAKnownEventAsProcessed() {
        UUID id = newEventId();
        given(processedEventRepository.existsById(id)).willReturn(true);

        assertThat(processedEventService.isProcessed(id)).isTrue();
    }

    @Test
    void reportsAnUnknownEventAsNotProcessed() {
        UUID id = newEventId();
        given(processedEventRepository.existsById(id)).willReturn(false);

        assertThat(processedEventService.isProcessed(id)).isFalse();
    }

    @Test
    void storesTheEventIdWhenMarkingItProcessed() {
        UUID id = newEventId();

        processedEventService.markProcessed(id);

        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertThat(processedEventCaptor.getValue().getId()).isEqualTo(id);
    }
}
