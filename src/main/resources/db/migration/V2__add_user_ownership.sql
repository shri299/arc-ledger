CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

ALTER TABLE stories
    ADD COLUMN owner_id UUID REFERENCES app_users(id);

CREATE INDEX idx_story_owner_updated ON stories(owner_id, updated_at DESC);
