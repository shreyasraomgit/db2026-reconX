package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.ReconBreakResponse;
import com.dbtraining.reconx.dto.ReconRunRequest;
import com.dbtraining.reconx.exception.ReconJobNotFoundException;
import com.dbtraining.reconx.exception.TradeNotFoundException;
import com.dbtraining.reconx.repository.ReconBreakRepository;
import com.dbtraining.reconx.repository.ReconJobRepository;
import com.dbtraining.reconx.repository.entity.ReconBreak;
import com.dbtraining.reconx.repository.entity.ReconJob;
import com.dbtraining.reconx.service.ReconJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TICKET-ADV068 — POST /api/v1/recon/run — returns 202 + jobId
 * TICKET-ADV069 — GET  /api/v1/recon/jobs/{jobId}/results
 * TICKET-ADV070 — PUT  /api/v1/recon/results/{id}/resolve
 */
@RestController
@RequestMapping("/v1/recon")
@Tag(name = "recon", description = "Reconciliation operations")
@SecurityRequirement(name = "bearerAuth")
public class ReconController {

    private final ReconBreakRepository breaks;
    private final ReconJobRepository jobs;
    private final ReconJobService jobService;

    public ReconController(ReconBreakRepository breaks, ReconJobRepository jobs, ReconJobService jobService) {
        this.breaks = breaks;
        this.jobs = jobs;
        this.jobService = jobService;
    }

    @PostMapping("/run")
    @Operation(summary = "Trigger a reconciliation job (async)")
    public ResponseEntity<Map<String, String>> runRecon(@Valid @RequestBody ReconRunRequest req) {
        ReconJob job = new ReconJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setFromDate(req.from());
        job.setToDate(req.to());
        job.setStatus("QUEUED");
        job = jobs.save(job);

        jobService.process(job.getId());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("jobId", job.getJobId(), "status", "QUEUED"));
    }

    @GetMapping("/jobs/{jobId}/results")
    @Operation(summary = "Get results for a recon job")
    public List<ReconBreakResponse> results(@PathVariable String jobId) {
        // TICKET-ADV069 (fix) — real per-job scoping: 404 for an unknown
        // jobId, breaks filtered to the job that actually detected them
        // (instead of the original stub, which ignored jobId entirely and
        // returned every open break in the table for any input).
        ReconJob job = jobs.findByJobId(jobId).orElseThrow(() -> new ReconJobNotFoundException(jobId));
        return breaks.findByJobId(job.getId()).stream().map(ReconBreakResponse::from).toList();
    }

    @PutMapping("/results/{id}/resolve")
    @Operation(summary = "Mark a recon break as RESOLVED with a note")
    public ResponseEntity<ReconBreakResponse> resolve(@PathVariable Long id,
                                              @RequestBody Map<String, String> body) {
        ReconBreak rb = breaks.findById(id)
                .orElseThrow(() -> new TradeNotFoundException(id.toString()));
        rb.resolve(body.get("note"));
        return ResponseEntity.ok(ReconBreakResponse.from(breaks.save(rb)));
    }
}