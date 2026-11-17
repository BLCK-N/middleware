package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.UserOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<UserOtp, String> {

    Optional<UserOtp> findFirstByEmailIgnoreCaseOrderByTimeGeneratedDesc(String email);

    List<UserOtp> findAllByEmailIgnoreCase(String email);

    Optional<UserOtp> findOtpByOtpReferenceAndEmail(String otpReference, String email);

    Optional<UserOtp> findByOtpReference(String otpReference);
}
