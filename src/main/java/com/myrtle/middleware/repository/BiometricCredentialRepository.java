package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.BiometricCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BiometricCredentialRepository extends JpaRepository<BiometricCredential, String> {

    Optional<BiometricCredential> findByDevice_Id(String deviceId);
}
