--liquibase formatted sql

--changeset swedbank:002-create-idempotency-records

CREATE TABLE idempotency_records (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    operation_type  VARCHAR(50)  NOT NULL,
    response_body   CLOB         NOT NULL,
    CONSTRAINT uq_idempotency UNIQUE (idempotency_key, operation_type)
);

