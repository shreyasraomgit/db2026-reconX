package com.dbtraining.reconx.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * TICKET-ADV072 — POST /api/auth/login body.
 *
 * Seeded demo users (see RUNNING-COMMANDS.md): admin@db.com/admin123 (ADMIN),
 * trader@db.com/trader123 (TRADER), viewer@db.com/viewer123 (VIEWER),
 * recon@db.com/recon123 (RECON_ANALYST).
 */
public record LoginRequest(
        @Schema(example = "trader@db.com") @Email @NotBlank String email,
        @Schema(example = "trader123") @NotBlank String password) {}
