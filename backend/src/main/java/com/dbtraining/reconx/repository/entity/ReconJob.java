package com.dbtraining.reconx.repository.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * TICKET-ADV069 (fix) — Persistent tracking for a POST /v1/recon/run job.
 * Maps to the recon_jobs table (006-audit-and-recon.xml). Status transitions:
 * QUEUED -> RUNNING -> COMPLETED (or FAILED).
 */
@Entity
@Table(name = "recon_jobs")
public class ReconJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, unique = true, length = 36)
    private String jobId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(nullable = false, length = 20)
    private String status = "QUEUED";

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "trades_processed")
    private Integer tradesProcessed = 0;

    @Column(name = "breaks_detected")
    private Integer breaksDetected = 0;

    public ReconJob() {}

    public Long getId()                    { return id; }
    public String getJobId()               { return jobId; }
    public LocalDate getFromDate()         { return fromDate; }
    public LocalDate getToDate()           { return toDate; }
    public String getStatus()              { return status; }
    public Instant getStartedAt()          { return startedAt; }
    public Instant getFinishedAt()         { return finishedAt; }
    public Integer getTradesProcessed()    { return tradesProcessed; }
    public Integer getBreaksDetected()     { return breaksDetected; }

    public void setJobId(String v)             { this.jobId = v; }
    public void setFromDate(LocalDate v)       { this.fromDate = v; }
    public void setToDate(LocalDate v)         { this.toDate = v; }
    public void setStatus(String v)            { this.status = v; }
    public void setStartedAt(Instant v)        { this.startedAt = v; }
    public void setFinishedAt(Instant v)       { this.finishedAt = v; }
    public void setTradesProcessed(Integer v)  { this.tradesProcessed = v; }
    public void setBreaksDetected(Integer v)   { this.breaksDetected = v; }
}
