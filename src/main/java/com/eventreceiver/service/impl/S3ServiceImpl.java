package com.eventreceiver.service.impl;

import com.eventreceiver.config.AppConfig;
import com.eventreceiver.model.Event;
import com.eventreceiver.service.S3Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {
    private final S3Client s3Client;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    /**
     * Stores a batch of events in S3 for a specific customer tier.
     * Events are serialized to JSON and stored with a unique key based on customer tier and timestamp.
     * 
     * @param events List of events to be stored
     * @param customerTier Customer tier for organizing events in S3
     * @throws RuntimeException if there's an error storing events in S3
     */
    @Override
    @Retryable(
        value = {AwsServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void storeEvents(List<Event> events, String customerTier) {
        try {
            String s3Key = generateS3Key(customerTier);
            String jsonContent = objectMapper.writeValueAsString(events);
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(appConfig.getS3().getBucketName())
                .key(s3Key)
                .contentType("application/json")
                .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromString(jsonContent));
            log.info("Successfully stored {} events in S3 with key: {}", events.size(), s3Key);
        } catch (AwsServiceException e) {
            log.error("Failed to store events in S3: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while storing events in S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store events in S3", e);
        }
    }

    /**
     * Generates a unique S3 key for storing events.
     * Format: {customer_tier}/{date}/{uuid}.json
     * Example: premium/2024-03-20/550e8400-e29b-41d4-a716-446655440000.json
     * 
     * @param customerTier Customer tier for the events
     * @return Unique S3 key for storing events
     */
    private String generateS3Key(String customerTier) {
        return String.format("%s/%s/%s.json",
            customerTier,
            Instant.now().toString().substring(0, 10),
                UUID.randomUUID()
        );
    }
} 