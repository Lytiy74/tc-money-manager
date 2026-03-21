package org.tc.mtracker.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.common.enums.MoneyFlowType;
import org.tc.mtracker.transaction.dto.TransactionCreateRequestDTO;
import org.tc.mtracker.utils.S3Service;
import org.tc.mtracker.utils.TestHelpers;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.secret-key=aZbYcXdWeVfUgThSiRjQkPlOmNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRr",
                "security.jwt.expiration-time=900",
                "security.jwt.test-expiration=60",
                "aws.region=us-central-1",
                "aws.s3.bucket-name=test-bucket",
                "aws.access-key-id=test-key-id",
                "aws.secret-access-key=test-secret"
        }
)
@Testcontainers
@Sql(value = "/datasets/test_categories.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = "/datasets/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TransactionControllerTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:8.3.0");

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private TestHelpers testHelpers;

    @MockitoBean
    private S3Service s3Service;

    private String authToken;

    @BeforeEach
    void setUp() {
        authToken = "Bearer " + testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);
        Mockito.when(s3Service.generatePresignedUrl(anyString())).thenReturn("https://example.com/test-receipt.jpg");
    }

    @Test
    void shouldReturn201WhenTransactionIsCreatedWithoutReceipts() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", buildValidDto(), MediaType.APPLICATION_JSON);

        restTestClient.post()
                .uri("/api/v1/transactions")
                .body(multipartBodyBuilder.build())
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.receiptsUrls.length()").isEqualTo(0);

        verifyNoInteractions(s3Service);
    }

    @Test
    void shouldReturn201WhenTransactionIsCreatedWithReceiptInJPEG() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", buildValidDto(), MediaType.APPLICATION_JSON);
        multipartBodyBuilder.part("receipts", buildResource("test-receipt1.jpg", "jpg-data"), MediaType.IMAGE_JPEG);

        restTestClient.post()
                .uri("/api/v1/transactions")
                .body(multipartBodyBuilder.build())
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.receiptsUrls.length()").isEqualTo(1);

        verify(s3Service, times(1)).saveFile(anyString(), any(MultipartFile.class));
        verify(s3Service, times(1)).generatePresignedUrl(anyString());
    }

    @Test
    void shouldReturn201WhenTransactionIsCreatedWithReceiptInPDF() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", buildValidDto(), MediaType.APPLICATION_JSON);
        multipartBodyBuilder.part(
                "receipts",
                buildResource("test-receipt1.pdf", "%PDF-1.7 test document"),
                MediaType.APPLICATION_PDF
        );

        restTestClient.post()
                .uri("/api/v1/transactions")
                .body(multipartBodyBuilder.build())
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.receiptsUrls.length()").isEqualTo(1);

        verify(s3Service, times(1)).saveFile(anyString(), any(MultipartFile.class));
        verify(s3Service, times(1)).generatePresignedUrl(anyString());
    }

    @Test
    void shouldReturn400WhenReceiptFormatIsInvalid() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", buildValidDto(), MediaType.APPLICATION_JSON);
        multipartBodyBuilder.part(
                "receipts",
                buildResource("test-receipt1.txt", "plain-text"),
                MediaType.TEXT_PLAIN
        );

        restTestClient.post()
                .uri("/api/v1/transactions")
                .body(multipartBodyBuilder.build())
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(s3Service);
    }

    @Test
    void shouldReturn400WhenReceiptExtensionIsInvalidEvenWithValidMimeType() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", buildValidDto(), MediaType.APPLICATION_JSON);
        multipartBodyBuilder.part(
                "receipts",
                buildResource("test-receipt1.txt", "fake-jpeg-data"),
                MediaType.IMAGE_JPEG
        );

        restTestClient.post()
                .uri("/api/v1/transactions")
                .body(multipartBodyBuilder.build())
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(s3Service);
    }

    @Test
    @SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
    @Sql(statements = "INSERT INTO categories (id, name, type, status, user_id, created_at, updated_at) " +
            "VALUES (5, 'Archived Hobby', 'INCOME', 'ARCHIVED', 1, NOW(), NOW())")
    void shouldReturn400WhenCategoryIsArchived() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", buildDtoWithInvalidCategory(), MediaType.APPLICATION_JSON);

        restTestClient.post()
                .uri("/api/v1/transactions")
                .body(multipartBodyBuilder.build())
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(s3Service);
    }

    private static TransactionCreateRequestDTO buildValidDto() {
        return new TransactionCreateRequestDTO(
                BigDecimal.valueOf(1.0),
                MoneyFlowType.INCOME,
                1L,
                LocalDate.now(),
                "Shop"
        );
    }

    private static TransactionCreateRequestDTO buildDtoWithInvalidCategory() {
        return new TransactionCreateRequestDTO(
                BigDecimal.valueOf(1.0),
                MoneyFlowType.INCOME,
                5L,
                LocalDate.now(),
                "Shop"
        );
    }


    private static ByteArrayResource buildResource(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

}
