package com.os.os_orchestrator.adapter.out.database;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(
                        System.getenv().getOrDefault(
                                "DYNAMODB_ENDPOINT",
                                "http://localhost:4566")))
                .region(Region.of(
                        System.getenv().getOrDefault(
                                "AWS_REGION",
                                "us-east-1")))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        System.getenv().getOrDefault(
                                                "AWS_ACCESS_KEY_ID",
                                                "test"),
                                        System.getenv().getOrDefault(
                                                "AWS_SECRET_ACCESS_KEY",
                                                "test"))))
                .build();
    }
}