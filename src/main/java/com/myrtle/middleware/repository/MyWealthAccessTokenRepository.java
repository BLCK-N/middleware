package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.MyWealthAccessToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MyWealthAccessTokenRepository extends CrudRepository<MyWealthAccessToken, Integer> {
}
