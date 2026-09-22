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

    @Transactional
    public boolean tryMarkProcessed(UUID eventId, String eventTopic) {
        return processedEventRepository.insertIgnoreConflict(eventId, eventTopic) > 0;
    }
}
