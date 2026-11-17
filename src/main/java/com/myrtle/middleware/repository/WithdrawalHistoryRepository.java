package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.WithdrawalHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WithdrawalHistoryRepository extends JpaRepository<WithdrawalHistory, String> {
    Page<WithdrawalHistory> findByUser_idAndProductId(String userId, String productId, Pageable pageable);

    List<WithdrawalHistory> findByUser_Id(String userId);
}