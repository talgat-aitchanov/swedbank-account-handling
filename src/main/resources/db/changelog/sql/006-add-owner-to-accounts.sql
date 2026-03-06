--liquibase formatted sql

--changeset swedbank:006-add-owner-to-accounts
ALTER TABLE accounts
    ADD COLUMN owner_username VARCHAR(255);

