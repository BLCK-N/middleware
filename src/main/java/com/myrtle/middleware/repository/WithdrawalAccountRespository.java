package com.myrtle.middleware.repository;

import com.myrtle.middleware.enums.AccountOwnerType;
import com.myrtle.middleware.model.WithdrawalAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WithdrawalAccountRespository extends JpaRepository<WithdrawalAccount,String> {
    // === User-related queries ===
    List<WithdrawalAccount> findByUser_EmailAddressIgnoreCase(String email);
    List<WithdrawalAccount> findByUser_Id(String userId);
    Optional<WithdrawalAccount> findByIdAndUser_EmailAddressIgnoreCase(String id, String email);

    // === Corporate account-related queries ===
    /**
     * Find all withdrawal accounts owned by a specific corporate account.
     * @param corporateClientId the ID of the corporate client
     * @return list of withdrawal accounts owned by the corporate client
     */
    List<WithdrawalAccount> findByCorporateClientIdAndOwnerType(String corporateClientId, AccountOwnerType ownerType);

    /**
     * Find a specific withdrawal account by ID owned by a corporate account.
     * @param id the ID of the withdrawal account
     * @param corporateClientId the ID of the corporate client
     * @param ownerType the account owner type
     * @return optional containing the withdrawal account if found and owned by the corporate client
     */
    Optional<WithdrawalAccount> findByIdAndCorporateClientIdAndOwnerType(String id, String corporateClientId, AccountOwnerType ownerType);

    // === Default method overloads for backward compatibility ===
    default List<WithdrawalAccount> findByUser_EmailAddress(String email) {
        return findByUser_EmailAddressIgnoreCase(email);
    }

    default Optional<WithdrawalAccount> findByIdAndUser_EmailAddress(String id, String email) {
        return findByIdAndUser_EmailAddressIgnoreCase(id, email);
    }

    // === Corporate account convenience methods ===
    /**
     * Find all withdrawal accounts owned by a corporate account.
     * Convenience method that assumes ownerType is CORPORATE_ACCOUNT.
     */
    default List<WithdrawalAccount> findByCorporateClientId(String corporateClientId) {
        return findByCorporateClientIdAndOwnerType(corporateClientId, AccountOwnerType.CORPORATE_ACCOUNT);
    }

    /**
     * Find a specific withdrawal account owned by a corporate account.
     * Convenience method that assumes ownerType is CORPORATE_ACCOUNT.
     */
    default Optional<WithdrawalAccount> findByIdAndCorporateClientId(String id, String corporateClientId) {
        return findByIdAndCorporateClientIdAndOwnerType(id, corporateClientId, AccountOwnerType.CORPORATE_ACCOUNT);
    }
}
