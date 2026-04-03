package org.tc.mtracker.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.auth.dto.LoginRequestDto;
import org.tc.mtracker.auth.dto.RefreshTokenRequestDto;
import org.tc.mtracker.auth.dto.RegistrationRequestDto;
import org.tc.mtracker.auth.dto.ResetPasswordRequestDto;
import org.tc.mtracker.auth.mail.AuthEmailService;
import org.tc.mtracker.currency.CurrencyCode;
import org.tc.mtracker.security.JwtResponseDTO;
import org.tc.mtracker.utils.S3Service;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
class AuthControllerTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:8.3.0");

    @MockitoBean
    private AuthEmailService authEmailService;

    @MockitoBean
    private S3Service s3Service;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private Environment env;

    @Test
    void shouldReturn201AndAuthResponseDtoIfUserIsSignedUpSuccessfullyWithoutAvatar() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "new1-user@gmail.com",
                "validStrongPassword!1",
                "NewOne User",
                CurrencyCode.USD
        ), MediaType.APPLICATION_JSON);

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.fullName").isEqualTo("NewOne User")
                .jsonPath("$.email").isEqualTo("new1-user@gmail.com")
                .jsonPath("$.currencyCode").isEqualTo("USD")
                .jsonPath("$.avatarUrl").isEmpty()
                .jsonPath("$.isActivated").isEqualTo(false)
                .jsonPath("$.createdAt").isNotEmpty();

        verifyNoInteractions(s3Service);
        verify(authEmailService, times(1)).sendVerificationEmail(eq("new1-user@gmail.com"), anyString());
        verifyNoMoreInteractions(authEmailService);
    }

    @Test
    void shouldReturn201WithCyrillicFullName() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "new1-user@gmail.com",
                "validStrongPassword!1",
                "Батько Батькович",
                CurrencyCode.USD
        ), MediaType.APPLICATION_JSON);

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void shouldReturn201AndAuthResponseDtoIfUserIsSignedUpSuccessfullyWithAvatar() {
        when(s3Service.generatePresignedUrl(any(String.class))).thenReturn("test-avatar-url");

        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "new-user@gmail.com",
                "newValidPassword!123",
                "New User",
                CurrencyCode.USD
        ), MediaType.APPLICATION_JSON);

        byte[] avatarBytes = "test-avatar.jpg".getBytes();
        ByteArrayResource avatarResource = new ByteArrayResource(avatarBytes) {
            @Override
            public String getFilename() {
                return "test-avatar.jpg";
            }
        };
        multipartBodyBuilder.part("avatar", avatarResource, MediaType.IMAGE_JPEG);

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.fullName").isEqualTo("New User")
                .jsonPath("$.email").isEqualTo("new-user@gmail.com")
                .jsonPath("$.currencyCode").isEqualTo("USD")
                .jsonPath("$.avatarUrl").isEqualTo("test-avatar-url")
                .jsonPath("$.isActivated").isEqualTo(false)
                .jsonPath("$.createdAt").isNotEmpty();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        verify(s3Service, times(1)).saveFile(keyCaptor.capture(), any(MultipartFile.class));
        verify(s3Service, times(1)).generatePresignedUrl(keyCaptor.getValue());
        verify(authEmailService, times(1)).sendVerificationEmail(eq("new-user@gmail.com"), anyString());
        verifyNoMoreInteractions(s3Service, authEmailService);
    }

    @Test
    void shouldReturn400IfDtoPartIsMissingOnSignUp() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();

        byte[] avatarBytes = "avatar".getBytes();
        ByteArrayResource avatarResource = new ByteArrayResource(avatarBytes) {
            @Override
            public String getFilename() {
                return "test-avatar.jpg";
            }
        };
        multipartBodyBuilder.part("avatar", avatarResource, MediaType.IMAGE_JPEG);

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody();

        verifyNoInteractions(s3Service, authEmailService);
    }

    @Test
    void shouldReturn400IfAvatarHasUnsupportedContentTypeOnSignUp() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "new-user3@gmail.com",
                "12345678",
                "New User",
                CurrencyCode.USD
        ), MediaType.APPLICATION_JSON);

        byte[] avatarBytes = "not-an-image".getBytes();
        ByteArrayResource avatarResource = new ByteArrayResource(avatarBytes) {
            @Override
            public String getFilename() {
                return "avatar.txt";
            }
        };
        multipartBodyBuilder.part("avatar", avatarResource, MediaType.TEXT_PLAIN);

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody();

        verifyNoInteractions(s3Service, authEmailService);
    }

    @Test
    void shouldReturn400IfFullNameIsShorterThanBoundOfLength() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "Test7@gmail.com",
                "ABc123456!",
                "Aa",
                CurrencyCode.USD
        ));

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400IfFullNameIsLongerThanBoundOfLength() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "Test7@gmail.com",
                "ABc123456!",
                "qweqweqweqewqweqweqweqweqweqweqweqweqweqweqweqeqeqwewqeqweqweqweqe",
                CurrencyCode.USD
        ));

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400IfFullNameIsNotContainsLatinOrCyrilicSymbols() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "Test7@gmail.com",
                "ABc123456!",
                "1234567890",
                CurrencyCode.USD
        ));

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
    }


    @Test
    void shouldReturn400IfPasswordFieldIsEmptyOnSignUp() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "test@test.com",
                "",
                "Test User",
                CurrencyCode.USD
        ));

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400IsLessThen8CharactersOnSignUp() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "test@test.com",
                "Pass12!",
                "Test User",
                CurrencyCode.USD
        ));

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400IsMoreThen72CharactersOnSignUp() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "test@test.com",
                "Pass12!",
                "Test User",
                CurrencyCode.USD
        ));

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn201WhenPasswordLength72CharactersOnSignUp() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "test@test.com",
                "3XqgtwJeRa76TbRKxApPaXeahvr4eVUKHPe7Sm2ai0R7dxXxPhb0GRFnXf5PL2!fjaQ3Uf9U",
                "Test User",
                CurrencyCode.USD
        ));

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void shouldReturn400IsNotContainsUppercaseOnSignUp() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "test@test.com",
                "validpassword1!",
                "Test User",
                CurrencyCode.USD
        ));

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400IsNotContainsLowercaseOnSignUp() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "test@test.com",
                "VALIDPASSWORD1!",
                "Test User",
                CurrencyCode.USD
        ));

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400IsNotContainsSpecialCharatersOnSignUp() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("dto", new RegistrationRequestDto(
                "test@test.com",
                "SuperStrong1",
                "Test User",
                CurrencyCode.USD
        ));

        restTestClient
                .post()
                .uri("/api/v1/auth/sign-up")
                .body(multipartBodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
    }


    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturnAccessTokenIfUserIsLoggedSuccessfully() {
        LoginRequestDto authDto = new LoginRequestDto("test@gmail.com", "12345678");

        restTestClient
                .post()
                .uri("/api/v1/auth/login")
                .body(authDto)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn403IfUserLoginWithoutAccountActivation() {
        LoginRequestDto authDto = new LoginRequestDto("inactive@gmail.com", "12345678");

        restTestClient
                .post()
                .uri("/api/v1/auth/login")
                .body(authDto)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn401IfPasswordIsIncorrect() {
        LoginRequestDto authDto = new LoginRequestDto("test@gmail.com", "wrongpassword");

        restTestClient
                .post()
                .uri("/api/v1/auth/login")
                .body(authDto)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Invalid credentials. Password does not match!");
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn401IfUserDoesNotExist() {
        LoginRequestDto authDto = new LoginRequestDto("nonexistent@gmail.com", "12345678");

        restTestClient
                .post()
                .uri("/api/v1/auth/login")
                .body(authDto)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("User with email nonexistent@gmail.com does not exist.");
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldSendResetTokenSuccessfully() {
        restTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/auth/getTokenToResetPassword")
                        .queryParam("email", "test@gmail.com")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("Your link to reset password was sent!");
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldSendResetTokenSuccessfullyViaNewEndpoint() {
        restTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/auth/reset-token")
                        .queryParam("email", "test@gmail.com")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("Your link to reset password was sent!");
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn401WhenRequestingResetForNonExistentEmail() {
        restTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/auth/reset-token")
                        .queryParam("email", "nonexistent@gmail.com")
                        .build())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldReturn404WhenResetTokenReferencesMissingUser() {
        String validToken = generateTestToken("deleted-user@gmail.com", "password_reset", 60000);
        ResetPasswordRequestDto resetDto = new ResetPasswordRequestDto("newPassword123", "newPassword123");

        restTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/auth/reset-password/confirm")
                        .queryParam("token", validToken)
                        .build())
                .body(resetDto)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("User with email 'deleted-user@gmail.com' not found");
    }

    @Test
    void shouldReturn404WhenVerificationTokenReferencesMissingUser() {
        String validToken = generateTestToken("deleted-user@gmail.com", "email_verification", 60000);

        restTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/auth/verify")
                        .queryParam("token", validToken)
                        .build())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("User with email 'deleted-user@gmail.com' not found");
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldResetPasswordSuccessfullyWithValidToken() {
        String validToken = generateTestToken("test@gmail.com", "password_reset", 60000);

        ResetPasswordRequestDto resetDto = new ResetPasswordRequestDto("newPassword123", "newPassword123");

        restTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/auth/reset-password/confirm")
                        .queryParam("token", validToken)
                        .build())
                .body(resetDto)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn400WhenPasswordsDoNotMatch() {
        String validToken = generateTestToken("test@gmail.com", "password_reset", 60000);
        ResetPasswordRequestDto mismatchDto = new ResetPasswordRequestDto("newPassword123", "differentPassword");

        restTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/auth/reset-password/confirm")
                        .queryParam("token", validToken)
                        .build())
                .body(mismatchDto)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn401WhenTokenIsExpired() {
        String expiredToken = generateTestToken("test@gmail.com", "password_reset", -3600000);
        ResetPasswordRequestDto resetDto = new ResetPasswordRequestDto("newPassword123", "newPassword123");

        restTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/auth/reset-password/confirm")
                        .queryParam("token", expiredToken)
                        .build())
                .body(resetDto)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldReturn401WhenTokenPurposeIsWrong() {
        String wrongPurposeToken = generateTestToken("test@gmail.com", "email_verification", 60000);
        ResetPasswordRequestDto resetDto = new ResetPasswordRequestDto("newPassword123", "newPassword123");

        restTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/auth/reset-password/confirm")
                        .queryParam("token", wrongPurposeToken)
                        .build())
                .body(resetDto)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /**
     * Helper to generate JWTs that match the Test configuration
     */
    private String generateTestToken(String email, String purpose, long expirationOffsetMs) {
        String secretKey = env.getProperty("security.jwt.secret-key");

        byte[] keyBytes = Decoders.BASE64.decode(secretKey);

        return Jwts.builder()
                .setClaims(Map.of("purpose", purpose))
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationOffsetMs))
                .signWith(Keys.hmacShaKeyFor(keyBytes), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @Sql("/datasets/test_users.sql")
    void shouldRefreshAccessTokenSuccessfully() throws InterruptedException {
        LoginRequestDto loginDto = new LoginRequestDto("test@gmail.com", "12345678");

        JwtResponseDTO loginResponse = restTestClient
                .post()
                .uri("/api/v1/auth/login")
                .body(loginDto)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JwtResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(loginResponse).isNotNull();
        String refreshToken = loginResponse.refreshToken();

        // Wait 1 second to ensure the JWT 'iat' claim changes
        Thread.sleep(1000);

        RefreshTokenRequestDto refreshRequest = new RefreshTokenRequestDto(refreshToken);

        restTestClient
                .post()
                .uri("/api/v1/auth/refresh")
                .body(refreshRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.accessToken").value(newAccessToken -> {
                    assertThat(newAccessToken).isNotNull();
                    assertThat(newAccessToken).isNotEqualTo(loginResponse.accessToken());
                })
                .jsonPath("$.refreshToken").isEqualTo(refreshToken);
    }

    @Test
    void shouldReturn401WhenRefreshTokenIsInvalid() {
        RefreshTokenRequestDto invalidRequest = new RefreshTokenRequestDto("invalid-uuid-token");

        restTestClient
                .post()
                .uri("/api/v1/auth/refresh")
                .body(invalidRequest)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Refresh token is invalid.");
    }
}
