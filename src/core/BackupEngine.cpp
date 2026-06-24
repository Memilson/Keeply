#include "keeply/BackupEngine.hpp"
#include "keeply/Compression.hpp"
#include "keeply/Crypto.hpp"
#include "keeply/Hash.hpp"
#include "keeply/LocalDb.hpp"
#include "keeply/ObjectStoreFactory.hpp"
#include "keeply/Util.hpp"

#include <fstream>
#include <iostream>
#include <nlohmann/json.hpp>
#include <stdexcept>

namespace keeply {

using json = nlohmann::json;

BackupEngine::BackupEngine(Config config) : config_(std::move(config)) {}

static std::string chunkObjectKey(const std::string& hash) {
    return "chunks/" + hash.substr(0, 2) + "/" + hash.substr(2, 2) + "/" + hash + ".zst";
}

BackupResult BackupEngine::run(const std::filesystem::path& source, BackupProgressFn progress) {
    if (!std::filesystem::exists(source)) throw std::runtime_error("source does not exist: " + source.string());
    if (!std::filesystem::is_directory(source)) throw std::runtime_error("source must be a directory: " + source.string());

    // First pass: collect accessible regular files so we know the total for progress %.
    std::vector<std::filesystem::path> allFiles;
    for (auto const& entry : std::filesystem::recursive_directory_iterator(
             source, std::filesystem::directory_options::skip_permission_denied)) {
        try {
            if (!entry.is_regular_file()) continue;
        } catch (const std::filesystem::filesystem_error&) { continue; }
        try {
            (void)entry.file_size();
            (void)entry.last_write_time();
        } catch (const std::filesystem::filesystem_error&) { continue; }
        allFiles.push_back(entry.path());
    }
    const std::int64_t totalFiles = static_cast<std::int64_t>(allFiles.size());

    auto db = LocalDb(config_.dbPath);
    db.migrate();
    auto store = makeObjectStore(config_);

    BackupResult result;
    result.snapshotId = makeSnapshotId();
    result.manifestKey = "manifests/" + result.snapshotId + ".json.zst";

    SnapshotRow row;
    row.id = result.snapshotId;
    row.createdAt = nowUtcIso();
    row.source = std::filesystem::absolute(source).string();
    row.status = "running";
    row.manifestKey = result.manifestKey;
    db.insertSnapshot(row);

    json manifest;
    manifest["version"] = 1;
    manifest["type"] = "file";
    manifest["snapshot_id"] = result.snapshotId;
    manifest["created_at"] = row.createdAt;
    manifest["source"] = row.source;
    manifest["chunking"] = {
        {"algorithm", "fixed"},
        {"chunk_size", config_.chunkSize}
    };
    manifest["compression"] = {
        {"algorithm", "zstd"},
        {"level", config_.compressionLevel}
    };
    manifest["encryption"] = {
        {"enabled", config_.encryption.enabled},
        {"algorithm", config_.encryption.enabled ? "aes-256-gcm" : "none"}
    };
    manifest["hash"] = {
        {"algorithm", "sha256"}
    };
    manifest["files"] = json::array();

    std::vector<unsigned char> buffer(static_cast<std::size_t>(config_.chunkSize));

    for (const auto& absPath : allFiles) {
        const auto rel = genericRelativePath(absPath, source);

        std::int64_t entrySize = 0;
        std::int64_t entryMtime = 0;
        try {
            entrySize = static_cast<std::int64_t>(std::filesystem::file_size(absPath));
            entryMtime = fileTimeToUnix(std::filesystem::last_write_time(absPath));
        } catch (const std::filesystem::filesystem_error& fe) {
            std::cerr << "skip unreadable attributes: " << absPath << " (" << fe.what() << ")\n";
            continue;
        }

        std::ifstream in(absPath, std::ios::binary);
        if (!in) {
            std::cerr << "skip unreadable file: " << absPath << "\n";
            continue;
        }

        json jf;
        jf["path"] = rel;
        jf["size"] = entrySize;
        jf["mtime"] = entryMtime;
        jf["chunks"] = json::array();

        Sha256State fileHash;
        std::uint64_t offset = 0;
        int index = 0;
        while (in) {
            in.read(reinterpret_cast<char*>(buffer.data()), static_cast<std::streamsize>(buffer.size()));
            const auto got = static_cast<std::size_t>(in.gcount());
            if (got == 0) break;

            std::vector<unsigned char> plain(buffer.begin(), buffer.begin() + static_cast<std::ptrdiff_t>(got));
            fileHash.update(plain);
            const auto chunkHash = sha256Hex(plain);
            const auto objectKey = chunkObjectKey(chunkHash);

            bool already = db.hasChunk(chunkHash);
            if (!already) {
                try { already = store->exists(objectKey); } catch (...) { already = false; }
            }
            std::size_t storedSize = 0;
            if (!already) {
                auto compressed = zstdCompress(plain, config_.compressionLevel);
                auto stored = protectData(compressed, config_.encryption);
                storedSize = stored.size();
                store->put(objectKey, stored);
                db.upsertChunk(chunkHash, objectKey, static_cast<std::int64_t>(got), static_cast<std::int64_t>(storedSize));
                result.uploadedChunks++;
            } else {
                result.reusedChunks++;
            }

            jf["chunks"].push_back({
                {"index", index},
                {"offset", offset},
                {"size", got},
                {"hash", "sha256:" + chunkHash},
                {"object", objectKey}
            });
            db.insertFileChunk(result.snapshotId, rel, index, chunkHash, static_cast<std::int64_t>(offset), static_cast<std::int64_t>(got));
            offset += got;
            index++;
        }

        const auto fh = fileHash.finalHex();
        jf["sha256"] = fh;
        manifest["files"].push_back(jf);
        db.insertFile(result.snapshotId, rel, entrySize, entryMtime, fh);
        result.files++;
        result.bytes += entrySize;
        if (progress) progress(result.files, totalFiles, result.bytes, rel);
        std::cout << "backed up: " << rel << "\n";
    }

    const auto manifestRaw = fromString(manifest.dump(2));
    const auto manifestCompressed = zstdCompress(manifestRaw, config_.compressionLevel);
    store->put(result.manifestKey, protectData(manifestCompressed, config_.encryption));
    db.updateSnapshotCompleted(result.snapshotId, result.manifestKey, result.files, result.bytes);
    return result;
}

}
