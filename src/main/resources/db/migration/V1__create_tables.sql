-- V1: esquema inicial do finance-platform (MySQL 9.1)

CREATE TABLE users (
                       id          BIGINT       NOT NULL AUTO_INCREMENT,
                       google_id   VARCHAR(255) NOT NULL,
                       email       VARCHAR(255) NOT NULL,
                       name        VARCHAR(255) NOT NULL,
                       picture_url VARCHAR(500),
                       active      BIT(1)       NOT NULL DEFAULT b'1',
                       created_at  DATETIME(6)  NOT NULL,
                       updated_at  DATETIME(6)  NOT NULL,
                       PRIMARY KEY (id),
                       CONSTRAINT uk_users_google_id UNIQUE (google_id),
                       CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE accounts (
                          id              BIGINT         NOT NULL AUTO_INCREMENT,
                          user_id         BIGINT         NOT NULL,
                          name            VARCHAR(60)    NOT NULL,
                          type            VARCHAR(20)    NOT NULL,
                          initial_balance DECIMAL(19,2)  NOT NULL DEFAULT 0.00,
                          color           VARCHAR(7),
                          icon            VARCHAR(40),
                          archived        BIT(1)         NOT NULL DEFAULT b'0',
                          created_at      DATETIME(6)    NOT NULL,
                          updated_at      DATETIME(6)    NOT NULL,
                          PRIMARY KEY (id),
                          CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE categories (
                            id         BIGINT      NOT NULL AUTO_INCREMENT,
                            user_id    BIGINT      NOT NULL,
                            name       VARCHAR(60) NOT NULL,
                            type       VARCHAR(10) NOT NULL,
                            color      VARCHAR(7),
                            icon       VARCHAR(40),
                            deleted    BIT(1)      NOT NULL DEFAULT b'0',
                            created_at DATETIME(6) NOT NULL,
                            updated_at DATETIME(6) NOT NULL,
                            PRIMARY KEY (id),
                            CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE credit_cards (
                              id           BIGINT        NOT NULL AUTO_INCREMENT,
                              user_id      BIGINT        NOT NULL,
                              name         VARCHAR(60)   NOT NULL,
                              credit_limit DECIMAL(19,2) NOT NULL,
                              closing_day  INT           NOT NULL,
                              due_day      INT           NOT NULL,
                              color        VARCHAR(7),
                              icon         VARCHAR(40),
                              archived     BIT(1)        NOT NULL DEFAULT b'0',
                              created_at   DATETIME(6)   NOT NULL,
                              updated_at   DATETIME(6)   NOT NULL,
                              PRIMARY KEY (id),
                              CONSTRAINT fk_credit_cards_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (
                                id         BIGINT       NOT NULL AUTO_INCREMENT,
                                user_id    BIGINT       NOT NULL,
                                token_hash VARCHAR(255) NOT NULL,
                                expires_at DATETIME(6)  NOT NULL,
                                revoked    BIT(1)       NOT NULL DEFAULT b'0',
                                created_at DATETIME(6)  NOT NULL,
                                updated_at DATETIME(6)  NOT NULL,
                                PRIMARY KEY (id),
                                CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
                                CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE invoice_payments (
                                  id                BIGINT        NOT NULL AUTO_INCREMENT,
                                  user_id           BIGINT        NOT NULL,
                                  credit_card_id    BIGINT        NOT NULL,
                                  account_id        BIGINT        NOT NULL,
                                  invoice_reference DATE          NOT NULL,
                                  amount            DECIMAL(19,2) NOT NULL,
                                  date              DATE          NOT NULL,
                                  created_at        DATETIME(6)   NOT NULL,
                                  updated_at        DATETIME(6)   NOT NULL,
                                  PRIMARY KEY (id),
                                  CONSTRAINT fk_invoice_payments_user        FOREIGN KEY (user_id)        REFERENCES users (id),
                                  CONSTRAINT fk_invoice_payments_credit_card FOREIGN KEY (credit_card_id) REFERENCES credit_cards (id),
                                  CONSTRAINT fk_invoice_payments_account     FOREIGN KEY (account_id)     REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE goals (
                       id             BIGINT        NOT NULL AUTO_INCREMENT,
                       user_id        BIGINT        NOT NULL,
                       type           VARCHAR(10)   NOT NULL,
                       name           VARCHAR(100)  NOT NULL,
                       description    VARCHAR(255),
                       icon           VARCHAR(40)   DEFAULT 'target',
                       color          VARCHAR(7),
                       target_amount  DECIMAL(19,2) NOT NULL,
                       current_amount DECIMAL(19,2),
                       category_id    BIGINT,
                       start_date     DATE,
                       end_date       DATE,
                       status         VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
                       created_at     DATETIME(6)   NOT NULL,
                       updated_at     DATETIME(6)   NOT NULL,
                       PRIMARY KEY (id),
                       CONSTRAINT fk_goals_user     FOREIGN KEY (user_id)     REFERENCES users (id),
                       CONSTRAINT fk_goals_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE goal_contributions (
                                    id         BIGINT        NOT NULL AUTO_INCREMENT,
                                    goal_id    BIGINT        NOT NULL,
                                    amount     DECIMAL(19,2) NOT NULL,
                                    date       DATE          NOT NULL,
                                    notes      VARCHAR(255),
                                    created_at DATETIME(6)   NOT NULL,
                                    updated_at DATETIME(6)   NOT NULL,
                                    PRIMARY KEY (id),
                                    CONSTRAINT fk_goal_contributions_goal FOREIGN KEY (goal_id) REFERENCES goals (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transfers (
                           id              BIGINT        NOT NULL AUTO_INCREMENT,
                           user_id         BIGINT        NOT NULL,
                           from_account_id BIGINT        NOT NULL,
                           to_account_id   BIGINT        NOT NULL,
                           amount          DECIMAL(19,2) NOT NULL,
                           date            DATE          NOT NULL,
                           description     VARCHAR(255),
                           created_at      DATETIME(6)   NOT NULL,
                           updated_at      DATETIME(6)   NOT NULL,
                           PRIMARY KEY (id),
                           CONSTRAINT fk_transfers_user         FOREIGN KEY (user_id)         REFERENCES users (id),
                           CONSTRAINT fk_transfers_from_account FOREIGN KEY (from_account_id) REFERENCES accounts (id),
                           CONSTRAINT fk_transfers_to_account   FOREIGN KEY (to_account_id)   REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transactions (
                              id                   BIGINT        NOT NULL AUTO_INCREMENT,
                              user_id              BIGINT        NOT NULL,
                              account_id           BIGINT,
                              credit_card_id       BIGINT,
                              category_id          BIGINT        NOT NULL,
                              type                 VARCHAR(10)   NOT NULL,
                              amount               DECIMAL(19,2) NOT NULL,
                              date                 DATE          NOT NULL,
                              description          VARCHAR(255),
                              notes                VARCHAR(500),
                              invoice_reference    DATE,
                              installment_group_id VARCHAR(36),
                              installment_number   INT,
                              installment_total    INT,
                              deleted              BIT(1)        NOT NULL DEFAULT b'0',
                              created_at           DATETIME(6)   NOT NULL,
                              updated_at           DATETIME(6)   NOT NULL,
                              PRIMARY KEY (id),
                              CONSTRAINT fk_transactions_user        FOREIGN KEY (user_id)        REFERENCES users (id),
                              CONSTRAINT fk_transactions_account     FOREIGN KEY (account_id)     REFERENCES accounts (id),
                              CONSTRAINT fk_transactions_credit_card FOREIGN KEY (credit_card_id) REFERENCES credit_cards (id),
                              CONSTRAINT fk_transactions_category    FOREIGN KEY (category_id)    REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Índices para as consultas mais comuns
CREATE INDEX idx_transactions_user_date ON transactions (user_id, date);
CREATE INDEX idx_transactions_installment_group ON transactions (installment_group_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_goal_contributions_goal_date ON goal_contributions (goal_id, date);