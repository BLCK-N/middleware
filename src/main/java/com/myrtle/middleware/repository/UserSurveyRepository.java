package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.User;
import com.myrtle.middleware.model.UserSurvey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSurveyRepository extends JpaRepository<UserSurvey, String> {
    Optional<UserSurvey> findByUser_EmailAddressIgnoreCase(String email);
    Optional<UserSurvey> findByUser_Id(String userId);
    default Optional<UserSurvey> findByUser_EmailAddress(String email) {
        return findByUser_EmailAddressIgnoreCase(email);
    }
    Optional<UserSurvey> findByUser(User user);
}
