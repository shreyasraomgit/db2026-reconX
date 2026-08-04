package com.dbtraining.reconx.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ============================================================================
 * TICKET-ADV053 — TradeRequest DTO (POST body)
 * TICKET-ADV029 — JSR-380 validation annotations live on the DTO, not the entity
 *
 * WHY:    Putting @Pattern/@Positive/@NotNull on the JPA entity couples
 *         persistence to wire format. The DTO is the wire contract; validate
 *         it before mapping.
 * ============================================================================
 */
public record TradeRequest(
        @Schema(example = "AAA-20260804-0001", description = "Must match AAA-YYYYMMDD-NNNN — 3 uppercase letters, date, 4-digit sequence")
        @NotNull
        @Pattern(regexp = "^[A-Z]{3}-\\d{8}-\\d{4}$",
                 message = "tradeRef must match AAA-YYYYMMDD-NNNN")
        String tradeRef,

        @Schema(example = "1", description = "Seeded instruments: 1=SAP.DE, 2=SIE.DE, 3=DBKGn.DE, 4=AAPL, 5=MSFT")
        @NotNull
        Long instrumentId,

        @Schema(example = "1", description = "Seeded counterparties: ids 1-5, see GET /v1/trades response for names")
        @NotNull
        Long counterpartyId,

        @Schema(example = "EQUITY")
        @NotBlank
        String assetClass,

        @Schema(example = "BUY", allowableValues = {"BUY", "SELL"})
        @NotBlank
        @Pattern(regexp = "^(BUY|SELL)$")
        String side,

        @Schema(example = "100")
        @NotNull @Positive
        BigDecimal quantity,

        @Schema(example = "245.50")
        @NotNull @Positive
        BigDecimal price,

        @Schema(example = "2026-06-02")
        @NotNull
        LocalDate tradeDate
) {}
