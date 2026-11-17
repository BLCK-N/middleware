package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.CorporateNextOfKin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorporateNextOfKinRepository extends JpaRepository<CorporateNextOfKin, String> {
    List<CorporateNextOfKin> findByCorporateClient_Id(String corporateClientId);
    boolean existsByCorporateClient_IdAndEmailIgnoreCase(String corporateClientId, String email);
    boolean existsByCorporateClient_IdAndMobileNo(String corporateClientId, String mobileNo);
}
