package com.dbtraining.reconx.repository.entity;

/**
 * TICKET-ADV050 — Trade lifecycle status.
 *
 * Stored via @Enumerated(EnumType.STRING) so reordering these constants
 * never re-points existing rows (the ORDINAL trap).
 */
public enum TradeStatus {
    PENDING,
    MATCHED,
    UNMATCHED,
    DISPUTED,
    CANCELLED
}
