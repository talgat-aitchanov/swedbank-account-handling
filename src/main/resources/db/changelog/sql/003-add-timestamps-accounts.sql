--liquibase formatted sql

--changeset swedbank:003-add-timestamps-accounts
ALTER TABLE accounts
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

