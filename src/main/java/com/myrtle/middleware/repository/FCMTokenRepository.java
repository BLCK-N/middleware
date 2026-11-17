package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.FCMToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FCMTokenRepository extends JpaRepository<FCMToken,String>{
    Optional<FCMToken> findByUserId(String userId);
}
