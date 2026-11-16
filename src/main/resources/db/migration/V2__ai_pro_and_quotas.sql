ALTER TABLE users ADD COLUMN pro_expires_at DATETIME(6) NULL;

CREATE TABLE ai_quota_guards (
    id BIGINT NOT NULL PRIMARY KEY,
    blocked_until DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_ai_guard_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO ai_quota_guards (id, created_at, updated_at) VALUES (1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

CREATE TABLE ai_usage_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    key_hash CHAR(64) NOT NULL,
    audio_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reserved_at DATETIME(6) NOT NULL,
    lease_until DATETIME(6) NOT NULL,
    success_day DATE NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ai_request_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_ai_request_key UNIQUE (user_id, key_hash),
    CONSTRAINT ck_ai_request_status CHECK (status IN ('PROCESSING','SUCCEEDED','FAILED')),
    CONSTRAINT ck_ai_success_day CHECK ((status = 'SUCCEEDED' AND success_day IS NOT NULL)
                                  OR (status <> 'SUCCEEDED' AND success_day IS NULL)),
    INDEX ix_ai_user_success (user_id, status, success_day),
    INDEX ix_ai_user_lease (user_id, status, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_global_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reserved_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX ix_ai_global_time (reserved_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
