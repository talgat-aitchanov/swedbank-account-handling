--liquibase formatted sql

--changeset swedbank:008-add-audit-to-idempotency-records
-- initiated_by: the username who triggered the operation (USER or ADMIN)
-- note: optional comment filled in by an admin (e.g. reason for manual adjustment)
ALTER TABLE idempotency_records
    ADD COLUMN initiated_by VARCHAR(100) NOT NULL DEFAULT 'system';
ALTER TABLE idempotency_records
    ADD COLUMN note VARCHAR(500);

