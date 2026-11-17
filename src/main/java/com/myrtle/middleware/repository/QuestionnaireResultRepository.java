package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.QuestionnaireResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface QuestionnaireResultRepository
        extends JpaRepository<QuestionnaireResult, String>, JpaSpecificationExecutor<QuestionnaireResult> {

    Optional<QuestionnaireResult> findFirstByClientEmailIgnoreCaseAndDeletedFalseOrderByVersionDesc(String clientEmail);

    List<QuestionnaireResult> findByClientEmailIgnoreCaseAndDeletedFalseOrderByVersionDesc(String clientEmail);

    @Query("select coalesce(max(r.version), 0) from QuestionnaireResult r "
            + "where lower(r.clientEmail) = lower(:clientEmail)")
    int findMaxVersionByClientEmail(@Param("clientEmail") String clientEmail);

    @Transactional
    @Modifying
    @Query("update QuestionnaireResult r set r.latest = false "
            + "where lower(r.clientEmail) = lower(:clientEmail) and r.latest = true")
    void clearLatestFlag(@Param("clientEmail") String clientEmail);

}
