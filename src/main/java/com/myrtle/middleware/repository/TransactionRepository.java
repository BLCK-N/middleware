package com.myrtle.middleware.repository;

import com.myrtle.middleware.enums.TransactionType;
import com.myrtle.middleware.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, String>, JpaSpecificationExecutor<Transaction> {
    @EntityGraph(attributePaths = {"investment", "investment.product"})
    Optional<Transaction> findByTxRef(String txRef);
    List<Transaction> findByUserId(String userId);
    Page<Transaction> findByUserId(String userId, Pageable pageable);
    Page<Transaction> findByUserIdAndType(String userId, TransactionType type, Pageable pageable);
    List<Transaction> findByUserIdAndCreatedAtBetween(String userId, LocalDateTime start, LocalDateTime end);
}
