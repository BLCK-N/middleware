package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.PendingCorporateData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingCorporateDataRepository extends JpaRepository<PendingCorporateData, String> {
    Optional<PendingCorporateData> findByEmailIgnoreCase(String email);
}
