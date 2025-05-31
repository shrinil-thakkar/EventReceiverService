package com.eventreceiver.controller;

import com.eventreceiver.config.AppConfig;
import com.eventreceiver.model.Event;
import com.eventreceiver.service.EventService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class EventController {
    private static final String CUSTOMER_TIER_HEADER = "X-Customer-Tier";
    private final AppConfig appConfig;
    private final EventService eventService;
    private final Counter requestCounter;
    private final Counter filteredRequestCounter;

    public EventController(AppConfig appConfig, EventService eventService, MeterRegistry registry) {
        this.appConfig = appConfig;
        this.eventService = eventService;
        this.requestCounter = Counter.builder("event.requests.total")
                .description("Total number of event requests received")
                .register(registry);
        this.filteredRequestCounter = Counter.builder("event.requests.filtered")
                .description("Number of filtered event requests")
                .register(registry);
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, String>> ingestEvent(
            @Valid @RequestBody Event event,
            @RequestHeader(CUSTOMER_TIER_HEADER) String customerTier) {
        try {
            // count total requests
            requestCounter.increment();

            // check if customer tier is allowed
            if (!appConfig.getAllowedCustomerTiers().contains(customerTier)) {
                log.warn("Rejected event from unauthorized customer tier: {}", customerTier);
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Unauthorized customer tier"));
            }
            // count total requests
            filteredRequestCounter.increment();
            log.info("Received event from customer tier: {}", customerTier);

            // process event
            eventService.processEvent(event, customerTier);

            return ResponseEntity.accepted().body(Map.of(
                "status", "success",
                "message", "Event accepted"));
        } catch (Exception e) {
            log.error("Error processing event: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
} 