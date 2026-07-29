-- ============================================================================
-- TICKET-ADV008 — mv_daily_recon_summary
-- ============================================================================

CREATE MATERIALIZED VIEW mv_daily_recon_summary AS
SELECT
    t.trade_date,
    c.region,
    t.asset_class,
    COUNT(DISTINCT t.id)                                            AS total_trades,
    COUNT(DISTINCT t.id) FILTER (WHERE t.status = 'MATCHED')        AS matched_trades,
    COUNT(DISTINCT rb.id) FILTER (WHERE rb.status <> 'RESOLVED')    AS open_breaks,
    ROUND(COALESCE(SUM(t.quantity * t.price), 0)::NUMERIC, 2)       AS gross_notional,
    ROUND(
        100.0 * COUNT(DISTINCT t.id) FILTER (WHERE t.status = 'MATCHED')
        / NULLIF(COUNT(DISTINCT t.id), 0)
    , 2)                                                             AS match_rate_pct
FROM trades t
JOIN counterparties c ON c.id = t.counterparty_id
JOIN instruments i ON i.id = t.instrument_id
LEFT JOIN recon_breaks rb ON rb.trade_id = t.id
WHERE t.deleted_at IS NULL
GROUP BY t.trade_date, c.region, t.asset_class
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_daily_recon_summary
    ON mv_daily_recon_summary (trade_date, region, asset_class);

-- First refresh must be plain (view created WITH NO DATA).
REFRESH MATERIALIZED VIEW mv_daily_recon_summary;

-- Subsequent refreshes (e.g. EOD batch) use CONCURRENTLY so dashboards
-- are never blocked:
-- REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_recon_summary;