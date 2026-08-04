package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.PagedResponse;
import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.repository.entity.TradeStatus;
import com.dbtraining.reconx.service.TradeService;
import com.dbtraining.reconx.service.TradeStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

/**
 * ============================================================================
 * TICKET-ADV063-ADV067 — TradeController (full CRUD + filterable list)
 * TICKET-ADV080 — API versioning: every endpoint under /v1/
 *
 * Combined with the /api context-path from application.yml, full URLs are
 * /api/v1/trades, /api/v1/trades/{id} etc.
 *
 * NOTE: entity->DTO mapping lives in TradeService (inside its @Transactional
 * boundary), not here — instrument/counterparty are LAZY and open-in-view is
 * false, so mapping after the session closes throws LazyInitializationException.
 * ============================================================================
 */
@RestController
@RequestMapping("/v1/trades")
@Tag(name = "trades", description = "Trade CRUD and search")
@SecurityRequirement(name = "bearerAuth")
public class TradeController {

    private final TradeService service;
    private final TradeStreamService stream;

    public TradeController(TradeService service, TradeStreamService stream) {
        this.service = service;
        this.stream = stream;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live SSE feed of trade create/update events")
    public SseEmitter stream() {
        return stream.subscribe();
    }

    @GetMapping
    @Operation(summary = "List trades — paginated, filterable, sortable")
    public PagedResponse<TradeResponse> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) TradeStatus status,
            @RequestParam(required = false) Long counterpartyId,
            @RequestParam(required = false) String ref,
            @PageableDefault(size = 20, sort = "tradeDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(from, to, status, counterpartyId, ref, pageable);
    }

    @PostMapping
    @Operation(summary = "Create a trade")
    public ResponseEntity<TradeResponse> create(@Valid @RequestBody TradeRequest req,
                                                @AuthenticationPrincipal String actor) {
        TradeResponse saved = service.create(req, actor);
        URI location = URI.create("/api/v1/trades/" + saved.id());
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Full update of a trade")
    public TradeResponse update(@PathVariable Long id, @Valid @RequestBody TradeRequest req,
                                @AuthenticationPrincipal String actor) {
        return service.update(id, req, actor);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update only the status field",
            description = "Valid values: PENDING, MATCHED, UNMATCHED, DISPUTED, CANCELLED")
    public TradeResponse updateStatus(@PathVariable Long id,
                                      @RequestBody
                                      @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                              content = @Content(examples = @ExampleObject(value = "{\"status\": \"MATCHED\"}")))
                                      Map<String, String> body,
                                      @AuthenticationPrincipal String actor) {
        return service.updateStatus(id, body.get("status"), actor);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete (sets deleted_at)")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal String actor) {
        service.softDelete(id, actor);
        return ResponseEntity.noContent().build();
    }
}
