package com.example.gdprkv.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
public class DynamoConfig {
    @Bean
    public DynamoDbClient dynamo(
            @Value("${app.aws.region}") String region,
            @Value("${app.aws.endpoint}") String endpoint,
            @Value("${app.aws.use-localstack:true}") boolean useLocalstack) {
        var builder = DynamoDbClient.builder().region(Region.of(region));
        if (useLocalstack) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test","test")));
        }
        return builder.build();
    }
}
