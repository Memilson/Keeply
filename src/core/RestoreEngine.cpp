#include "keeply/RestoreEngine.hpp"
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

RestoreEngine::RestoreEngine(Config config) : config_(std::move(config)) {}

RestoreResult RestoreEngine::run(const std::string& snapshotIdOrLatest, const std::filesystem::path& target, RestoreProgressFn progress) {
    auto db = LocalDb(config_.dbPath);
    db.migrate();
    auto snap = db.getSnapshot(snapshotIdOrLatest);
    if (!snap) throw std::runtime_error("snapshot not found: " + snapshotIdOrLatest);
    if (snap->manifestKey.empty()) throw std::runtime_error("snapshot has no manifest: " + snap->id);

    auto store = makeObjectStore(config_);
    auto manifestCompressed = unprotectData(store->get(snap->manifestKey), config_.encryption);
    auto manifestRaw = zstdDecompress(manifestCompressed);
    auto manifest = json::parse(toString(manifestRaw));

    RestoreResult result;
    result.snapshotId = manifest.value("snapshot_id", snap->id);
    std::filesystem::create_directories(target);

    const auto& filesArray = manifest.at("files");
    const std::int64_t totalFiles = static_cast<std::int64_t>(filesArray.size());

    for (const auto& jf : filesArray) {
        const std::string rel = jf.at("path").get<std::string>();
        const auto outPath = target / std::filesystem::path(rel);
        ensureParentDir(outPath);
        std::ofstream out(outPath, std::ios::binary | std::ios::trunc);
        if (!out) throw std::runtime_error("cannot write restore file: " + outPath.string());

        Sha256State fileHash;
        for (const auto& jc : jf.at("chunks")) {
            const std::string objectKey = jc.at("object").get<std::string>();
            std::string expected = jc.at("hash").get<std::string>();
            const std::string prefix = "sha256:";
            if (expected.rfind(prefix, 0) == 0) expected = expected.substr(prefix.size());
            auto compressed = unprotectData(store->get(objectKey), config_.encryption);
            auto plain = zstdDecompress(compressed);
            const auto actual = sha256Hex(plain);
            if (actual != expected) {
                throw std::runtime_error("chunk hash mismatch for " + objectKey);
            }
            fileHash.update(plain);
            out.write(reinterpret_cast<const char*>(plain.data()), static_cast<std::streamsize>(plain.size()));
            result.bytes += static_cast<std::int64_t>(plain.size());
        }
        out.close();

        const auto actualFileHash = fileHash.finalHex();
        const auto expectedFileHash = jf.value("sha256", "");
        if (!expectedFileHash.empty() && actualFileHash != expectedFileHash) {
            throw std::runtime_error("file hash mismatch after restore: " + rel);
        }
        if (jf.contains("mtime")) {
            std::filesystem::last_write_time(outPath, unixToFileTime(jf.at("mtime").get<std::int64_t>()));
        }
        result.files++;
        if (progress) progress(result.files, totalFiles, result.bytes, rel);
        std::cout << "restored: " << rel << "\n";
    }
    return result;
}

}
