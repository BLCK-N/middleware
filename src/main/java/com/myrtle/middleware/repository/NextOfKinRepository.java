package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.NextOfKin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NextOfKinRepository extends JpaRepository<NextOfKin, String> {
    List<NextOfKin> findByUser_Id(String userId);
    List<NextOfKin> findByCorporateClient_Id(String corporateClientId);
    long countByUser_Id(String userId);
    long countByCorporateClient_Id(String corporateClientId);
    boolean existsByUser_IdAndPhoneNumber(String userId, String phoneNumber);
    boolean existsByCorporateClient_IdAndPhoneNumber(String corporateClientId, String phoneNumber);
    boolean existsByUser_IdAndPhoneNumberAndIdNot(String userId, String phoneNumber, String id);
    Optional<NextOfKin> findByIdAndUser_Id(String id, String userId);
}
