package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.CorporateDirector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorporateDirectorRepository extends JpaRepository<CorporateDirector, String> {

    List<CorporateDirector> findByCorporateClientId(String corporateClientId);

    boolean existsByCorporateClientId(String corporateClientId);
}
