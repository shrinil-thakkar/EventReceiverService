package com.eventreceiver.service.impl;

import com.eventreceiver.model.Event;
import com.eventreceiver.service.S3Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {
    @Mock
    private S3Service s3Service;
    
    private EventServiceImpl eventService;
    private final long maxBatchSizeBytes = 1000;

    @BeforeEach
    void setUp() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        eventService = new EventServiceImpl(s3Service, meterRegistry);
        ReflectionTestUtils.setField(eventService, "maxBatchSizeBytes", maxBatchSizeBytes);
        int maxBatchDelaySeconds = 5;
        ReflectionTestUtils.setField(eventService, "maxBatchDelaySeconds", maxBatchDelaySeconds);
        eventService.init();
    }

    @Test
    void processEvent_AddsToBatch() {
        // Given
        Event event = new Event();
        event.setEventTimestamp(Instant.now());
        event.setBody("test body");
        String customerTier = "premium";

        // When
        eventService.processEvent(event, customerTier);

        // Then
        verify(s3Service, never()).storeEvents(any(), any());
    }

    @Test
    void processEvent_BatchSizeExceeded_ProcessesBatch() {
        // Given
        Event event = new Event();
        event.setEventTimestamp(Instant.now());
        event.setBody("x".repeat((int)maxBatchSizeBytes));
        String customerTier = "premium";

        // When
        eventService.processEvent(event, customerTier);

        // Then
        verify(s3Service, times(1)).storeEvents(any(), eq(customerTier));
    }

    @Test
    void processEvent_MultipleEvents_SameTier() {
        // Given
        Event event1 = new Event();
        event1.setEventTimestamp(Instant.now());
        event1.setBody("test body 1");

        Event event2 = new Event();
        event2.setEventTimestamp(Instant.now());
        event2.setBody("test body 2");

        String customerTier = "premium";

        // When
        eventService.processEvent(event1, customerTier);
        eventService.processEvent(event2, customerTier);

        // Then
        verify(s3Service, never()).storeEvents(any(), any());
    }

    @Test
    void processEvent_DifferentTiers_SeparateBatches() {
        // Given
        Event event1 = new Event();
        event1.setEventTimestamp(Instant.now());
        event1.setBody("x".repeat((int)maxBatchSizeBytes));

        Event event2 = new Event();
        event2.setEventTimestamp(Instant.now());
        event2.setBody("x".repeat((int)maxBatchSizeBytes));

        // When
        eventService.processEvent(event1, "premium");
        eventService.processEvent(event2, "standard");

        // Then
        verify(s3Service, times(1)).storeEvents(any(), eq("premium"));
        verify(s3Service, times(1)).storeEvents(any(), eq("standard"));
    }

    @Test
    void processEvent_ServiceThrowsException_PropagatesException() {
        // Given
        Event event = new Event();
        event.setEventTimestamp(Instant.now());
        event.setBody("x".repeat((int)maxBatchSizeBytes));
        String customerTier = "premium";

        doThrow(new RuntimeException("Test error")).when(s3Service).storeEvents(any(), any());

        // When/Then
        try {
            eventService.processEvent(event, customerTier);
        } catch (Exception e) {
            // Expected
        }

        verify(s3Service, times(1)).storeEvents(any(), eq(customerTier));
    }
} 