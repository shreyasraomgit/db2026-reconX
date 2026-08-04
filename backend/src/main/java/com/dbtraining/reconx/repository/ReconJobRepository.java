package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.ReconJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReconJobRepository extends JpaRepository<ReconJob, Long> {
    Optional<ReconJob> findByJobId(String jobId);
}
