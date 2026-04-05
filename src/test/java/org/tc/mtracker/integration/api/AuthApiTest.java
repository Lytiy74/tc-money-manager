package org.tc.mtracker.integration.api;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.tc.mtracker.account.AccountRepository;
import org.tc.mtracker.auth.dto.LoginRequestDto;
import org.tc.mtracker.auth.dto.RefreshTokenRequestDto;
import org.tc.mtracker.auth.dto.RegistrationRequestDto;
import org.tc.mtracker.auth.dto.ResetPasswordRequestDto;
import org.tc.mtracker.auth.repository.RefreshTokenRepository;
import org.tc.mtracker.currency.CurrencyCode;
import org.tc.mtracker.security.JwtResponseDTO;
import org.tc.mtracker.support.base.BaseApiIntegrationTest;
import org.tc.mtracker.support.factory.DatabaseTestDataFactory;
import org.tc.mtracker.support.factory.MultipartTestResourceFactory;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserRepository;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("integration")
class AuthApiTest extends BaseApiIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Nested
    class SignUp {

        @Test
        void shouldCreateUserWithoutAvatarAndProvisionDefaultAccount() {
            String email = "new-user@example.com";
            MultipartBodyBuilder parts = new MultipartBodyBuilder();
            parts.part("dto", new RegistrationRequestDto(
                    email,
                    DatabaseTestDataFactory.DEFAULT_PASSWORD,
                    "New User",
                    CurrencyCode.USD
            ), MediaType.APPLICATION_JSON);

            restTestClient.post()
                    .uri("/api/v1/auth/sign-up")
                    .body(parts.build())
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.email").isEqualTo(email)
                    .jsonPath("$.avatarUrl").isEmpty()
                    .jsonPath("$.isActivated").isEqualTo(false);

            User savedUser = userRepository.findByEmail(email).orElseThrow();

            assertThat(savedUser.isActivated()).isFalse();
            assertThat(accountRepository.findAll()).hasSize(1);
            verify(authEmailService).sendVerificationEmail(eq(email), anyString());
            verifyNoInteractions(s3Service);
        }

        @Test
        void shouldCreateUserWithAvatar() {
            String email = "avatar-user@example.com";
            when(s3Service.generatePresignedUrl(anyString())).thenReturn("https://test-bucket.local/avatar.jpg");

            MultipartBodyBuilder parts = new MultipartBodyBuilder();
            parts.part("dto", new RegistrationRequestDto(
                    email,
                    DatabaseTestDataFactory.DEFAULT_PASSWORD,
                    "Avatar User",
                    CurrencyCode.EUR
            ), MediaType.APPLICATION_JSON);
            ByteArrayResource avatar = MultipartTestResourceFactory.resource("avatar.jpg", "avatar");
            parts.part("avatar", avatar, MediaType.IMAGE_JPEG);

            restTestClient.post()
                    .uri("/api/v1/auth/sign-up")
                    .body(parts.build())
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.avatarUrl").isEqualTo("https://test-bucket.local/avatar.jpg");

            verify(s3Service).saveFile(anyString(), any());
            verify(s3Service).generatePresignedUrl(anyString());
            verify(authEmailService).sendVerificationEmail(eq(email), anyString());
        }

        @Test
        void shouldRejectDuplicateEmail() {
            fixtures.createUser("duplicate@example.com");

            MultipartBodyBuilder parts = new MultipartBodyBuilder();
            parts.part("dto", new RegistrationRequestDto(
                    "duplicate@example.com",
                    DatabaseTestDataFactory.DEFAULT_PASSWORD,
                    "Duplicate User",
                    CurrencyCode.USD
            ), MediaType.APPLICATION_JSON);

            restTestClient.post()
                    .uri("/api/v1/auth/sign-up")
                    .body(parts.build())
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("User with this email already exists")
                    .jsonPath("$.code").isEqualTo("user_already_exists");
        }

        @Test
        void shouldRejectUnsupportedAvatarType() {
            MultipartBodyBuilder parts = new MultipartBodyBuilder();
            parts.part("dto", new RegistrationRequestDto(
                    "bad-avatar@example.com",
                    DatabaseTestDataFactory.DEFAULT_PASSWORD,
                    "Bad Avatar",
                    CurrencyCode.USD
            ), MediaType.APPLICATION_JSON);
            parts.part("avatar", MultipartTestResourceFactory.resource("avatar.txt", "not-image"), MediaType.TEXT_PLAIN);

            restTestClient.post()
                    .uri("/api/v1/auth/sign-up")
                    .body(parts.build())
                    .exchange()
                    .expectStatus().isBadRequest();

            verifyNoInteractions(s3Service, authEmailService);
        }
    }

    @Nested
    class LoginAndTokens {

        @Test
        void shouldLoginActivatedUserAndPersistRefreshToken() {
            User user = fixtures.createUser("active@example.com");

            JwtResponseDTO response = restTestClient.post()
                    .uri("/api/v1/auth/login")
                    .body(new LoginRequestDto(user.getEmail(), DatabaseTestDataFactory.DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(JwtResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(refreshTokenRepository.findAll()).hasSize(1);
        }

        @Test
        void shouldRejectInactiveUserLogin() {
            User user = fixtures.createUser("inactive@example.com", false);

            restTestClient.post()
                    .uri("/api/v1/auth/login")
                    .body(new LoginRequestDto(user.getEmail(), DatabaseTestDataFactory.DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Account is not activated yet.")
                    .jsonPath("$.code").isEqualTo("user_not_activated");
        }

        @Test
        void shouldReturnGenericBadCredentialsMessageForUnknownUser() {
            restTestClient.post()
                    .uri("/api/v1/auth/login")
                    .body(new LoginRequestDto("missing@example.com", DatabaseTestDataFactory.DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Invalid email or password.")
                    .jsonPath("$.code").isEqualTo("bad_credentials");
        }

        @Test
        void shouldSendResetPasswordEmailForExistingUser() {
            User user = fixtures.createUser("reset@example.com");

            restTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/auth/reset-token")
                            .queryParam("email", user.getEmail())
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("Your link to reset password was sent!");

            verify(authEmailService).sendResetPassword(eq(user.getEmail()), anyString());
        }

        @Test
        void shouldResetPasswordWhenVerificationTokenIsValid() {
            User user = fixtures.createUser("reset@example.com");
            String token = jwtFactory.token(user.getEmail(), "password_reset", Duration.ofMinutes(5));

            JwtResponseDTO response = restTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/auth/reset-password/confirm")
                            .queryParam("token", token)
                            .build())
                    .body(new ResetPasswordRequestDto("NewStrongPass!2", "NewStrongPass!2"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(JwtResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(passwordEncoder.matches("NewStrongPass!2", userRepository.findByEmail(user.getEmail()).orElseThrow().getPassword()))
                    .isTrue();
        }

        @Test
        void shouldActivateUserForValidEmailVerificationToken() {
            User user = fixtures.createUser("verify@example.com", false);
            String token = jwtFactory.token(user.getEmail(), "email_verification", Duration.ofMinutes(5));

            JwtResponseDTO response = restTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/auth/verify")
                            .queryParam("token", token)
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(JwtResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(userRepository.findByEmail(user.getEmail()).orElseThrow().isActivated()).isTrue();
        }

        @Test
        void shouldRefreshAccessTokenForKnownRefreshToken() {
            User user = fixtures.createUser("refresh@example.com");
            fixtures.createRefreshToken(user, "refresh-token");

            JwtResponseDTO response = restTestClient.post()
                    .uri("/api/v1/auth/refresh")
                    .body(new RefreshTokenRequestDto("refresh-token"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(JwtResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
        }

        @Test
        void shouldExposeVerificationTokenFromRegistrationMail() {
            String email = "capture@example.com";
            MultipartBodyBuilder parts = new MultipartBodyBuilder();
            parts.part("dto", new RegistrationRequestDto(
                    email,
                    DatabaseTestDataFactory.DEFAULT_PASSWORD,
                    "Capture User",
                    CurrencyCode.USD
            ), MediaType.APPLICATION_JSON);

            restTestClient.post()
                    .uri("/api/v1/auth/sign-up")
                    .body(parts.build())
                    .exchange()
                    .expectStatus().isCreated();

            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(authEmailService).sendVerificationEmail(eq(email), tokenCaptor.capture());

            assertThat(tokenCaptor.getValue()).isNotBlank();
        }
    }
}
