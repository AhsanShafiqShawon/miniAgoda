package com.miniagoda.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miniagoda.auth.entity.RefreshToken;
import com.miniagoda.user.entity.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String hashedToken);
    void deleteByUser(User user);
}