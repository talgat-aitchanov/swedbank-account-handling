--liquibase formatted sql

--changeset swedbank:004-add-timestamps-account-balances
ALTER TABLE account_balances
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE account_balances
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

