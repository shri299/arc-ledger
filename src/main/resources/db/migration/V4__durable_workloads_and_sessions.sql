UPDATE scenes SET processing_status = 'QUEUED' WHERE processing_status = 'PENDING';

CREATE TABLE scene_processing_jobs (
    id UUID PRIMARY KEY,
    scene_id UUID NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(120),
    last_error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_scene_processing_job_scene UNIQUE (scene_id),
    CONSTRAINT ck_scene_processing_job_attempts CHECK (attempts >= 0 AND max_attempts > 0),
    CONSTRAINT ck_scene_processing_job_status CHECK (
        status IN ('QUEUED', 'PROCESSING', 'RETRYING', 'COMPLETED', 'DEAD_LETTER')
    )
);

CREATE INDEX idx_scene_processing_job_claim
    ON scene_processing_jobs(status, available_at, created_at);
CREATE INDEX idx_scene_processing_job_lease
    ON scene_processing_jobs(status, locked_at);

INSERT INTO scene_processing_jobs(
    id, scene_id, status, attempts, max_attempts, available_at, created_at, updated_at
)
SELECT gen_random_uuid(), id, 'QUEUED', 0, 3, NOW(), NOW(), NOW()
FROM scenes
WHERE processing_status = 'QUEUED';

CREATE TABLE rate_limit_windows (
    rate_key VARCHAR(160) NOT NULL,
    window_start BIGINT NOT NULL,
    hit_count INTEGER NOT NULL,
    expires_at BIGINT NOT NULL,
    CONSTRAINT pk_rate_limit_windows PRIMARY KEY (rate_key, window_start),
    CONSTRAINT ck_rate_limit_hit_count CHECK (hit_count > 0)
);

CREATE INDEX idx_rate_limit_expiry ON rate_limit_windows(expires_at);

CREATE TABLE spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INTEGER NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(320),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session(session_id);
CREATE INDEX spring_session_ix2 ON spring_session(expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session(principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session(primary_id) ON DELETE CASCADE
);
