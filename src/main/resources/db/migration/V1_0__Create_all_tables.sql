CREATE TABLE sites
(
    id          SERIAL PRIMARY KEY,
    status      varchar(255) NOT NULL,
    status_time TIMESTAMP    NOT NULL,
    last_error  TEXT,
    url         VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL
);

CREATE TABLE pages
(
    id      SERIAL PRIMARY KEY,
    site_id BIGINT,
    path    VARCHAR(255) NOT NULL,
    code    INT          NOT NULL,
    content TEXT   NOT NULL,
    CONSTRAINT fk_pages_site FOREIGN KEY (site_id) REFERENCES sites (id) ON DELETE CASCADE
);

CREATE INDEX idx_path ON pages (path);

CREATE TABLE lemmas
(
    id        SERIAL PRIMARY KEY,
    site_id   BIGINT,
    lemma     VARCHAR(255) NOT NULL,
    frequency INT          NOT NULL,
    CONSTRAINT fk_lemmas_site FOREIGN KEY (site_id) REFERENCES sites (id) ON DELETE CASCADE
);

CREATE TABLE indexes
(
    id       SERIAL PRIMARY KEY,
    page_id  BIGINT,
    lemma_id BIGINT,
    rank   INT NOT NULL,

    CONSTRAINT fk_indexes_page FOREIGN KEY (page_id) REFERENCES pages (id) ON DELETE CASCADE,
    CONSTRAINT fk_indexes_lemma FOREIGN KEY (lemma_id) REFERENCES lemmas (id) ON DELETE CASCADE
);
