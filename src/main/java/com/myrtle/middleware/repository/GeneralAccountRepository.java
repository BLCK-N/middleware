package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.GeneralAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeneralAccountRepository extends JpaRepository<GeneralAccount,String> {
    Optional<GeneralAccount> findByProductCodeIgnoreCase(String productCode);
    boolean existsByAccountNumber(String accountNumber);
}
