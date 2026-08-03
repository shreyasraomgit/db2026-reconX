package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.PagedResponse;
import com.dbtraining.reconx.dto.TradeMapper;
import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.exception.DuplicateTradeRefException;
import com.dbtraining.reconx.exception.InvalidTradeException;
import com.dbtraining.reconx.exception.TradeNotFoundException;
import com.dbtraining.reconx.kafka.TradeEventProducer;
import com.dbtraining.reconx.observability.TradeMetrics;
import com.dbtraining.reconx.repository.CounterpartyRepository;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.repository.TradeRepository;
import com.dbtraining.reconx.repository.entity.Trade;
import com.dbtraining.reconx.repository.entity.TradeStatus;
import com.dbtraining.reconx.dto.TradeEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.dbtraining.reconx.repository.TradeSpecifications.*;

/**
 * ============================================================================
 * TICKET-ADV064 — TradeService.create (POST endpoint backing)
 * TICKET-ADV065 — update
 * TICKET-ADV066 — updateStatus (PATCH)
 * TICKET-ADV067 — softDelete
 * TICKET-ADV083 — increments trade_created_total Counter on create
 * TICKET-ADV129 — publishes TradeEvent on every state change
 * TICKET-ADV055/ADV056 — list() uses Specifications + filter query
 *
 * NOTE: response mapping (TradeMapper.toResponse) happens INSIDE these
 * transactional methods, not in the controller. instrument/counterparty are
 * @ManyToOne(LAZY) — with open-in-view=false (ADV049), touching those
 * proxies after the transaction/session has closed throws
 * LazyInitializationException. Mapping to the flat DTO while the session is
 * still open is what keeps the LAZY fetch safe.
 * ============================================================================
 */
@Service
@Transactional
public class TradeService {

    private final TradeRepository tradeRepo;
    private final CounterpartyRepository cpRepo;
    private final InstrumentRepository instRepo;
    private final TradeEventProducer events;
    private final TradeMetrics metrics;
    private final TradeMapper mapper;

    public TradeService(TradeRepository tradeRepo,
                        CounterpartyRepository cpRepo,
                        InstrumentRepository instRepo,
                        TradeEventProducer events,
                        TradeMetrics metrics,
                        TradeMapper mapper) {
        this.tradeRepo = tradeRepo;
        this.cpRepo = cpRepo;
        this.instRepo = instRepo;
        this.events = events;
        this.metrics = metrics;
        this.mapper = mapper;
    }

    public TradeResponse create(TradeRequest req, String actor) {
        tradeRepo.findByTradeRef(req.tradeRef()).ifPresent(t -> {
            throw new DuplicateTradeRefException(req.tradeRef());
        });
        var instrument = instRepo.findById(req.instrumentId())
                .orElseThrow(() -> new TradeNotFoundException("instrument " + req.instrumentId()));
        var counterparty = cpRepo.findById(req.counterpartyId())
                .orElseThrow(() -> new TradeNotFoundException("counterparty " + req.counterpartyId()));

        var t = new Trade();
        t.setTradeRef(req.tradeRef());
        t.setInstrument(instrument);
        t.setCounterparty(counterparty);
        t.setAssetClass(req.assetClass());
        t.setSide(req.side());
        t.setQuantity(req.quantity());
        t.setPrice(req.price());
        t.setTradeDate(req.tradeDate());
        t.setStatus(TradeStatus.PENDING);
        Trade saved = tradeRepo.save(t);

        metrics.incrementTradeCreated();
        metrics.recordTradeValue(req.quantity().multiply(req.price()).doubleValue());
        events.publish(new TradeEvent(UUID.randomUUID(), saved.getTradeRef(),
                TradeEvent.EventType.TRADE_CREATED, Instant.now(), actor,
                null, "status=PENDING"));
        return mapper.toResponse(saved);
    }

    public TradeResponse update(Long id, TradeRequest req, String actor) {
        var t = tradeRepo.findById(id)
                .orElseThrow(() -> new TradeNotFoundException("id " + id));
        String before = "status=" + t.getStatus() + ",qty=" + t.getQuantity() + ",price=" + t.getPrice();

        t.setAssetClass(req.assetClass());
        t.setSide(req.side());
        t.setQuantity(req.quantity());
        t.setPrice(req.price());
        t.setTradeDate(req.tradeDate());
        Trade saved = tradeRepo.save(t);

        events.publish(new TradeEvent(UUID.randomUUID(), saved.getTradeRef(),
                TradeEvent.EventType.TRADE_UPDATED, Instant.now(), actor,
                before, "qty=" + saved.getQuantity() + ",price=" + saved.getPrice()));
        return mapper.toResponse(saved);
    }

    public TradeResponse updateStatus(Long id, String status, String actor) {
        var t = tradeRepo.findById(id)
                .orElseThrow(() -> new TradeNotFoundException("id " + id));
        TradeStatus newStatus;
        try {
            newStatus = TradeStatus.valueOf(status == null ? "" : status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidTradeException("Unknown trade status: " + status);
        }
        String before = "status=" + t.getStatus();
        t.setStatus(newStatus);
        Trade saved = tradeRepo.save(t);

        events.publish(new TradeEvent(UUID.randomUUID(), saved.getTradeRef(),
                TradeEvent.EventType.TRADE_UPDATED, Instant.now(), actor,
                before, "status=" + newStatus));
        return mapper.toResponse(saved);
    }

    public void softDelete(Long id, String actor) {
        var t = tradeRepo.findById(id)
                .orElseThrow(() -> new TradeNotFoundException("id " + id));
        t.softDelete();
        tradeRepo.save(t);

        events.publish(new TradeEvent(UUID.randomUUID(), t.getTradeRef(),
                TradeEvent.EventType.TRADE_CANCELLED, Instant.now(), actor,
                "deleted_at=null", "deleted_at=" + t.getDeletedAt()));
    }

    @Transactional(readOnly = true)
    public PagedResponse<TradeResponse> list(LocalDate from, LocalDate to, TradeStatus status, Long counterpartyId,
                            String ref, Pageable pageable) {
        Specification<Trade> spec = Specification
                .where(tradeDateBetween(from, to))
                .and(hasStatus(status))
                .and(hasCounterparty(counterpartyId))
                .and(refLike(ref));
        Page<Trade> page = tradeRepo.findAll(spec, pageable);
        return PagedResponse.from(page, mapper::toResponse);
    }
}
