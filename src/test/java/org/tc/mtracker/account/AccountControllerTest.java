package org.tc.mtracker.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.tc.mtracker.utils.S3Service;
import org.tc.mtracker.utils.TestHelpers;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@Sql(value = "/datasets/test_users.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AccountControllerTest {

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
    }

    @Test
    @SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
    @Sql(statements = "UPDATE accounts SET balance = 123.45 WHERE id = 1")
    void shouldReturnDefaultAccountBalance() {
        restTestClient.get()
                .uri("/api/v1/accounts/default")
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.balance").isEqualTo(123.45);
    }

    @Test
    @SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
    @Sql(statements = "UPDATE users SET default_account_id = NULL WHERE id = 1")
    void shouldReturn404WhenDefaultAccountIsMissing() {
        restTestClient.get()
                .uri("/api/v1/accounts/default")
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isNotFound();
    }
}
