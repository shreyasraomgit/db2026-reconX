package com.dbtraining.reconx.repository.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * TICKET-ADV051 — JPA entity Instrument. JSONB metadata column mapped via
 * Hibernate's native SqlTypes.JSON — true jsonb on Postgres, H2's native
 * JSON column type in dev. (hypersistence-utils' JsonBinaryType was tried
 * first per the ticket's Hint 2, but its H2 extractor does not round-trip
 * H2 2.x's native JSON JDBC representation on this Hibernate version —
 * Hibernate 6's own SqlTypes.JSON mapping is portable across both dialects
 * and achieves the same "typed JSONB field" outcome.)
 */
@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "asset_class", nullable = false, length = 20)
    private String assetClass;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 12)
    private String isin;

    /**
     * JSONB metadata: tick size, lot size, exchange code, etc.
     * On H2 (dev profile) this stores as a CLOB; on Postgres it's true JSONB
     * and is queryable via the @> operator.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    public Instrument() {}

    public Long getId()         { return id; }
    public String getSymbol()   { return symbol; }
    public String getName()     { return name; }
    public String getAssetClass(){ return assetClass; }
    public String getCurrency() { return currency; }
    public String getIsin()     { return isin; }
    public Map<String, Object> getMetadata() { return metadata; }

    public void setSymbol(String v)    { this.symbol = v; }
    public void setName(String v)      { this.name = v; }
    public void setAssetClass(String v){ this.assetClass = v; }
    public void setCurrency(String v)  { this.currency = v; }
    public void setIsin(String v)      { this.isin = v; }
    public void setMetadata(Map<String, Object> v) { this.metadata = v; }
}
