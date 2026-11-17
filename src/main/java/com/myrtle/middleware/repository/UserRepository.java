package com.myrtle.middleware.repository;

import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmailAddressIgnoreCase(String email);

    default Optional<User> findByEmailAddress(String email) {
        return findByEmailAddressIgnoreCase(email);
    }

    default Optional<User> findByEmail(String email) {
        return findByEmailAddressIgnoreCase(email);
    }

    Optional<User> findByAccountNumber(String accountNumber);

    Optional<User> findByBvn(String bvn);

    List<User> findByCorporateClient_Id(String corporateClientId);

    /**
     * True when the account is a corporate login: attached to a corporate client AND carrying the
     * corporate role. Queried rather than walked off the entity so it works with
     * {@code spring.jpa.open-in-view=false}, where the lazy corporateClient association is not
     * reachable outside a transaction.
     */
    @Query("""
            select count(u) > 0 from User u
            where lower(u.emailAddress) = lower(:email)
              and u.corporateClient is not null
              and u.role = :role
            """)
    boolean isCorporateUser(@Param("email") String email, @Param("role") Role role);

    @Query("""
            select count(u) from User u
            where u.corporateClient.id = :corporateClientId
              and u.isDeleted = false
            """)
    long countActiveByCorporateClientId(@Param("corporateClientId") String corporateClientId);
}

