package com.yassin.datalens.repository;

import com.yassin.datalens.model.LogEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogEventRepository extends JpaRepository<LogEvent, Long> {
}
