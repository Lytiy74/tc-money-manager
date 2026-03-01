package org.tc.mtracker.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.tc.mtracker.security.CustomUserDetails;
import org.tc.mtracker.security.JwtService;
import org.tc.mtracker.user.User;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String PURPOSE_CLAIM_KEY = "purpose";
    private static final String EMAIL_VERIFICATION_PURPOSE = "email_verification";
    private static final String EMAIL_VERIFICATION_SUBJECT = "Email Verification";

    private final JwtService jwtService;
    private final JavaMailSender javaMailSender;

    @Value("${app.frontend-url:http://localhost:8080}")
    private String frontendUrl;

    public void sendVerificationEmail(User user) {
        String token = generateVerificationToken(user);
        String verificationLink = String.format("%s/verify?token=%s", frontendUrl, token);

        sendPlainTextEmail(
                user.getEmail(),
                EMAIL_VERIFICATION_SUBJECT,
                "Please verify your email by clicking this link: " + verificationLink
        );
        log.info("Verification email sent to user with id {}", user.getId());
    }

    public void sendVerificationEmail(String email, String token) {
        String verificationLink = String.format("%s/verify?token=%s", frontendUrl, token);
        sendPlainTextEmail(
                email,
                EMAIL_VERIFICATION_SUBJECT,
                "Please verify your email by clicking this link: " + verificationLink
        );
        log.info("Verification email sent to user with email {}", email);
    }

    public void sendPlainTextEmail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        javaMailSender.send(message);
    }

    /**
     * Generates an email with link
     * @param user requested user
     * @param resetToken generated token
     */
    public void sendResetPassword(User user, String resetToken) {
        String verificationLink = String.format("%s/reset-password?resetToken=%s", frontendUrl, resetToken);

        sendPlainTextEmail(user.getEmail(),
                "Reset Password",
                "Please click on this link within 24 hours to reset your password: " + verificationLink);
    }

    private String generateVerificationToken(User user) {
        return jwtService.generateToken(
                Map.of(PURPOSE_CLAIM_KEY, EMAIL_VERIFICATION_PURPOSE),
                new CustomUserDetails(user)
        );
    }
}
