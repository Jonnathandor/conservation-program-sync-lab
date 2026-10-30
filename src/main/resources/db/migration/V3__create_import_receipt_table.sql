CREATE TABLE import_receipt (
    id UUID PRIMARY KEY,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    received INTEGER NOT NULL CHECK (received >= 0),
    created INTEGER NOT NULL CHECK (created >= 0),
    updated INTEGER NOT NULL CHECK (updated >= 0),
    unchanged INTEGER NOT NULL CHECK (unchanged >= 0),
    rejected INTEGER NOT NULL CHECK (rejected >= 0),
    CONSTRAINT import_receipt_counts_match CHECK (
        received = created::BIGINT + updated + unchanged + rejected
    )
);
