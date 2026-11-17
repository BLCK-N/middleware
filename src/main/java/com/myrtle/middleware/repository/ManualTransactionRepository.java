package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.ManualTransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManualTransactionRepository extends JpaRepository<ManualTransactionEntity, String> {
    List<ManualTransactionEntity> findByUserIdAndProductId(String userId, String productId);
    Optional<ManualTransactionEntity> findFirstByUserIdAndProductIdOrderByTxnTimeDesc(String userId, String productId);
    Page<ManualTransactionEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
