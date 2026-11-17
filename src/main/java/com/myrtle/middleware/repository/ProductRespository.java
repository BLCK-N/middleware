package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRespository extends JpaRepository<Product,String> {
    Optional<Product> findProductByCode(String code);
}
