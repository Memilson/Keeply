CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_snapshot_files_path_trgm
    ON snapshot_files USING GIN (lower(path) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_snapshot_files_snapshot_path_prefix
    ON snapshot_files (snapshot_id, path text_pattern_ops);
