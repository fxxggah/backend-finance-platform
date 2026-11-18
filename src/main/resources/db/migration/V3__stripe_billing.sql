CREATE TABLE billing_customers (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE,
 reference_uuid VARCHAR(36) COLLATE utf8mb4_bin NOT NULL UNIQUE,
 stripe_customer_id VARCHAR(255) COLLATE utf8mb4_bin UNIQUE,
 customer_started_at DATETIME(6) NOT NULL,
 checkout_key_hash CHAR(64), checkout_operation VARCHAR(36), checkout_id VARCHAR(255), checkout_url VARCHAR(2048),
 checkout_expires_at DATETIME(6), checkout_fingerprint CHAR(64),
 sync_token VARCHAR(36), lease_until DATETIME(6), next_sync_at DATETIME(6) NOT NULL,
 deleting BIT(1) NOT NULL DEFAULT b'0',
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_billing_customer_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 INDEX idx_billing_customer_sync (next_sync_at, lease_until)
);
CREATE TABLE billing_subscriptions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 stripe_subscription_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL UNIQUE,
 status VARCHAR(32) NOT NULL, cancel_at_period_end BIT(1) NOT NULL,
 current_period_end DATETIME(6), created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_billing_subscription_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 INDEX idx_billing_subscription_user (user_id, status)
);
CREATE TABLE billing_invoices (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 stripe_invoice_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL UNIQUE,
 stripe_subscription_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
 stripe_charge_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL UNIQUE,
 amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
 period_start DATETIME(6) NOT NULL, period_end DATETIME(6) NOT NULL,
 revoked BIT(1) NOT NULL, review_required BIT(1) NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_billing_invoice_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT ck_billing_invoice_period CHECK (period_end > period_start AND amount > 0),
 INDEX idx_billing_invoice_access (user_id, revoked, period_end)
);
CREATE TABLE billing_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 stripe_event_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL UNIQUE,
 event_type VARCHAR(100) NOT NULL, resource_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
 status VARCHAR(16) NOT NULL, attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6) NOT NULL, lease_until DATETIME(6), token VARCHAR(36),
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT ck_billing_event_status CHECK (status IN ('PENDING','PROCESSING','DONE')),
 INDEX idx_billing_event_pending (status, next_attempt_at, lease_until)
);
