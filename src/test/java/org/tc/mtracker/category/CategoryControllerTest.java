package org.tc.mtracker.category;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.tc.mtracker.category.dto.CategoryResponseDTO;
import org.tc.mtracker.category.dto.CreateCategoryDTO;
import org.tc.mtracker.common.enums.TransactionType;
import org.tc.mtracker.utils.S3Service;
import org.tc.mtracker.utils.TestHelpers;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.secret-key=aZbYcXdWeVfUgThSiRjQkPlOmNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRr",
                "security.jwt.expiration-time=900",
                "security.jwt.test-expiration=60",
                "aws.region=us-central-1",
                "aws.bucket-name=test-bucket",
                "aws.access-key-id=test-key-id",
                "aws.secret-access-key=test-secret"
        }
)
@Testcontainers
@Sql(value = "/datasets/test_categories.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = "/datasets/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CategoryControllerTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:8.3.0");

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private Environment env;

    @MockitoBean
    private S3Service s3Service;

    @Autowired
    private TestHelpers testHelpers;

    private String authToken;

    @BeforeEach
    void setUp() {
        authToken = "Bearer " + testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);
    }

    @Test
    void shouldReturnGlobalAndUserSpecificCategories() {
        restTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/categories")
                        .queryParam("name", "")
                        .queryParam("type", TransactionType.INCOME, TransactionType.EXPENSE)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CategoryResponseDTO[].class)
                .value(categories -> {
                    assertThat(categories).hasSize(3);
                    assertThat(categories).extracting("name")
                            .containsExactlyInAnyOrder("Salary", "Rent", "Side Project");
                });
    }

    @Test
    void shouldFilterCategoriesByNameIgnoringCase() {
        restTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/categories")
                        .queryParam("name", "sal") // Partial search for "Salary"
                        .queryParam("type", TransactionType.INCOME)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].name").isEqualTo("Salary");
    }

    @Test
    void shouldFilterCategoriesByType() {
        restTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/categories")
                        .queryParam("name", "")
                        .queryParam("type", TransactionType.EXPENSE)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].name").isEqualTo("Rent");
    }

    @Test
    void shouldCreateCategorySuccessfully() {
        CreateCategoryDTO newCategory = new CreateCategoryDTO("Health", TransactionType.EXPENSE, "heart-pulse");

        restTestClient
                .post()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .body(newCategory)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponseDTO.class)
                .value(response -> {
                    assertThat(response.name()).isEqualTo("Health");
                    assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
                    assertThat(response.icon()).isEqualTo("heart-pulse");
                });
    }

    @Test
    void shouldReturnConflictWhenCategoryAlreadyExists() {
        // Assuming "Salary" / INCOME exists in your test_categories.sql
        CreateCategoryDTO duplicate = new CreateCategoryDTO("Salary", TransactionType.INCOME, "money");

        restTestClient
                .post()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .body(duplicate)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void shouldReturnBadRequestWhenNameIsMissing() {
        // Create a DTO that violates validation (if you have @NotBlank on name)
        CreateCategoryDTO invalid = new CreateCategoryDTO("", TransactionType.EXPENSE, "icon");

        restTestClient
                .post()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .body(invalid)
                .exchange()
                .expectStatus().isBadRequest();
    }
}