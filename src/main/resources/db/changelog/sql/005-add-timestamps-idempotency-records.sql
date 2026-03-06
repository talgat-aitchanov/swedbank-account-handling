--liquibase formatted sql

--changeset swedbank:005-add-timestamps-idempotency-records
ALTER TABLE idempotency_records
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

