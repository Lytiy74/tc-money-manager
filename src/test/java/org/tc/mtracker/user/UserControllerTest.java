package org.tc.mtracker.user;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.auth.EmailService;
import org.tc.mtracker.currency.CurrencyCode;
import org.tc.mtracker.user.dto.RequestUpdateUserEmailDTO;
import org.tc.mtracker.user.dto.RequestUpdateUserPasswordDTO;
import org.tc.mtracker.user.dto.RequestUpdateUserProfileDTO;
import org.tc.mtracker.utils.S3Service;
import org.tc.mtracker.utils.TestHelpers;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.secret-key=aZbYcXdWeVfUgThSiRjQkPlOmNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAaZyXxWwVvUuTtSsRr",
                "security.jwt.expiration-time=3600000",
                "aws.region=us-central-1",
                "aws.s3.bucket-name=test-bucket",
                "aws.access-key-id=test-key-id",
                "aws.secret-access-key=test-secret",
        }
)
@Testcontainers
class UserControllerTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:8.3.0");

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestHelpers testHelpers;

    @MockitoBean
    private S3Service s3Service;
    @Autowired
    private UserService userService;

    @MockitoBean
    private EmailService emailService;

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldUpdateUserInfoSuccessfully() {
        String email = "test@gmail.com";
        String newFullname = "New Fullname";
        CurrencyCode newCC = CurrencyCode.UAH;
        String token = testHelpers.generateTestToken(email, "access_token", 3600000);
        RequestUpdateUserProfileDTO updateDto = new RequestUpdateUserProfileDTO(newFullname, newCC);

        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", updateDto, MediaType.APPLICATION_JSON);

        restTestClient
                .put()
                .uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isOk();

        User updatedUser = userRepository.findByEmail(email).orElseThrow();

        assertThat(updatedUser.getFullName()).isEqualTo(newFullname);
        assertThat(updatedUser.getCurrencyCode()).isEqualTo(newCC);
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn400WhenFullNameIsEmpty() {
        String email = "test@gmail.com";
        String newFullname = "";
        String token = testHelpers.generateTestToken(email, "access_token", 3600000);
        RequestUpdateUserProfileDTO updateDto = new RequestUpdateUserProfileDTO(newFullname, null);

        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", updateDto, MediaType.APPLICATION_JSON);

        restTestClient
                .put()
                .uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn400WhenFullNameIsTooShort() {
        String token = testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);
        RequestUpdateUserProfileDTO updateDto = new RequestUpdateUserProfileDTO("", null);

        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", updateDto, MediaType.APPLICATION_JSON);

        restTestClient
                .put()
                .uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn400WhenFullNameIsTooLong() {
        String token = testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);
        RequestUpdateUserProfileDTO updateDto = new RequestUpdateUserProfileDTO("a".repeat(129), null);

        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", updateDto, MediaType.APPLICATION_JSON);

        restTestClient
                .put()
                .uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn200WhenUserAvatarIsSuccessfullyUpdated() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        when(s3Service.generatePresignedUrl(Mockito.anyString())).thenReturn("https://example.com/test-avatar.jpg");

        String token = testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);
        byte[] avatarBytes = "test-avatar.jpg".getBytes();
        ByteArrayResource avatarResource = new ByteArrayResource(avatarBytes) {
            @Override
            public String getFilename() {
                return "test-avatar.jpg";
            }
        };
        multipartBodyBuilder.part("avatar", avatarResource, MediaType.IMAGE_JPEG);

        restTestClient
                .put()
                .uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.avatarUrl").isEqualTo("https://example.com/test-avatar.jpg");

        verify(s3Service).generatePresignedUrl(Mockito.anyString());
        verify(s3Service).saveFile(Mockito.anyString(), Mockito.any(MultipartFile.class));
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn400WhenUserAvatarIsNotAnImageFile() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();

        String token = testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);
        byte[] avatarBytes = "not-an-image".getBytes();
        ByteArrayResource avatarResource = new ByteArrayResource(avatarBytes) {
            @Override
            public String getFilename() {
                return "avatar.txt";
            }
        };
        multipartBodyBuilder.part("avatar", avatarResource, MediaType.TEXT_PLAIN);

        restTestClient
                .put()
                .uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody();

        verifyNoInteractions(s3Service);
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn200WhenEmailUpdateFlowTriggered() {
        RequestUpdateUserEmailDTO requestUpdateUserEmailDTO = new RequestUpdateUserEmailDTO("newemail@example.com");
        String token = testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);
        restTestClient
                .post()
                .uri("/api/v1/users/me/update-email")
                .header("Authorization", "Bearer " + token)
                .body(requestUpdateUserEmailDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody();

        verify(emailService).sendVerificationEmail(eq("newemail@example.com"), anyString());
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldVerifyEmailUpdateSuccessfully() {
        RequestUpdateUserEmailDTO requestUpdateUserEmailDTO = new RequestUpdateUserEmailDTO("newemail@example.com");
        String accessToken = testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);

        restTestClient
                .post()
                .uri("/api/v1/users/me/update-email")
                .header("Authorization", "Bearer " + accessToken)
                .body(requestUpdateUserEmailDTO)
                .exchange()
                .expectStatus().isOk();
        User userWithToken = userRepository.findByEmail("test@gmail.com").orElseThrow();
        String verificationToken = userWithToken.getVerificationToken();

        restTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/verify-email")
                        .queryParam("token", verificationToken)
                        .build())
                .exchange()
                .expectStatus().isOk();

        User updatedUser = userRepository.findByEmail("newemail@example.com").orElseThrow();
        assertThat(updatedUser.getEmail()).isEqualTo("newemail@example.com");
        assertThat(updatedUser.getPendingEmail()).isNull();
        assertThat(updatedUser.getVerificationToken()).isNull();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn401IfWrongVerificationTokenIsProvided() {
        RequestUpdateUserEmailDTO requestUpdateUserEmailDTO = new RequestUpdateUserEmailDTO("newemail@example.com");
        String accessToken = testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);

        restTestClient
                .post()
                .uri("/api/v1/users/me/update-email")
                .header("Authorization", "Bearer " + accessToken)
                .body(requestUpdateUserEmailDTO)
                .exchange()
                .expectStatus().isOk();
        String verificationToken = testHelpers.generateTestToken("test@gmail.com", "email_update_verification", 3600000);

        restTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/verify-email")
                        .queryParam("token", verificationToken)
                        .build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn401WhenVerificationTokenHasWrongPurpose() {
        RequestUpdateUserEmailDTO requestUpdateUserEmailDTO = new RequestUpdateUserEmailDTO("newemail@example.com");
        String accessToken = testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);

        restTestClient
                .post()
                .uri("/api/v1/users/me/update-email")
                .header("Authorization", "Bearer " + accessToken)
                .body(requestUpdateUserEmailDTO)
                .exchange()
                .expectStatus().isOk();

        String wrongPurposeToken = testHelpers.generateTestToken("test@gmail.com", "password_reset", 3600000);

        restTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/verify-email")
                        .queryParam("token", wrongPurposeToken)
                        .build())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn401WhenVerificationTokenIsExpired() {
        RequestUpdateUserEmailDTO requestUpdateUserEmailDTO = new RequestUpdateUserEmailDTO("newemail@example.com");
        String accessToken = testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);

        restTestClient
                .post()
                .uri("/api/v1/users/me/update-email")
                .header("Authorization", "Bearer " + accessToken)
                .body(requestUpdateUserEmailDTO)
                .exchange()
                .expectStatus().isOk();

        String expiredToken = testHelpers.generateTestToken("test@gmail.com", "email_update_verification", -3600000);

        restTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/verify-email")
                        .queryParam("token", expiredToken)
                        .build())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn401WhenNewEmailIsUsed() {
        RequestUpdateUserEmailDTO requestUpdateUserEmailDTO = new RequestUpdateUserEmailDTO("admin@mtracker.com");
        String accessToken = testHelpers.generateTestToken("test@gmail.com", "email_update_verification", 3600000);

        restTestClient
                .post()
                .uri("/api/v1/users/me/update-email")
                .header("Authorization", "Bearer " + accessToken)
                .body(requestUpdateUserEmailDTO)
                .exchange()
                .expectStatus().is4xxClientError();
        verifyNoInteractions(emailService);
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturnUserProfileWhenAuthenticated() {
        String email = "test@gmail.com";
        String token = testHelpers.generateTestToken(email, "access_token", 3600000);

        restTestClient
                .get()
                .uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo(email)
                .jsonPath("$.fullName").isEqualTo("Active User")
                .jsonPath("$.currencyCode").isEqualTo("USD")
                .jsonPath("$.createdAt").isNotEmpty();
    }

    @Test
    void shouldReturn403WhenAccessingMeWithoutToken() {
        restTestClient
                .get()
                .uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn200WhenPasswordUpdated() {
        String accessToken = testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);
        RequestUpdateUserPasswordDTO dto = new RequestUpdateUserPasswordDTO("12345678", "newPassword", "newPassword");

        restTestClient
                .put()
                .uri("/api/v1/users/me/update-password")
                .header("Authorization", "Bearer " + accessToken)
                .body(dto)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn400WhenCurrentPasswordIsInvalid() {
        String accessToken = testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);
        RequestUpdateUserPasswordDTO dto = new RequestUpdateUserPasswordDTO("87654321", "newPassword", "newPassword");

        restTestClient
                .put()
                .uri("/api/v1/users/me/update-password")
                .header("Authorization", "Bearer " + accessToken)
                .body(dto)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn400WhenPasswordIsShort() {
        String accessToken = testHelpers.generateTestToken("test@gmail.com", "access_token", 3600000);
        RequestUpdateUserPasswordDTO dto = new RequestUpdateUserPasswordDTO("87654321", "new", "new");

        restTestClient
                .put()
                .uri("/api/v1/users/me/update-password")
                .header("Authorization", "Bearer " + accessToken)
                .body(dto)
                .exchange()
                .expectStatus().isBadRequest();
    }
}