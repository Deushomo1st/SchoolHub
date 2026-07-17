package com.schoolhub.authservice.service;

import com.schoolhub.authservice.model.AppUser;
import com.schoolhub.authservice.model.PasswordReset;
import com.schoolhub.authservice.repository.AppUserRepository;
import com.schoolhub.authservice.repository.PasswordResetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Email-based forgot-password. Every request gets the same neutral answer (no account
 * enumeration). With SMTP configured (spring.mail.host) the link is emailed; without it
 * (dev) the link is logged to the AuthService console instead.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TTL_MINUTES = 30;

    private final AppUserRepository userRepo;
    private final PasswordResetRepository resetRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSender;   // present only when SMTP configured
    private final String appBaseUrl;
    private final String fromAddress;

    public PasswordResetService(AppUserRepository userRepo, PasswordResetRepository resetRepo,
                                BCryptPasswordEncoder passwordEncoder,
                                ObjectProvider<JavaMailSender> mailSender,
                                @Value("${schoolhub.app-base-url:http://localhost:9000}") String appBaseUrl,
                                @Value("${schoolhub.mail-from:no-reply@schoolhub.local}") String fromAddress) {
        this.userRepo = userRepo;
        this.resetRepo = resetRepo;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.appBaseUrl = appBaseUrl;
        this.fromAddress = fromAddress;
    }

    @Transactional
    public void requestReset(String email) {
        AppUser user = userRepo.findByEmailIgnoreCase(email == null ? "" : email.trim()).orElse(null);
        if (user == null) return;                       // same outward response either way

        PasswordReset pr = new PasswordReset();
        pr.setUserId(user.getId());
        pr.setToken(UUID.randomUUID().toString().replace("-", ""));
        pr.setExpiresAt(LocalDateTime.now().plusMinutes(TTL_MINUTES));
        resetRepo.save(pr);

        String link = appBaseUrl + "/reset-password.html?token=" + pr.getToken();
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender != null) {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(user.getEmail());
            msg.setSubject("Reset your SchoolHub password");
            msg.setText("Hi " + user.getFirstName() + ",\n\n"
                    + "Someone (hopefully you) asked to reset your SchoolHub password.\n"
                    + "This link works once and expires in " + TTL_MINUTES + " minutes:\n\n"
                    + link + "\n\n"
                    + "If this wasn't you, ignore this email — nothing changes.");
            try {
                sender.send(msg);
                return;
            } catch (Exception e) {
                log.warn("Password-reset email to {} failed ({}); falling back to console link", user.getEmail(), e.getMessage());
            }
        }
        // Dev fallback: no SMTP — the operator reads the link from the service console.
        log.info("PASSWORD RESET LINK for {}: {}", user.getEmail(), link);
    }

    @Transactional
    public void reset(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters");
        }
        PasswordReset pr = resetRepo.findByToken(token == null ? "" : token.trim())
                .filter(r -> !r.isUsed() && r.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException("This reset link is invalid or has expired — request a new one"));
        AppUser user = userRepo.findById(pr.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Account no longer exists"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        pr.setUsed(true);
        resetRepo.save(pr);
    }
}
