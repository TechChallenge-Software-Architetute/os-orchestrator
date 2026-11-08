package com.os.os_orchestrator.adapter.out.database;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

@Configuration
public class DynamoDbConfig {

    /**
     * LocalStack endpoint. Set only for local development (docker-compose sets it);
     * left blank everywhere else so the SDK resolves the real regional endpoint.
     */
    private final String endpoint;

    private final String region;

    public DynamoDbConfig(@Value("${app.aws.dynamodb.endpoint:}") String endpoint,
                          @Value("${app.aws.region:us-east-1}") String region) {
        this.endpoint = endpoint;
        this.region = region;
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region));

        // Credentials always come from the default provider chain (container/pod
        // environment, instance profile, or IRSA once the cluster has it). Static
        // keys are never compiled in — the old "test"/"test" defaults silently
        // became the production path if the env vars were missing.
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }
}
