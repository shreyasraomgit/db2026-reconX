package com.dbtraining.reconx.service;

import com.dbtraining.reconx.repository.TradeRepository;
import com.dbtraining.reconx.repository.entity.ReconJob;
import com.dbtraining.reconx.repository.ReconJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * ============================================================================
 * TICKET-ADV069 (fix) — Real persistence + processing for POST /v1/recon/run.
 *
 * WHAT:    Runs a queued ReconJob off the request thread: counts internal
 *          trades in the job's date range and marks the job COMPLETED.
 * WHY:     There is no external (counterparty-side) trade feed anywhere in
 *          this system yet — every Trade row is internal. ReconciliationEngine
 *          needs both an internal and an external list to produce MATCHED/
 *          BREAK results, so this deliberately does NOT fabricate breaks by
 *          comparing internal trades against themselves. breaksDetected stays
 *          0 until a real external feed exists. What this DOES fix is the
 *          original stub: jobs are now persisted and job-scoped, instead of
 *          GET /jobs/{jobId}/results ignoring jobId and returning every open
 *          break in the table regardless of which job (or whether any job)
 *          produced it.
 * ============================================================================
 */
@Service
public class ReconJobService {

    private static final Logger log = LoggerFactory.getLogger(ReconJobService.class);

    private final ReconJobRepository jobs;
    private final TradeRepository trades;

    public ReconJobService(ReconJobRepository jobs, TradeRepository trades) {
        this.jobs = jobs;
        this.trades = trades;
    }

    @Async
    public void process(Long jobPk) {
        ReconJob job = jobs.findById(jobPk).orElse(null);
        if (job == null) return;

        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now());
        jobs.save(job);

        try {
            long processed = trades.findByFilters(job.getFromDate(), job.getToDate(), null, null, Pageable.unpaged())
                    .getTotalElements();
            job.setTradesProcessed((int) processed);
            job.setBreaksDetected(0);
            job.setStatus("COMPLETED");
        } catch (Exception ex) {
            log.error("Recon job {} failed", job.getJobId(), ex);
            job.setStatus("FAILED");
        } finally {
            job.setFinishedAt(Instant.now());
            jobs.save(job);
        }
    }
}
