package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.exception.DuplicateTradeRefException;
import com.dbtraining.reconx.exception.TradeNotFoundException;
import com.dbtraining.reconx.kafka.TradeEventProducer;
import com.dbtraining.reconx.observability.TradeMetrics;
import com.dbtraining.reconx.repository.CounterpartyRepository;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.repository.TradeRepository;
import com.dbtraining.reconx.repository.entity.Trade;
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

    public TradeService(TradeRepository tradeRepo,
                        CounterpartyRepository cpRepo,
                        InstrumentRepository instRepo,
                        TradeEventProducer events,
                        TradeMetrics metrics) {
        this.tradeRepo = tradeRepo;
        this.cpRepo = cpRepo;
        this.instRepo = instRepo;
        this.events = events;
        this.metrics = metrics;
    }

    public Trade create(TradeRequest req, String actor) {
        tradeRepo.findByTradeRef(req.tradeRef()).ifPresent(t -> {
            throw new DuplicateTradeRefException(req.tradeRef());
        });
        var instrument = instRepo.findById(req.instrumentId())
                .orElseThrow(() -> new TradeNotFoundException("instrument " + req.instrumentId()));
        var counterparty = cpRepo.findById(req.counterpartyId())
                .orElseThrow(() -> new TradeNotFoundException("counterparty " + req.counterpartyId()));

        var trade = new Trade();
        trade.setTradeRef(req.tradeRef());
        trade.setInstrument(instrument);
        trade.setCounterparty(counterparty);
        trade.setAssetClass(req.assetClass());
        trade.setSide(req.side());
        trade.setQuantity(req.quantity());
        trade.setPrice(req.price());
        trade.setTradeDate(req.tradeDate());
        trade.setStatus("PENDING");
        Trade saved = tradeRepo.save(trade);

        metrics.incrementTradeCreated();
        metrics.recordTradeValue(req.quantity().multiply(req.price()).doubleValue());
        events.publish(new TradeEvent(UUID.randomUUID(), saved.getTradeRef(),
                TradeEvent.EventType.TRADE_CREATED, Instant.now(), actor,
                null, "status=PENDING"));
        return saved;
    }

    public Trade update(Long id, TradeRequest req, String actor) {
        var trade = tradeRepo.findById(id)
                .orElseThrow(() -> new TradeNotFoundException("id " + id));
        String before = "status=" + trade.getStatus() + ",qty=" + trade.getQuantity() + ",price=" + trade.getPrice();
        trade.setAssetClass(req.assetClass());
        trade.setSide(req.side());
        trade.setQuantity(req.quantity());
        trade.setPrice(req.price());
        trade.setTradeDate(req.tradeDate());
        Trade saved = tradeRepo.save(trade);
        events.publish(new TradeEvent(UUID.randomUUID(), saved.getTradeRef(),
                TradeEvent.EventType.TRADE_UPDATED, Instant.now(), actor,
                before, "qty=" + saved.getQuantity() + ",price=" + saved.getPrice()));
        return saved;
    }

    public Trade updateStatus(Long id, String status, String actor) {
        var trade = tradeRepo.findById(id)
                .orElseThrow(() -> new TradeNotFoundException("id " + id));
        String before = "status=" + trade.getStatus();
        trade.setStatus(status);
        Trade saved = tradeRepo.save(trade);
        events.publish(new TradeEvent(UUID.randomUUID(), saved.getTradeRef(),
                TradeEvent.EventType.TRADE_UPDATED, Instant.now(), actor,
                before, "status=" + status));
        return saved;
    }

    public void softDelete(Long id, String actor) {
        var trade = tradeRepo.findById(id)
                .orElseThrow(() -> new TradeNotFoundException("id " + id));
        trade.softDelete();
        tradeRepo.save(trade);
        events.publish(new TradeEvent(UUID.randomUUID(), trade.getTradeRef(),
                TradeEvent.EventType.TRADE_CANCELLED, Instant.now(), actor,
                "deleted_at=null", "deleted_at=" + trade.getDeletedAt()));
    }

    @Transactional(readOnly = true)
    public Page<Trade> list(LocalDate from, LocalDate to, String status, Long counterpartyId, Pageable pageable) {
        Specification<Trade> specification = Specification
                .where(tradeDateBetween(from, to))
                .and(hasStatus(status))
                .and(hasCounterparty(counterpartyId));
        return tradeRepo.findAll(specification, pageable);
    }
}
