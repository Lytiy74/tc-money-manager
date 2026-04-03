package org.tc.mtracker.auth.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEmailService {

    private static final String EMAIL_VERIFICATION_SUBJECT = "Email Verification";
    private static final String PASSWORD_RESET_SUBJECT = "Reset Password";
    private static final String PASSWORD_CHANGED_SUBJECT = "Password changed";
    private static final String PASSWORD_CHANGED_CONTENT = "Your password has been changed successfully.";

    private final JavaMailSender javaMailSender;

    @Value("${app.frontend-url:http://localhost:8080}")
    private String frontendUrl;

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

    public void sendResetPassword(String email, String resetToken) {
        String verificationLink = String.format("%s/reset-password?resetToken=%s", frontendUrl, resetToken);

        sendPlainTextEmail(email,
                PASSWORD_RESET_SUBJECT,
                "Please click on this link within 24 hours to reset your password: " + verificationLink);
    }

    public void sendPasswordChangedNotification(String email) {
        sendPlainTextEmail(email, PASSWORD_CHANGED_SUBJECT, PASSWORD_CHANGED_CONTENT);
    }
}
