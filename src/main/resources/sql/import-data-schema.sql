CREATE TABLE IF NOT EXISTS import_jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    status VARCHAR(32) NOT NULL,
    uploaded_file_count INT NOT NULL DEFAULT 0,
    longterm_row_count INT NOT NULL DEFAULT 0,
    spot_row_count INT NOT NULL DEFAULT 0,
    failed_file_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    created_by VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    normalized_at TIMESTAMP NULL,
    confirmed_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS import_job_files (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    data_type VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    normalized_rows INT NOT NULL DEFAULT 0,
    duplicate_rows INT NOT NULL DEFAULT 0,
    new_rows INT NOT NULL DEFAULT 0,
    updated_rows INT NOT NULL DEFAULT 0,
    skipped_rows INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_job_files_job_id (job_id),
    CONSTRAINT fk_import_job_files_job FOREIGN KEY (job_id) REFERENCES import_jobs(id)
);

CREATE TABLE IF NOT EXISTS import_job_longterm_rows (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    transaction_id INT NULL,
    company_id BIGINT NOT NULL,
    place VARCHAR(100) NULL,
    transaction_date DATE NULL,
    transaction_name VARCHAR(255) NULL,
    transaction_type_id INT NULL,
    outsend_province VARCHAR(50) NULL,
    gen_type_id INT NOT NULL,
    transaction_period_id INT NULL,
    transaction_start_year INT NULL,
    transaction_end_year INT NULL,
    contract_start_date DATE NULL,
    contract_end_date DATE NULL,
    is_green BOOLEAN NOT NULL DEFAULT FALSE,
    is_cheap BOOLEAN NOT NULL DEFAULT FALSE,
    base_price DECIMAL(18,4) NULL,
    market_size DECIMAL(18,6) NULL,
    market_participation_capacity DECIMAL(18,4) NULL,
    market_avg_price DECIMAL(18,4) NULL,
    chng_participation_capacity DECIMAL(18,4) NULL,
    chng_transaction_amount DECIMAL(18,6) NULL,
    chng_avg_price DECIMAL(18,4) NULL,
    env_premium DECIMAL(18,4) NULL,
    data_source VARCHAR(255) NULL,
    note VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_import_job_longterm_rows_job_id (job_id)
);

CREATE TABLE IF NOT EXISTS import_job_spot_rows (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    company_id BIGINT NOT NULL,
    date DATE NOT NULL,
    gen_type_id INT NOT NULL,
    gen_amount DECIMAL(18,6) NULL,
    longterm_amount DECIMAL(18,6) NULL,
    longterm_price DECIMAL(18,4) NULL,
    longterm_percent DECIMAL(18,6) NULL,
    spot_price DECIMAL(18,4) NULL,
    chng_spot_price DECIMAL(18,4) NULL,
    data_source VARCHAR(255) NULL,
    note VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_import_job_spot_rows_job_id (job_id)
);

CREATE TABLE IF NOT EXISTS import_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version_code VARCHAR(64) NOT NULL UNIQUE,
    source_job_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    longterm_row_count INT NOT NULL DEFAULT 0,
    spot_row_count INT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP NULL,
    rolled_back_at TIMESTAMP NULL,
    remark VARCHAR(500) NULL,
    INDEX idx_import_versions_status (status)
);

CREATE TABLE IF NOT EXISTS import_version_longterm_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    transaction_id INT NULL,
    company_id BIGINT NOT NULL,
    place VARCHAR(100) NULL,
    transaction_date DATE NULL,
    transaction_name VARCHAR(255) NULL,
    transaction_type_id INT NULL,
    outsend_province VARCHAR(50) NULL,
    gen_type_id INT NOT NULL,
    transaction_period_id INT NULL,
    transaction_start_year INT NULL,
    transaction_end_year INT NULL,
    contract_start_date DATE NULL,
    contract_end_date DATE NULL,
    is_green BOOLEAN NOT NULL DEFAULT FALSE,
    is_cheap BOOLEAN NOT NULL DEFAULT FALSE,
    base_price DECIMAL(18,4) NULL,
    market_size DECIMAL(18,6) NULL,
    market_participation_capacity DECIMAL(18,4) NULL,
    market_avg_price DECIMAL(18,4) NULL,
    chng_participation_capacity DECIMAL(18,4) NULL,
    chng_transaction_amount DECIMAL(18,6) NULL,
    chng_avg_price DECIMAL(18,4) NULL,
    env_premium DECIMAL(18,4) NULL,
    data_source VARCHAR(255) NULL,
    note VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_import_version_longterm_snapshot_version_id (version_id)
);

CREATE TABLE IF NOT EXISTS import_version_spot_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    date DATE NOT NULL,
    gen_type_id INT NOT NULL,
    gen_amount DECIMAL(18,6) NULL,
    longterm_amount DECIMAL(18,6) NULL,
    longterm_price DECIMAL(18,4) NULL,
    longterm_percent DECIMAL(18,6) NULL,
    spot_price DECIMAL(18,4) NULL,
    chng_spot_price DECIMAL(18,4) NULL,
    data_source VARCHAR(255) NULL,
    note VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_import_version_spot_snapshot_version_id (version_id)
);

CREATE TABLE IF NOT EXISTS import_restore_points (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_type VARCHAR(32) NOT NULL,
    trigger_action VARCHAR(32) NOT NULL,
    reference_job_id BIGINT NULL,
    reference_version_id BIGINT NULL,
    from_version_id BIGINT NULL,
    to_version_id BIGINT NULL,
    binlog_file VARCHAR(255) NOT NULL,
    binlog_position BIGINT NOT NULL,
    gtid_set TEXT NULL,
    operator_name VARCHAR(64) NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_import_restore_points_created_at (created_at),
    INDEX idx_import_restore_points_action (trigger_action),
    INDEX idx_import_restore_points_to_version (to_version_id)
);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    last_login_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_users_username (username)
);

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_roles_code (code)
);

CREATE TABLE IF NOT EXISTS user_roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_roles_user_role (user_id, role_id),
    INDEX idx_user_roles_user_id (user_id),
    INDEX idx_user_roles_role_id (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_refresh_tokens_user_id (user_id),
    INDEX idx_refresh_tokens_expires_at (expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

INSERT INTO roles (code, name)
VALUES ('ADMIN', '管理员')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO roles (code, name)
VALUES ('USER', '普通用户')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO users (username, password_hash, display_name, status, created_at, updated_at)
SELECT 'admin', '$2b$12$E22fcysubh1i3.8EDCSlGeg1Q6D7S5IEOM1ZnKIUNNkJIWYRiVezy', '系统管理员', 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'admin'
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'ADMIN'
WHERE u.username = 'admin'
  AND NOT EXISTS (
    SELECT 1
    FROM user_roles ur
    WHERE ur.user_id = u.id
      AND ur.role_id = r.id
);
