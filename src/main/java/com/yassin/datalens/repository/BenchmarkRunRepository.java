package com.yassin.datalens.repository;

import com.yassin.datalens.model.BenchmarkRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {
}
