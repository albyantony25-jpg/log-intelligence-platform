package com.logplatform.repository;

import com.logplatform.model.LogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link LogEntry}.
 *
 * By extending JpaRepository<LogEntry, Long>, Spring auto-generates at runtime:
 *   - save(entity)        – INSERT or UPDATE
 *   - findById(id)        – SELECT by primary key
 *   - findAll()           – SELECT * FROM log_entries
 *   - deleteById(id)      – DELETE by primary key
 *   - count()             – SELECT COUNT(*)
 *   …and many more standard CRUD operations.
 *
 * No implementation class is needed – Spring Data provides a dynamic proxy.
 *
 * Custom query methods can be added here using Spring Data's derived query
 * syntax, e.g.:
 *   List<LogEntry> findByLogLevel(String logLevel);
 *   List<LogEntry> findByServiceNameAndLogLevel(String serviceName, String level);
 */
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Repository
public interface LogEntryRepository extends JpaRepository<LogEntry, Long>, JpaSpecificationExecutor<LogEntry> {
}
