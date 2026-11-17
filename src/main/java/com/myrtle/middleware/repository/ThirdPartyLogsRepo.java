package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.ThirdPartyActivityLog;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ThirdPartyLogsRepo extends CrudRepository<ThirdPartyActivityLog, String> {
}
