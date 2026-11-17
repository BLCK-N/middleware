package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.PendingPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingPaymentRepository extends JpaRepository<PendingPayment, String> {

    Optional<PendingPayment> findByTxRef(String txRef);

    boolean existsByTxRef(String txRef);
}
