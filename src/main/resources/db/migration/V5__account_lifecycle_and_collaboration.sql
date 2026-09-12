ALTER TABLE app_users ADD COLUMN email_verified_at TIMESTAMPTZ;
ALTER TABLE app_users ADD COLUMN account_role VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE app_users ADD COLUMN suspended_at TIMESTAMPTZ;

UPDATE app_users SET email_verified_at = NOW();

ALTER TABLE app_users ADD CONSTRAINT ck_app_user_role
    CHECK (account_role IN ('USER', 'ADMIN'));

CREATE TABLE account_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_account_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_account_token_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
);

CREATE INDEX idx_account_token_user_purpose
    ON account_tokens(user_id, purpose, created_at DESC);
CREATE INDEX idx_account_token_expiry ON account_tokens(expires_at);

CREATE TABLE account_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_account_recovery_code_hash UNIQUE (code_hash)
);

CREATE INDEX idx_account_recovery_user ON account_recovery_codes(user_id, consumed_at);

CREATE TABLE account_session_metadata (
    session_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    device_label VARCHAR(160) NOT NULL,
    ip_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_account_session_user_seen
    ON account_session_metadata(user_id, last_seen_at DESC);

CREATE TABLE story_memberships (
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    invited_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_story_membership PRIMARY KEY (story_id, user_id),
    CONSTRAINT ck_story_membership_role CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER'))
);

INSERT INTO story_memberships(story_id, user_id, role, invited_by, created_at, updated_at)
SELECT id, owner_id, 'OWNER', owner_id, created_at, NOW()
FROM stories
WHERE owner_id IS NOT NULL;

CREATE INDEX idx_story_membership_user ON story_memberships(user_id, updated_at DESC);

CREATE TABLE story_invitations (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    invited_email VARCHAR(320) NOT NULL,
    role VARCHAR(16) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    invited_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_story_invitation_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_story_invitation_role CHECK (role IN ('EDITOR', 'VIEWER'))
);

CREATE INDEX idx_story_invitation_email ON story_invitations(invited_email, expires_at);
CREATE INDEX idx_story_invitation_expiry ON story_invitations(expires_at);

CREATE TABLE security_audit_events (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    event_type VARCHAR(64) NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    target_type VARCHAR(32),
    target_id UUID,
    request_id VARCHAR(64),
    ip_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_security_audit_actor_time ON security_audit_events(actor_id, created_at DESC);
CREATE INDEX idx_security_audit_event_time ON security_audit_events(event_type, created_at DESC);
