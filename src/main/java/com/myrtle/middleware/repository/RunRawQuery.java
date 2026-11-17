package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.Investment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RunRawQuery extends CrudRepository<Investment, String> {

    @Query(nativeQuery = true, value = """
            select count(*) from investments
            where investments.product_id:productId AND
            investments.user_id =:userId
            """)
    int countByProduct_IdAndUser_Id(String productId, String userId);
}
