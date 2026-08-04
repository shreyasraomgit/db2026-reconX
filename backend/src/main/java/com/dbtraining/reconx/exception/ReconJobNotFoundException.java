package com.dbtraining.reconx.exception;

/** TICKET-ADV069 (fix) — 404 Not Found: jobId has no row in recon_jobs. */
public class ReconJobNotFoundException extends ReconException {
    public ReconJobNotFoundException(String jobId) {
        super("Recon job not found: " + jobId);
    }
}
