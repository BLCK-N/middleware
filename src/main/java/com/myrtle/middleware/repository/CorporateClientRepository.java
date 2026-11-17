package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.CorporateClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CorporateClientRepository extends JpaRepository<CorporateClient, String> {
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPrimaryContactEmailIgnoreCase(String primaryContactEmail);
    boolean existsByPrimaryContactUsernameIgnoreCase(String primaryContactUsername);
    boolean existsByBvn(String bvn);
    boolean existsByMobileNo(String mobileNo);
    boolean existsByPrimaryContactMobileNo(String primaryContactMobileNo);
    boolean existsByRefCode(String refCode);
    boolean existsByExternalClientId(String externalClientId);
    Optional<CorporateClient> findByRefCode(String refCode);

    Optional<CorporateClient> findByRcNumberIgnoreCase(String rcNumber);
    
    /**
     * Find a corporate client by email address (case-insensitive).
     * @param email the email address
     * @return optional containing the corporate client if found
     */
    Optional<CorporateClient> findByEmailIgnoreCase(String email);

    default Optional<CorporateClient> findByEmail(String email) {
        return findByEmailIgnoreCase(email);
    }
    
    /**
     * Find a corporate client by primary contact email (case-insensitive).
     * @param email the primary contact email address
     * @return optional containing the corporate client if found
     */
    Optional<CorporateClient> findByPrimaryContactEmailIgnoreCase(String email);

    @Modifying
    @Query(value = """
            UPDATE corporate_clients
            SET role = 'CORPORATE_ACCOUNT'
            WHERE role = 'CORPERATE_ACCOUNT'
            """, nativeQuery = true)
    int fixMisspelledCorporateAccountRole();

    @Modifying
    @Query(value = """
            UPDATE corporate_clients
            SET role = 'CORPORATE_ACCOUNT'
            WHERE role IS NULL
            """, nativeQuery = true)
    int backfillNullCorporateAccountRole();

    @Modifying
    @Query(value = """
            UPDATE corporate_clients
            SET tier = 3
            WHERE tier IS NULL
            """, nativeQuery = true)
    int backfillNullCorporateClientTier();
}
