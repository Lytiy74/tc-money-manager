package org.tc.mtracker.utils.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        String region,
        String accessKeyId,
        String secretAccessKey,
        S3 s3
) {
    public record S3(String bucketName) {
    }
}
