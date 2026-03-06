--liquibase formatted sql

--changeset swedbank:001-create-accounts
CREATE TABLE accounts
(
    id BIGINT AUTO_INCREMENT PRIMARY KEY
);
CREATE TABLE account_balances
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT         NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    amount     DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    CONSTRAINT fk_account_balances_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT uq_account_currency UNIQUE (account_id, currency)
);
