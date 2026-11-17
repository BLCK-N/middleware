package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.HoldPersonalDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface HoldPersonalDetailsRepository extends JpaRepository<HoldPersonalDetails, String>,
        JpaSpecificationExecutor<HoldPersonalDetails> {
    Optional<HoldPersonalDetails> findByUser_EmailAddressIgnoreCase(String email);
    Optional<HoldPersonalDetails> findByUser_Id(String userId);
    default Optional<HoldPersonalDetails> findByUser_EmailAddress(String email) {
        return findByUser_EmailAddressIgnoreCase(email);
    }
}
