package org.tc.mtracker.integration.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.tc.mtracker.auth.dto.UpdateEmailRequestDto;
import org.tc.mtracker.auth.dto.UpdatePasswordRequestDto;
import org.tc.mtracker.currency.CurrencyCode;
import org.tc.mtracker.support.base.BaseApiIntegrationTest;
import org.tc.mtracker.support.factory.DatabaseTestDataFactory;
import org.tc.mtracker.support.factory.MultipartTestResourceFactory;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserRepository;
import org.tc.mtracker.user.dto.RequestUpdateUserProfileDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("integration")
class UserApiTest extends BaseApiIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldReturnCurrentUserProfile() {
        User user = fixtures.createUser("profile@example.com");

        restTestClient.get()
                .uri("/api/v1/users/me")
                .header("Authorization", authHeader(user))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo(user.getEmail())
                .jsonPath("$.fullName").isEqualTo("Test User")
                .jsonPath("$.currencyCode").isEqualTo("USD");
    }

    @Test
    void shouldUpdateProfileFields() {
        User user = fixtures.createUser("profile@example.com");
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("dto", new RequestUpdateUserProfileDTO("Updated User", CurrencyCode.EUR), MediaType.APPLICATION_JSON);

        restTestClient.put()
                .uri("/api/v1/users/me")
                .header("Authorization", authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fullName").isEqualTo("Updated User")
                .jsonPath("$.currencyCode").isEqualTo("EUR");

        User updatedUser = userRepository.findByEmail(user.getEmail()).orElseThrow();
        assertThat(updatedUser.getFullName()).isEqualTo("Updated User");
        assertThat(updatedUser.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
    }

    @Test
    void shouldUploadAvatarWhenUpdatingProfile() {
        User user = fixtures.createUser("avatar@example.com");
        when(s3Service.generatePresignedUrl(anyString())).thenReturn("https://test-bucket.local/avatar.jpg");

        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        ByteArrayResource avatar = MultipartTestResourceFactory.jpegImage("avatar.jpg");
        parts.part("avatar", avatar, MediaType.IMAGE_JPEG);

        restTestClient.put()
                .uri("/api/v1/users/me")
                .header("Authorization", authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.avatarUrl").isEqualTo("https://test-bucket.local/avatar.jpg");

        verify(s3Service).saveFile(anyString(), any());
        verify(s3Service).generatePresignedUrl(anyString());
    }

    @Test
    void shouldUploadWebpAvatarWhenUpdatingProfile() {
        User user = fixtures.createUser("avatar-webp@example.com");
        when(s3Service.generatePresignedUrl(anyString())).thenReturn("https://test-bucket.local/avatar.webp");

        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        ByteArrayResource avatar = MultipartTestResourceFactory.webpImage("avatar.webp");
        parts.part("avatar", avatar, MediaType.parseMediaType("image/webp"));

        restTestClient.put()
                .uri("/api/v1/users/me")
                .header("Authorization", authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.avatarUrl").isEqualTo("https://test-bucket.local/avatar.webp");

        verify(s3Service).saveFile(anyString(), any());
        verify(s3Service).generatePresignedUrl(anyString());
    }

    @Test
    void shouldTriggerAndVerifyEmailUpdateFlow() {
        User user = fixtures.createUser("before-update@example.com");

        restTestClient.post()
                .uri("/api/v1/users/me/update-email")
                .header("Authorization", authHeader(user))
                .body(new UpdateEmailRequestDto("after-update@example.com"))
                .exchange()
                .expectStatus().isOk();

        String token = userRepository.findByEmail(user.getEmail()).orElseThrow().getVerificationToken();

        restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/verify-email")
                        .queryParam("token", token)
                        .build())
                .exchange()
                .expectStatus().isOk();

        assertThat(userRepository.findByEmail("after-update@example.com")).isPresent();
    }

    @Test
    void shouldUpdatePasswordAndSendNotification() {
        User user = fixtures.createUser("password@example.com");

        restTestClient.put()
                .uri("/api/v1/users/me/update-password")
                .header("Authorization", authHeader(user))
                .body(new UpdatePasswordRequestDto(
                        DatabaseTestDataFactory.DEFAULT_PASSWORD,
                        "AnotherStrongPass!2",
                        "AnotherStrongPass!2"
                ))
                .exchange()
                .expectStatus().isOk();

        assertThat(passwordEncoder.matches(
                "AnotherStrongPass!2",
                userRepository.findByEmail(user.getEmail()).orElseThrow().getPassword()
        )).isTrue();
        verify(authEmailService).sendPasswordChangedNotification(user.getEmail());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "@Invalid Full Name",
            "John123",
            "Іван9",
            "Test_Name"
    })
    void shouldRejectProfileUpdateWhenFullNameContainsInvalidCharacters(String invalidFullName) {
        User user = fixtures.createUser();
        String fullName = user.getFullName();
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("dto", new RequestUpdateUserProfileDTO(invalidFullName, CurrencyCode.USD), MediaType.APPLICATION_JSON);

        restTestClient.put()
                .uri("/api/v1/users/me")
                .header("Authorization", authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Request validation failed.");

        assertThat(userRepository.findByEmail(user.getEmail()).orElseThrow().getFullName()).isEqualTo(fullName);
    }

    @Test
    void shouldRejectProfileUpdateWhenFullNameIsLongerThan35Characters() {
        User user = fixtures.createUser();
        String fullName = user.getFullName();
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("dto", new RequestUpdateUserProfileDTO("Abfhkiuytresdfghjkloiuytrewsdfghjkloi", CurrencyCode.USD), MediaType.APPLICATION_JSON);

        restTestClient.put()
                .uri("/api/v1/users/me")
                .header("Authorization", authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Request validation failed.");

        assertThat(userRepository.findByEmail(user.getEmail()).orElseThrow().getFullName()).isEqualTo(fullName);
    }

    @Test
    void shouldRejectProfileUpdateWhenFullNameIsShorterThan3Characters() {
        User user = fixtures.createUser();
        String fullName = user.getFullName();
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("dto", new RequestUpdateUserProfileDTO("Ab", CurrencyCode.USD), MediaType.APPLICATION_JSON);

        restTestClient.put()
                .uri("/api/v1/users/me")
                .header("Authorization", authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Request validation failed.");

        assertThat(userRepository.findByEmail(user.getEmail()).orElseThrow().getFullName()).isEqualTo(fullName);
    }

    @Test
    void shouldRejectUnauthenticatedProfileAccess() {
        restTestClient.get()
                .uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isForbidden();
    }
}
