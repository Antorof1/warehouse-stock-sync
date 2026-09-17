package com.github.antorof1.reorder.idempotency;

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

    public boolean isProcessed(UUID id) {
        return processedEventRepository.existsById(id);
    }

    @Transactional
    public void markProcessed(UUID id) {
        ProcessedEvent event = new ProcessedEvent(id);

        processedEventRepository.save(event);
    }
}
