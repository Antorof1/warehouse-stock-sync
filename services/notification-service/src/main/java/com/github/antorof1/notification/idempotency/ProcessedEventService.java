package com.github.antorof1.notification.idempotency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProcessedEventService {
    private final ProcessedEventRepository processedEventRepository;

    public ProcessedEventService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    public boolean isProcessed(UUID eventId, String eventTopic) {
        return processedEventRepository.existsById(new ProcessedEventId(eventId, eventTopic));
    }

    @Transactional
    public void markProcessed(UUID eventId, String eventTopic) {
        ProcessedEvent event = new ProcessedEvent(new ProcessedEventId(eventId, eventTopic));

        processedEventRepository.save(event);
    }
}
