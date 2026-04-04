package org.tc.mtracker.utils.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tc.mtracker.utils.config.properties.AwsProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(AwsProperties.class)
public class S3Config {

    private final AwsProperties awsProperties;

    public S3Config(AwsProperties awsProperties) {
        this.awsProperties = awsProperties;
    }

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(awsProperties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(awsProperties.accessKeyId(), awsProperties.secretAccessKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(awsProperties.s3().pathStyleAccessEnabled()).build());

        if (awsProperties.endpoint() != null && !awsProperties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(awsProperties.endpoint()));
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(awsProperties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(awsProperties.accessKeyId(), awsProperties.secretAccessKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(awsProperties.s3().pathStyleAccessEnabled())
                        .build());

        if (awsProperties.endpoint() != null && !awsProperties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(awsProperties.endpoint()));
        }

        return builder.build();
    }
}
