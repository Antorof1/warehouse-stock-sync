package com.github.antorof1.simulator.state;

import com.github.antorof1.simulator.client.ReorderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class ReorderRuleSync {
    private static final Logger log = LoggerFactory.getLogger(ReorderRuleSync.class);

    private final ReorderClient reorderClient;
    private final SimulationState state;

    public ReorderRuleSync(ReorderClient reorderClient, SimulationState state) {
        this.reorderClient = reorderClient;
        this.state = state;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 1000)
    public void sync() {
        try {
            state.replaceRules(reorderClient.listRules());
        } catch (RestClientException e) {
            log.debug("Can't load reorder rules: {}", e.getMessage());
        }
    }
}
