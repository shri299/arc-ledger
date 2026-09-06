ALTER TABLE scenes
    ADD COLUMN idempotency_key VARCHAR(128),
    ADD COLUMN request_hash VARCHAR(64);

ALTER TABLE scenes
    ADD CONSTRAINT uk_scene_story_idempotency UNIQUE (story_id, idempotency_key);
