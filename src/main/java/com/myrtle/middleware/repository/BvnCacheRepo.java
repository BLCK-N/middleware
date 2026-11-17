package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.BvnCache;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BvnCacheRepo extends CrudRepository<BvnCache, String> {

    List<BvnCache> bvn(String bvn);

    BvnCache findBvnCacheByBvn(String bvn);
}
