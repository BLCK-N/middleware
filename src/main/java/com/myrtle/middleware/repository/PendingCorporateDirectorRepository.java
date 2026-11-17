package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.PendingCorporateDirector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PendingCorporateDirectorRepository extends JpaRepository<PendingCorporateDirector, String> {
    List<PendingCorporateDirector> findByPendingCorporateDataId(String pendingCorporateDataId);

    void deleteByPendingCorporateDataId(String pendingCorporateDataId);
}
