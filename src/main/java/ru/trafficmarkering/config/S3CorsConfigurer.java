package ru.trafficmarkering.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3CorsConfigurer {

    private final S3Client s3Client;

    @Value("${s3.cors.auto}")
    private boolean autoConfigure;

    @Value("${s3.bucket}")
    private String bucket;

    @Value("${allowed.origins}")
    private List<String> allowedOrigins;

    @EventListener(ApplicationReadyEvent.class)
    public void applyCors() {
        if (!autoConfigure) {
            return;
        }
        CORSRule rule = CORSRule.builder()
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "PUT", "HEAD")
                .allowedHeaders("*")
                .exposeHeaders("ETag")
                .maxAgeSeconds(3600)
                .build();
        try {
            s3Client.putBucketCors(PutBucketCorsRequest.builder()
                    .bucket(bucket)
                    .corsConfiguration(CORSConfiguration.builder().corsRules(rule).build())
                    .build());
            log.info("CORS бакета {} настроен для {}", bucket, allowedOrigins);
        } catch (Exception e) {
            log.warn("Не удалось настроить CORS бакета {}: {} — пропишите правила вручную в консоли",
                    bucket, e.getMessage());
        }
    }
}
