CREATE TABLE file_object (
    file_object_id BIGINT NOT NULL AUTO_INCREMENT,
    object_key VARCHAR(500) NOT NULL,
    temporary_object_key VARCHAR(500) NOT NULL,
    account_id BIGINT NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    status ENUM('ATTACHED','CLEANUP_PENDING','CONFIRMED') NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (file_object_id),
    CONSTRAINT uk_file_object_key UNIQUE (object_key),
    CONSTRAINT uk_file_object_temporary_key UNIQUE (temporary_object_key),
    CONSTRAINT fk_file_object_account
        FOREIGN KEY (account_id) REFERENCES account (account_id),
    INDEX idx_file_object_cleanup (status, created_at)
);
