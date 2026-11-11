package com.hykon.iot_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends password reset codes by email when an SMTP server is configured
 * (SPRING_MAIL_HOST etc.). Otherwise falls back to logging the code, which
 * is only suitable for local development.
 */
@Component
public class PasswordResetEmailSender {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;
    private final String username;

    public PasswordResetEmailSender(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:}") String from,
            @Value("${spring.mail.username:}") String username
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
        this.username = username;
    }

    /**
     * Never throws: a mail failure must not reveal whether an account exists
     * or break the forgot-password request.
     */
    public void sendResetCode(String email, String code, long validMinutes) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        if (mailSender == null) {
            log.info("DEV ONLY - no mail server configured; password reset code for {}: {}", email, code);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        String sender = !from.isBlank() ? from : username;
        if (!sender.isBlank()) {
            message.setFrom(sender);
        }
        message.setTo(email);
        message.setSubject("Your password reset code");
        message.setText(
                "Hello,\n\n"
                + "Use this code to reset your password: " + code + "\n\n"
                + "The code expires in " + validMinutes + " minutes. "
                + "If you did not ask to reset your password, you can ignore this email.\n");

        try {
            mailSender.send(message);
            log.info("Password reset code emailed to {}", email);
        } catch (RuntimeException ex) {
            log.error("Failed to send password reset email to {}: {}", email, ex.getMessage());
        }
    }
}
