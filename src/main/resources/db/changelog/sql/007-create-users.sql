--liquibase formatted sql

--changeset swedbank:007-create-users
CREATE TABLE app_users
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_users_username UNIQUE (username)
);

--changeset swedbank:007-seed-users context:local,test
-- Seed accounts for local development and tests.
-- Passwords in plain text (for reference only) → BCrypt hash stored in DB:
--   user      plain: "password"
--   admin     plain: "admin123"
-- To regenerate: new BCryptPasswordEncoder().encode("<plain>") with default strength 10.
INSERT INTO app_users (username, password_hash, role)
VALUES ('user', '$2a$10$GoXkY9hoGLEGx0MJn3kLLeimUpJCmhknZ.6IO0DjDHRBZKZXZi11q', 'USER'),
       ('admin', '$2a$10$cVLTA71Nt0OAo2JMoMzCG.5vzHl9bQV/Pz3B9VhOCArO77uPubtR2', 'ADMIN');


