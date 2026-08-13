CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE stories (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE chapters (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL REFERENCES stories(id),
    chapter_number INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_chapter_story_number UNIQUE (story_id, chapter_number)
);

CREATE TABLE scenes (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL REFERENCES stories(id),
    chapter_id UUID NOT NULL REFERENCES chapters(id),
    sequence_number INTEGER NOT NULL,
    raw_text TEXT NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_scene_chapter_sequence UNIQUE (chapter_id, sequence_number)
);

CREATE TABLE narrative_entities (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL REFERENCES stories(id),
    name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    latest_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_entity_story_name UNIQUE (story_id, normalized_name)
);

CREATE TABLE entity_state_versions (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL REFERENCES narrative_entities(id),
    story_id UUID NOT NULL REFERENCES stories(id),
    originating_scene_id UUID NOT NULL REFERENCES scenes(id),
    version_number INTEGER NOT NULL,
    changed_facts_json TEXT NOT NULL,
    resulting_state_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_entity_version UNIQUE (entity_id, version_number)
);

CREATE TABLE entity_facts (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL REFERENCES narrative_entities(id),
    state_version_id UUID NOT NULL REFERENCES entity_state_versions(id),
    source_scene_id UUID NOT NULL REFERENCES scenes(id),
    fact_key VARCHAR(255) NOT NULL,
    fact_value VARCHAR(2000) NOT NULL,
    knowledge_kind VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL,
    superseded_by_fact_id UUID,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_fact_current ON entity_facts(entity_id, active);

CREATE TABLE synthetic_questions (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL REFERENCES stories(id),
    entity_id UUID NOT NULL REFERENCES narrative_entities(id),
    state_version_id UUID NOT NULL REFERENCES entity_state_versions(id),
    scene_id UUID NOT NULL REFERENCES scenes(id),
    question VARCHAR(1000) NOT NULL,
    answer VARCHAR(2000) NOT NULL,
    embedding vector(768),
    current_state BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_question_story_current ON synthetic_questions(story_id, current_state);
CREATE INDEX idx_question_entity_current ON synthetic_questions(entity_id, current_state);
CREATE INDEX idx_question_embedding_hnsw ON synthetic_questions
    USING hnsw (embedding vector_cosine_ops)
    WHERE current_state = TRUE;

CREATE TABLE consistency_results (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL REFERENCES stories(id),
    scene_id UUID NOT NULL REFERENCES scenes(id),
    entity_id UUID REFERENCES narrative_entities(id),
    status VARCHAR(32) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    supporting_evidence VARCHAR(4000),
    source_scene_ids VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_consistency_scene ON consistency_results(scene_id, created_at);
