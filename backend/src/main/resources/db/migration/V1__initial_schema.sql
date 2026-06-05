CREATE TABLE settings (
    id BIGSERIAL PRIMARY KEY,
    task_interval_hours INTEGER NOT NULL DEFAULT 6,
    ai_api_url VARCHAR(500),
    ai_api_key VARCHAR(200),
    ai_model VARCHAR(100),
    default_group_id BIGINT,
    singleton INTEGER NOT NULL DEFAULT 1 UNIQUE,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE rss_group (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE rss_source (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(2048) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    icon_path VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_fetch_at TIMESTAMP,
    etag VARCHAR(512),
    last_modified VARCHAR(512),
    total_fetched INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE rss_source_group (
    rss_source_id BIGINT NOT NULL,
    rss_group_id BIGINT NOT NULL,
    PRIMARY KEY (rss_source_id, rss_group_id),
    CONSTRAINT fk_rsg_source FOREIGN KEY (rss_source_id) REFERENCES rss_source(id) ON DELETE CASCADE,
    CONSTRAINT fk_rsg_group FOREIGN KEY (rss_group_id) REFERENCES rss_group(id) ON DELETE CASCADE
);
CREATE INDEX idx_rsg_group_id ON rss_source_group(rss_group_id);
CREATE INDEX idx_rsg_source_id ON rss_source_group(rss_source_id);

CREATE TABLE task (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    time_range_start TIMESTAMP NOT NULL,
    time_range_end TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    source_type VARCHAR(10) NOT NULL,
    source_config TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    error_message VARCHAR(2000)
);
CREATE INDEX idx_task_status ON task(status);
CREATE INDEX idx_task_status_ended ON task(status, ended_at DESC);
CREATE INDEX idx_task_created_at ON task(created_at DESC);

CREATE TABLE report (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    time_range_start TIMESTAMP NOT NULL,
    time_range_end TIMESTAMP NOT NULL,
    news_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_report_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
);
CREATE INDEX idx_report_created_at ON report(created_at DESC);

CREATE TABLE news (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary TEXT,
    author VARCHAR(100) NOT NULL DEFAULT '未知',
    raw_content TEXT,
    structured_content TEXT,
    source_rss_id BIGINT,
    source_rss_name VARCHAR(200),
    source_url VARCHAR(2048),
    header_image_html VARCHAR(4096),
    category VARCHAR(50),
    published_at TIMESTAMP,
    sim_hash BIGINT,
    content_length INTEGER NOT NULL DEFAULT 0,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    in_material_pile BOOLEAN NOT NULL DEFAULT FALSE,
    material_pile_added_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_news_report FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE CASCADE,
    CONSTRAINT fk_news_source FOREIGN KEY (source_rss_id) REFERENCES rss_source(id) ON DELETE SET NULL
);
CREATE INDEX idx_news_report_id ON news(report_id);
CREATE INDEX idx_news_title ON news(title);
CREATE INDEX idx_news_sim_hash ON news(sim_hash);
CREATE INDEX idx_news_created_at ON news(created_at DESC);
CREATE INDEX idx_news_material_pile ON news(in_material_pile, material_pile_added_at DESC);
CREATE INDEX idx_news_is_read_created ON news(is_read, created_at DESC);

CREATE TABLE tag (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE news_tag (
    news_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (news_id, tag_id),
    CONSTRAINT fk_nt_news FOREIGN KEY (news_id) REFERENCES news(id) ON DELETE CASCADE,
    CONSTRAINT fk_nt_tag FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
);

CREATE TABLE draft (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    prompt TEXT,
    temperature DOUBLE PRECISION NOT NULL DEFAULT 0.7,
    style VARCHAR(50),
    target_platform VARCHAR(50),
    latest_version INTEGER NOT NULL DEFAULT 0,
    latest_content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_draft_updated_at ON draft(updated_at DESC);

CREATE TABLE draft_version (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    prompt TEXT,
    temperature DOUBLE PRECISION,
    style VARCHAR(50),
    target_platform VARCHAR(50),
    UNIQUE(draft_id, version),
    CONSTRAINT fk_dv_draft FOREIGN KEY (draft_id) REFERENCES draft(id) ON DELETE CASCADE
);

CREATE TABLE draft_news (
    draft_id BIGINT NOT NULL,
    news_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (draft_id, news_id),
    CONSTRAINT fk_dn_draft FOREIGN KEY (draft_id) REFERENCES draft(id) ON DELETE CASCADE,
    CONSTRAINT fk_dn_news FOREIGN KEY (news_id) REFERENCES news(id) ON DELETE CASCADE
);

-- Add foreign key for settings.default_group_id after rss_group exists
ALTER TABLE settings ADD CONSTRAINT fk_settings_group FOREIGN KEY (default_group_id) REFERENCES rss_group(id) ON DELETE SET NULL;
