#pragma once

#include <cstdint>
#include <optional>
#include <sqlite3.h>
#include <string>
#include <vector>

namespace keeply {

struct SnapshotRow {
    std::string id;
    std::string createdAt;
    std::string source;
    std::string status;
    std::string manifestKey;
    std::int64_t totalFiles{0};
    std::int64_t totalBytes{0};
};

struct JobStateRow {
    std::string jobName;
    std::string lastRunAt;
    std::string lastStatus;
    std::string lastError;
    std::string lastSnapshotId;
};

struct DaemonEventRow {
    std::int64_t id{0};
    std::string createdAt;
    std::string level;
    std::string source;
    std::string message;
};

struct SnapshotFileRow {
    std::string path;
    std::int64_t size{0};
    std::int64_t mtime{0};
    std::string fileHash;
};

class LocalDb {
public:
    explicit LocalDb(const std::string& path);
    ~LocalDb();
    LocalDb(const LocalDb&) = delete;
    LocalDb& operator=(const LocalDb&) = delete;

    void migrate();
    void begin();
    void commit();
    void rollback();

    void upsertChunk(const std::string& hash, const std::string& objectKey, std::int64_t plainSize, std::int64_t storedSize);
    bool hasChunk(const std::string& hash);
    void insertSnapshot(const SnapshotRow& row);
    void updateSnapshotCompleted(const std::string& id, const std::string& manifestKey, std::int64_t files, std::int64_t bytes);
    void insertFile(const std::string& snapshotId, const std::string& path, std::int64_t size, std::int64_t mtime, const std::string& fileHash);
    void insertFileChunk(const std::string& snapshotId, const std::string& path, int chunkIndex, const std::string& chunkHash, std::int64_t offset, std::int64_t plainSize);
    std::vector<SnapshotRow> listSnapshots(const std::string& source = "");
    std::vector<SnapshotFileRow> listSnapshotFiles(const std::string& snapshotId);
    std::vector<std::string> listSnapshotChunkHashes(const std::string& snapshotId);
    std::optional<std::string> getChunkObjectKey(const std::string& hash);
    std::int64_t chunkRefCount(const std::string& hash);
    void deleteSnapshot(const std::string& id);
    void deleteChunkRecord(const std::string& hash);
    std::optional<SnapshotRow> getSnapshot(const std::string& idOrLatest);
    std::optional<JobStateRow> getJobState(const std::string& jobName);
    void updateJobState(const JobStateRow& row);
    void setDaemonState(const std::string& key, const std::string& value);
    std::optional<std::string> getDaemonState(const std::string& key);
    void appendDaemonEvent(const std::string& level, const std::string& source, const std::string& message);
    std::vector<JobStateRow> listJobStates();
    std::vector<DaemonEventRow> listDaemonEvents(int limit);

private:
    sqlite3* db_{nullptr};
    void exec(const std::string& sql);
};

}
