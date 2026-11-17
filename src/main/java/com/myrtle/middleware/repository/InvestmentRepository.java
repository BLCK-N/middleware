package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.Investment;
import com.myrtle.middleware.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvestmentRepository extends JpaRepository<Investment,String> , JpaSpecificationExecutor<Investment> {
    Page<Investment> findByUserId(String userId, Pageable pageable);
    Page<Investment> findByProduct_IdAndUser_Id(String productId, String userId, Pageable pageable);
    List<Investment> findByUser_IdAndProduct_IdAndInvestmentStatus(String userId,String productId, Investment.InvestmentStatus investmentStatus);
    Optional<Investment> findByRefCode(String refCode);

    @Query("SELECT DISTINCT i.product FROM Investment i WHERE i.user.id = :userId")
    List<Product> findDistinctProductsByUser_Id(@Param("userId") String userId);
}
