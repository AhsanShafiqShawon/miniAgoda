package com.miniagoda.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miniagoda.auth.entity.EmailVerificationToken;
import com.miniagoda.user.entity.User;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    void deleteByUser(User user);
}