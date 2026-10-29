CREATE TABLE program_record_quarantine (
    id BIGSERIAL PRIMARY KEY,
    source_id TEXT,
    region_code TEXT,
    status TEXT,
    area_hectares TEXT,
    source_updated_at TEXT,
    rejection_reason VARCHAR(255) NOT NULL,
    quarantined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);