package com.eventreceiver.service.impl;

import com.eventreceiver.model.Event;
import com.eventreceiver.service.EventService;
import com.eventreceiver.service.S3Service;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EventServiceImpl implements EventService {
    private final S3Service s3Service;
    private final Map<String, List<Event>> batchBuffer;
    private final Counter batchCounter;
    private final Counter eventCounter;
    private final Timer processingTimer;
    private final Counter errorCounter;
    private ScheduledExecutorService scheduler;

    @Value("${app.batch.max-batch-size-bytes}")
    private long maxBatchSizeBytes;

    @Value("${app.batch.max-batch-delay-seconds}")
    private int maxBatchDelaySeconds;

    public EventServiceImpl(S3Service s3Service, MeterRegistry registry) {
        this.s3Service = s3Service;
        this.batchBuffer = new ConcurrentHashMap<>();
        
        // Initialize metrics
        this.batchCounter = Counter.builder("event.batches.total")
            .description("Total number of batches processed")
            .register(registry);
        log.info("Registered metric: event.batches.total with registry: {}", registry.getClass().getName());
            
        this.eventCounter = Counter.builder("event.processed.total")
            .description("Total number of events processed")
            .register(registry);
        log.info("Registered metric: event.processed.total with registry: {}", registry.getClass().getName());
            
        this.processingTimer = Timer.builder("event.processing.time")
            .description("Time taken to process events")
            .register(registry);
        log.info("Registered metric: event.processing.time with registry: {}", registry.getClass().getName());
            
        this.errorCounter = Counter.builder("event.errors.total")
            .description("Total number of processing errors")
            .register(registry);
        log.info("Registered metric: event.errors.total with registry: {}", registry.getClass().getName());

        // Log all available metrics
        registry.getMeters().forEach(meter -> 
            log.info("Available metric: {} - {}", meter.getId().getName(), meter.getId().getType())
        );
    }

    /**
     * Initializes the batch processing scheduler. Creates a single-threaded scheduler that runs
     * processBatches() every maxBatchDelaySeconds. This ensures events are processed periodically
     * even if they don't reach the size limit.
     */
    @PostConstruct
    public void init() {
        this.scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(
            this::processBatches,
            maxBatchDelaySeconds,
            maxBatchDelaySeconds,
            TimeUnit.SECONDS
        );
        log.info("Initialized batch scheduler with delay: {} seconds", maxBatchDelaySeconds);
    }

    /**
     * Processes an incoming event by adding it to a batch buffer for the specified customer tier.
     * The event is processed asynchronously and will be stored in S3 when either:
     * 1. The batch size reaches maxBatchSizeBytes
     * 2. The scheduled batch processor runs (every maxBatchDelaySeconds)
     * 
     * @param event The event to be processed
     * @param customerTier The customer tier for batch grouping
     */
    @Override
    @Async
    public void processEvent(Event event, String customerTier) {
        processingTimer.record(() -> {
            try {
                List<Event> batch = batchBuffer.computeIfAbsent(customerTier, k -> new ArrayList<>());
                synchronized (batch) {
                    batch.add(event);
                    eventCounter.increment();

                    if (calculateBatchSize(batch) >= maxBatchSizeBytes) {
                        List<Event> batchToProcess = new ArrayList<>(batch);
                        batch.clear();
                        processBatch(customerTier, batchToProcess);
                    }
                }
            } catch (Exception e) {
                errorCounter.increment();
                log.error("Error processing event: {}", e.getMessage());
                throw e;
            }
        });
    }

    /**
     * Periodically checks all customer tiers for pending events and processes them.
     * This method is called by the scheduler at fixed intervals to ensure events
     * are processed even if they don't reach the size limit.
     */
    private void processBatches() {
        for (String tier : batchBuffer.keySet()) {
            List<Event> batchToProcess = null;
            synchronized (batchBuffer) {
                List<Event> currentBatch = batchBuffer.get(tier);
                if (currentBatch != null && !currentBatch.isEmpty()) {
                    batchToProcess = new ArrayList<>(currentBatch);
                    currentBatch.clear();
                }
            }
            
            if (batchToProcess != null) {
                processBatch(tier, batchToProcess);
            }
        }
    }

    /**
     * Processes a batch of events for a specific customer tier by storing them in S3.
     * Increments the batch counter and logs the processing result.
     * 
     * @param tier The customer tier for the batch
     * @param batch The list of events to process
     */
    private void processBatch(String tier, List<Event> batch) {
        try {
            s3Service.storeEvents(batch, tier);
            batchCounter.increment();
            log.info("Processed batch of {} events for tier {}. Current batch count: {}", 
                batch.size(), tier, batchCounter.count());
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error storing batch for tier {}: {}", tier, e.getMessage());
        }
    }

    /**
     * Calculates the approximate size of a batch in bytes.
     * Uses a simple calculation based on event body length plus overhead.
     * 
     * @param batch The list of events to calculate size for
     * @return The approximate size of the batch in bytes
     */
    private long calculateBatchSize(List<Event> batch) {
        return batch.stream()
            .mapToLong(event -> event.getBody().length() + 50) // 50 bytes for timestamp and overhead
            .sum();
    }

    /**
     * Gracefully shuts down the batch scheduler when the application is stopping.
     * Attempts to complete any pending tasks before shutting down.
     * Forces shutdown if tasks don't complete within 5 seconds.
     */
    @PreDestroy
    public void cleanup() {
        if (scheduler != null) {
            log.info("Shutting down batch scheduler");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
} 