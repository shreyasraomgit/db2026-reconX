package com.dbtraining.reconx.dto;

import com.dbtraining.reconx.repository.entity.ReconBreak;

import java.time.Instant;

/**
 * Wire shape for ReconBreak — controllers must never return the JPA entity
 * directly (see Day 4 end-of-day checklist: "No JPA entity types appear in
 * any controller return signature").
 */
public record ReconBreakResponse(
        Long id,
        Long tradeId,
        String discrepancyType,
        String status,
        Instant detectedAt,
        Instant resolvedAt,
        String resolutionNote
) {
    public static ReconBreakResponse from(ReconBreak b) {
        return new ReconBreakResponse(
                b.getId(),
                b.getTradeId(),
                b.getDiscrepancyType(),
                b.getStatus(),
                b.getDetectedAt(),
                b.getResolvedAt(),
                b.getResolutionNote()
        );
    }
}
