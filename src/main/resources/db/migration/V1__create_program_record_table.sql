CREATE TABLE program_record (
    id BIGSERIAL PRIMARY KEY,
    source_id VARCHAR(100) NOT NULL,
    region_code VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    area_hectares NUMERIC(12, 2) NOT NULL,
    source_updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_program_record_source_id UNIQUE (source_id)
);