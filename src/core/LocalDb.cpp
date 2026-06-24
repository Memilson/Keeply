#include "keeply/LocalDb.hpp"

#include <stdexcept>

namespace keeply {

LocalDb::LocalDb(const std::string& path) {
    if (sqlite3_open(path.c_str(), &db_) != SQLITE_OK) {
        throw std::runtime_error("sqlite open failed: " + std::string(sqlite3_errmsg(db_)));
    }
}

LocalDb::~LocalDb() {
    if (db_) sqlite3_close(db_);
}

void LocalDb::exec(const std::string& sql) {
    char* err = nullptr;
    if (sqlite3_exec(db_, sql.c_str(), nullptr, nullptr, &err) != SQLITE_OK) {
        std::string msg = err ? err : "sqlite error";
        sqlite3_free(err);
        throw std::runtime_error(msg);
    }
}

void LocalDb::migrate() {
    exec("PRAGMA journal_mode=WAL;");
    exec("PRAGMA foreign_keys=ON;");
    exec(R"SQL(
CREATE TABLE IF NOT EXISTS snapshots (
  id TEXT PRIMARY KEY,
  created_at TEXT NOT NULL,
  source TEXT NOT NULL,
  status TEXT NOT NULL,
  manifest_key TEXT,
  total_files INTEGER DEFAULT 0,
  total_bytes INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS files (
  snapshot_id TEXT NOT NULL,
  path TEXT NOT NULL,
  size INTEGER NOT NULL,
  mtime INTEGER NOT NULL,
  file_hash TEXT NOT NULL,
  PRIMARY KEY(snapshot_id, path)
);
CREATE TABLE IF NOT EXISTS chunks (
  hash TEXT PRIMARY KEY,
  object_key TEXT NOT NULL,
  plain_size INTEGER NOT NULL,
  stored_size INTEGER NOT NULL,
  uploaded_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS file_chunks (
  snapshot_id TEXT NOT NULL,
  path TEXT NOT NULL,
  chunk_index INTEGER NOT NULL,
  chunk_hash TEXT NOT NULL,
  offset INTEGER NOT NULL,
  plain_size INTEGER NOT NULL,
  PRIMARY KEY(snapshot_id, path, chunk_index)
);
CREATE TABLE IF NOT EXISTS usn_state (
  volume_guid TEXT PRIMARY KEY,
  journal_id TEXT,
  last_usn INTEGER
);
CREATE TABLE IF NOT EXISTS job_state (
  job_name TEXT PRIMARY KEY,
  last_run_at TEXT,
  last_status TEXT,
  last_error TEXT,
  last_snapshot_id TEXT
);
CREATE TABLE IF NOT EXISTS daemon_state (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS daemon_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at TEXT DEFAULT CURRENT_TIMESTAMP,
  level TEXT NOT NULL,
  source TEXT NOT NULL,
  message TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_snapshots_created_at ON snapshots(created_at);
CREATE INDEX IF NOT EXISTS idx_file_chunks_hash ON file_chunks(chunk_hash);
CREATE INDEX IF NOT EXISTS idx_daemon_events_created_at ON daemon_events(created_at);
)SQL");
}

void LocalDb::begin() { exec("BEGIN IMMEDIATE;"); }
void LocalDb::commit() { exec("COMMIT;"); }
void LocalDb::rollback() { exec("ROLLBACK;"); }

bool LocalDb::hasChunk(const std::string& hash) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "SELECT 1 FROM chunks WHERE hash=? LIMIT 1", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, hash.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(st);
    sqlite3_finalize(st);
    return rc == SQLITE_ROW;
}

void LocalDb::upsertChunk(const std::string& hash, const std::string& objectKey, std::int64_t plainSize, std::int64_t storedSize) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "INSERT OR REPLACE INTO chunks(hash, object_key, plain_size, stored_size) VALUES(?,?,?,?)", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, hash.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 2, objectKey.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int64(st, 3, plainSize);
    sqlite3_bind_int64(st, 4, storedSize);
    if (sqlite3_step(st) != SQLITE_DONE) {
        std::string msg = sqlite3_errmsg(db_);
        sqlite3_finalize(st);
        throw std::runtime_error(msg);
    }
    sqlite3_finalize(st);
}

void LocalDb::insertSnapshot(const SnapshotRow& row) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "INSERT INTO snapshots(id, created_at, source, status, manifest_key, total_files, total_bytes) VALUES(?,?,?,?,?,?,?)", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, row.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 2, row.createdAt.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 3, row.source.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 4, row.status.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 5, row.manifestKey.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int64(st, 6, row.totalFiles);
    sqlite3_bind_int64(st, 7, row.totalBytes);
    if (sqlite3_step(st) != SQLITE_DONE) {
        std::string msg = sqlite3_errmsg(db_);
        sqlite3_finalize(st);
        throw std::runtime_error(msg);
    }
    sqlite3_finalize(st);
}

void LocalDb::updateSnapshotCompleted(const std::string& id, const std::string& manifestKey, std::int64_t files, std::int64_t bytes) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "UPDATE snapshots SET status='completed', manifest_key=?, total_files=?, total_bytes=? WHERE id=?", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, manifestKey.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int64(st, 2, files);
    sqlite3_bind_int64(st, 3, bytes);
    sqlite3_bind_text(st, 4, id.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(st) != SQLITE_DONE) {
        std::string msg = sqlite3_errmsg(db_);
        sqlite3_finalize(st);
        throw std::runtime_error(msg);
    }
    sqlite3_finalize(st);
}

void LocalDb::insertFile(const std::string& snapshotId, const std::string& path, std::int64_t size, std::int64_t mtime, const std::string& fileHash) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "INSERT OR REPLACE INTO files(snapshot_id, path, size, mtime, file_hash) VALUES(?,?,?,?,?)", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, snapshotId.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 2, path.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int64(st, 3, size);
    sqlite3_bind_int64(st, 4, mtime);
    sqlite3_bind_text(st, 5, fileHash.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(st) != SQLITE_DONE) {
        std::string msg = sqlite3_errmsg(db_);
        sqlite3_finalize(st);
        throw std::runtime_error(msg);
    }
    sqlite3_finalize(st);
}

void LocalDb::insertFileChunk(const std::string& snapshotId, const std::string& path, int chunkIndex, const std::string& chunkHash, std::int64_t offset, std::int64_t plainSize) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "INSERT OR REPLACE INTO file_chunks(snapshot_id, path, chunk_index, chunk_hash, offset, plain_size) VALUES(?,?,?,?,?,?)", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, snapshotId.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 2, path.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(st, 3, chunkIndex);
    sqlite3_bind_text(st, 4, chunkHash.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int64(st, 5, offset);
    sqlite3_bind_int64(st, 6, plainSize);
    if (sqlite3_step(st) != SQLITE_DONE) {
        std::string msg = sqlite3_errmsg(db_);
        sqlite3_finalize(st);
        throw std::runtime_error(msg);
    }
    sqlite3_finalize(st);
}

std::vector<SnapshotRow> LocalDb::listSnapshots(const std::string& source) {
    sqlite3_stmt* st = nullptr;
    if (source.empty()) {
        sqlite3_prepare_v2(db_, "SELECT id, created_at, source, status, COALESCE(manifest_key,''), total_files, total_bytes FROM snapshots ORDER BY created_at DESC", -1, &st, nullptr);
    } else {
        sqlite3_prepare_v2(db_, "SELECT id, created_at, source, status, COALESCE(manifest_key,''), total_files, total_bytes FROM snapshots WHERE source=? ORDER BY created_at DESC", -1, &st, nullptr);
        sqlite3_bind_text(st, 1, source.c_str(), -1, SQLITE_TRANSIENT);
    }
    std::vector<SnapshotRow> rows;
    while (sqlite3_step(st) == SQLITE_ROW) {
        SnapshotRow r;
        r.id = reinterpret_cast<const char*>(sqlite3_column_text(st, 0));
        r.createdAt = reinterpret_cast<const char*>(sqlite3_column_text(st, 1));
        r.source = reinterpret_cast<const char*>(sqlite3_column_text(st, 2));
        r.status = reinterpret_cast<const char*>(sqlite3_column_text(st, 3));
        r.manifestKey = reinterpret_cast<const char*>(sqlite3_column_text(st, 4));
        r.totalFiles = sqlite3_column_int64(st, 5);
        r.totalBytes = sqlite3_column_int64(st, 6);
        rows.push_back(std::move(r));
    }
    sqlite3_finalize(st);
    return rows;
}

std::vector<SnapshotFileRow> LocalDb::listSnapshotFiles(const std::string& snapshotId) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "SELECT path, size, mtime, file_hash FROM files WHERE snapshot_id=? ORDER BY path", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, snapshotId.c_str(), -1, SQLITE_TRANSIENT);
    std::vector<SnapshotFileRow> rows;
    while (sqlite3_step(st) == SQLITE_ROW) {
        SnapshotFileRow row;
        row.path = reinterpret_cast<const char*>(sqlite3_column_text(st, 0));
        row.size = sqlite3_column_int64(st, 1);
        row.mtime = sqlite3_column_int64(st, 2);
        row.fileHash = reinterpret_cast<const char*>(sqlite3_column_text(st, 3));
        rows.push_back(std::move(row));
    }
    sqlite3_finalize(st);
    return rows;
}

std::vector<std::string> LocalDb::listSnapshotChunkHashes(const std::string& snapshotId) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "SELECT DISTINCT chunk_hash FROM file_chunks WHERE snapshot_id=?", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, snapshotId.c_str(), -1, SQLITE_TRANSIENT);
    std::vector<std::string> hashes;
    while (sqlite3_step(st) == SQLITE_ROW) hashes.emplace_back(reinterpret_cast<const char*>(sqlite3_column_text(st, 0)));
    sqlite3_finalize(st);
    return hashes;
}

std::optional<std::string> LocalDb::getChunkObjectKey(const std::string& hash) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "SELECT object_key FROM chunks WHERE hash=? LIMIT 1", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, hash.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(st) != SQLITE_ROW) {
        sqlite3_finalize(st);
        return std::nullopt;
    }
    std::string key = reinterpret_cast<const char*>(sqlite3_column_text(st, 0));
    sqlite3_finalize(st);
    return key;
}

std::int64_t LocalDb::chunkRefCount(const std::string& hash) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "SELECT COUNT(*) FROM file_chunks WHERE chunk_hash=?", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, hash.c_str(), -1, SQLITE_TRANSIENT);
    std::int64_t count = 0;
    if (sqlite3_step(st) == SQLITE_ROW) count = sqlite3_column_int64(st, 0);
    sqlite3_finalize(st);
    return count;
}

void LocalDb::deleteSnapshot(const std::string& id) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "DELETE FROM file_chunks WHERE snapshot_id=?", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, id.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(st) != SQLITE_DONE) {
        std::string msg = sqlite3_errmsg(db_);
        sqlite3_finalize(st);
        throw std::runtime_error(msg);
    }
    sqlite3_finalize(st);
    sqlite3_prepare_v2(db_, "DELETE FROM files WHERE snapshot_id=?", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, id.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(st) != SQLITE_DONE) {
        std::string msg = sqlite3_errmsg(db_);
        sqlite3_finalize(st);
        throw std::runtime_error(msg);
    }
    sqlite3_finalize(st);
    sqlite3_prepare_v2(db_, "DELETE FROM snapshots WHERE id=?", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, id.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(st) != SQLITE_DONE) {
        std::string msg = sqlite3_errmsg(db_);
        sqlite3_finalize(st);
        throw std::runtime_error(msg);
    }
    sqlite3_finalize(st);
}

void LocalDb::deleteChunkRecord(const std::string& hash) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "DELETE FROM chunks WHERE hash=?", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, hash.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(st) != SQLITE_DONE) {
        std::string msg = sqlite3_errmsg(db_);
        sqlite3_finalize(st);
        throw std::runtime_error(msg);
    }
    sqlite3_finalize(st);
}

std::optional<SnapshotRow> LocalDb::getSnapshot(const std::string& idOrLatest) {
    sqlite3_stmt* st = nullptr;
    if (idOrLatest == "latest") {
        sqlite3_prepare_v2(db_, "SELECT id, created_at, source, status, COALESCE(manifest_key,''), total_files, total_bytes FROM snapshots WHERE status='completed' ORDER BY created_at DESC LIMIT 1", -1, &st, nullptr);
    } else {
        sqlite3_prepare_v2(db_, "SELECT id, created_at, source, status, COALESCE(manifest_key,''), total_files, total_bytes FROM snapshots WHERE id=? LIMIT 1", -1, &st, nullptr);
        sqlite3_bind_text(st, 1, idOrLatest.c_str(), -1, SQLITE_TRANSIENT);
    }
    if (sqlite3_step(st) != SQLITE_ROW) {
        sqlite3_finalize(st);
        return std::nullopt;
    }
    SnapshotRow r;
    r.id = reinterpret_cast<const char*>(sqlite3_column_text(st, 0));
    r.createdAt = reinterpret_cast<const char*>(sqlite3_column_text(st, 1));
    r.source = reinterpret_cast<const char*>(sqlite3_column_text(st, 2));
    r.status = reinterpret_cast<const char*>(sqlite3_column_text(st, 3));
    r.manifestKey = reinterpret_cast<const char*>(sqlite3_column_text(st, 4));
    r.totalFiles = sqlite3_column_int64(st, 5);
    r.totalBytes = sqlite3_column_int64(st, 6);
    sqlite3_finalize(st);
    return r;
}

std::optional<JobStateRow> LocalDb::getJobState(const std::string& jobName) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "SELECT job_name, COALESCE(last_run_at,''), COALESCE(last_status,''), COALESCE(last_error,''), COALESCE(last_snapshot_id,'') FROM job_state WHERE job_name=? LIMIT 1", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, jobName.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(st) != SQLITE_ROW) {
        sqlite3_finalize(st);
        return std::nullopt;
    }
    JobStateRow row;
    row.jobName = reinterpret_cast<const char*>(sqlite3_column_text(st, 0));
    row.lastRunAt = reinterpret_cast<const char*>(sqlite3_column_text(st, 1));
    row.lastStatus = reinterpret_cast<const char*>(sqlite3_column_text(st, 2));
    row.lastError = reinterpret_cast<const char*>(sqlite3_column_text(st, 3));
    row.lastSnapshotId = reinterpret_cast<const char*>(sqlite3_column_text(st, 4));
    sqlite3_finalize(st);
    return row;
}

void LocalDb::updateJobState(const JobStateRow& row) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "INSERT INTO job_state(job_name, last_run_at, last_status, last_error, last_snapshot_id) VALUES(?,?,?,?,?) ON CONFLICT(job_name) DO UPDATE SET last_run_at=excluded.last_run_at, last_status=excluded.last_status, last_error=excluded.last_error, last_snapshot_id=excluded.last_snapshot_id", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, row.jobName.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 2, row.lastRunAt.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 3, row.lastStatus.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 4, row.lastError.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 5, row.lastSnapshotId.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(st) != SQLITE_DONE) {
        std::string msg = sqlite3_errmsg(db_);
        sqlite3_finalize(st);
        throw std::runtime_error(msg);
    }
    sqlite3_finalize(st);
}

void LocalDb::setDaemonState(const std::string& key, const std::string& value) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "INSERT INTO daemon_state(key, value, updated_at) VALUES(?,?,CURRENT_TIMESTAMP) ON CONFLICT(key) DO UPDATE SET value=excluded.value, updated_at=CURRENT_TIMESTAMP", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, key.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 2, value.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(st) != SQLITE_DONE) {
        std::string msg = sqlite3_errmsg(db_);
        sqlite3_finalize(st);
        throw std::runtime_error(msg);
    }
    sqlite3_finalize(st);
}

std::optional<std::string> LocalDb::getDaemonState(const std::string& key) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "SELECT value FROM daemon_state WHERE key=? LIMIT 1", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, key.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(st) != SQLITE_ROW) {
        sqlite3_finalize(st);
        return std::nullopt;
    }
    std::string value = reinterpret_cast<const char*>(sqlite3_column_text(st, 0));
    sqlite3_finalize(st);
    return value;
}

void LocalDb::appendDaemonEvent(const std::string& level, const std::string& source, const std::string& message) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "INSERT INTO daemon_events(level, source, message) VALUES(?,?,?)", -1, &st, nullptr);
    sqlite3_bind_text(st, 1, level.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 2, source.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 3, message.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(st) != SQLITE_DONE) {
        std::string msg = sqlite3_errmsg(db_);
        sqlite3_finalize(st);
        throw std::runtime_error(msg);
    }
    sqlite3_finalize(st);
}

std::vector<JobStateRow> LocalDb::listJobStates() {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "SELECT job_name, COALESCE(last_run_at,''), COALESCE(last_status,''), COALESCE(last_error,''), COALESCE(last_snapshot_id,'') FROM job_state ORDER BY job_name", -1, &st, nullptr);
    std::vector<JobStateRow> rows;
    while (sqlite3_step(st) == SQLITE_ROW) {
        JobStateRow row;
        row.jobName = reinterpret_cast<const char*>(sqlite3_column_text(st, 0));
        row.lastRunAt = reinterpret_cast<const char*>(sqlite3_column_text(st, 1));
        row.lastStatus = reinterpret_cast<const char*>(sqlite3_column_text(st, 2));
        row.lastError = reinterpret_cast<const char*>(sqlite3_column_text(st, 3));
        row.lastSnapshotId = reinterpret_cast<const char*>(sqlite3_column_text(st, 4));
        rows.push_back(std::move(row));
    }
    sqlite3_finalize(st);
    return rows;
}

std::vector<DaemonEventRow> LocalDb::listDaemonEvents(int limit) {
    sqlite3_stmt* st = nullptr;
    sqlite3_prepare_v2(db_, "SELECT id, created_at, level, source, message FROM daemon_events ORDER BY id DESC LIMIT ?", -1, &st, nullptr);
    sqlite3_bind_int(st, 1, limit <= 0 ? 50 : limit);
    std::vector<DaemonEventRow> rows;
    while (sqlite3_step(st) == SQLITE_ROW) {
        DaemonEventRow row;
        row.id = sqlite3_column_int64(st, 0);
        row.createdAt = reinterpret_cast<const char*>(sqlite3_column_text(st, 1));
        row.level = reinterpret_cast<const char*>(sqlite3_column_text(st, 2));
        row.source = reinterpret_cast<const char*>(sqlite3_column_text(st, 3));
        row.message = reinterpret_cast<const char*>(sqlite3_column_text(st, 4));
        rows.push_back(std::move(row));
    }
    sqlite3_finalize(st);
    return rows;
}

}
