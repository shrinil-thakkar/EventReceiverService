package com.eventreceiver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import java.time.Duration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    private List<String> allowedCustomerTiers;
    private S3Config s3;
    private BatchConfig batch;

    @Data
    public static class S3Config {
        private String bucketName;
        private String region;
        private String accessKey;
        private String secretKey;
    }

    @Data
    public static class BatchConfig {
        private int maxBatchSizeBytes = 5 * 1024 * 1024; // 5MB
        private int maxBatchDelaySeconds = 5;
    }

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey());
        return S3Client.builder()
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .httpClient(ApacheHttpClient.builder()
                    .connectionTimeout(Duration.ofSeconds(5))
                    .socketTimeout(Duration.ofSeconds(5))
                    .build())
                .build();
    }

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            log.info("Verifying S3 bucket access...");
            log.info("Bucket: {}, Region: {}", s3.getBucketName(), s3.getRegion());
            
            s3Client().headBucket(builder -> builder.bucket(s3.getBucketName()).build());
            log.info("Successfully connected to S3 bucket: {}", s3.getBucketName());
        } catch (Exception e) {
            log.error("Failed to access S3 bucket: {}. Error: {}", s3.getBucketName(), e.getMessage());
            throw new RuntimeException("Failed to access S3 bucket", e);
        }
    }
} 