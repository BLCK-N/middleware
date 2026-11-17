package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.TxnAuditLogs;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TxnLogRepo extends CrudRepository<TxnAuditLogs, String> {
}
