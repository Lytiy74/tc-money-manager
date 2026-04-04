package org.tc.mtracker.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.utils.config.properties.AwsProperties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final Duration PRESIGNED_URL_TTL = Duration.ofMinutes(60);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsProperties awsProperties;


    public void saveFile(String objectKey, MultipartFile file) {
        PutObjectRequest putObjectRequest = buildPutObjectRequest(objectKey, file);

        try {
            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload file to S3. key=" + objectKey, e);
        }
    }


    public String generatePresignedUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }

        GetObjectRequest getObjectRequest = buildGetObjectRequest(objectKey);

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_TTL)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    public void deleteFile(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName())
                .key(objectKey)
                .build());
    }

    private PutObjectRequest buildPutObjectRequest(String objectKey, MultipartFile file) {
        return PutObjectRequest.builder()
                .bucket(bucketName())
                .key(objectKey)
                .contentType(file.getContentType())
                .build();
    }

    private GetObjectRequest buildGetObjectRequest(String objectKey) {
        return GetObjectRequest.builder()
                .bucket(bucketName())
                .key(objectKey)
                .build();
    }

    private String bucketName() {
        return awsProperties.s3().bucketName();
    }
}
