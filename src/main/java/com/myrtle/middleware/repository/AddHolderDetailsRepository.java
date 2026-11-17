package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.AddHolderDetails;
import com.myrtle.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AddHolderDetailsRepository extends JpaRepository<AddHolderDetails, String> {
    Optional<AddHolderDetails> findByUser_EmailAddressIgnoreCase(String email);
    default Optional<AddHolderDetails> findByUser_EmailAddress(String email) {
        return findByUser_EmailAddressIgnoreCase(email);
    }
    Optional<AddHolderDetails> findByUser_Id(String userId);
    Optional<AddHolderDetails> findByUser(User user);
}
