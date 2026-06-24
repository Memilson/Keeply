#include "keeply/VerifyEngine.hpp"
#include "keeply/Compression.hpp"
#include "keeply/Crypto.hpp"
#include "keeply/Hash.hpp"
#include "keeply/LocalDb.hpp"
#include "keeply/ObjectStoreFactory.hpp"
#include "keeply/Util.hpp"

#include <iostream>
#include <nlohmann/json.hpp>
#include <stdexcept>
#include <utility>

namespace keeply {

using json = nlohmann::json;

VerifyEngine::VerifyEngine(Config config) : config_(std::move(config)) {}

VerifyResult VerifyEngine::run(const std::string& snapshotIdOrLatest) {
    LocalDb db(config_.dbPath);
    db.migrate();
    auto snap = db.getSnapshot(snapshotIdOrLatest);
    if (!snap) throw std::runtime_error("snapshot not found: " + snapshotIdOrLatest);
    auto store = makeObjectStore(config_);
    VerifyResult result;
    result.snapshotId = snap->id;
    try {
        auto manifestCompressed = unprotectData(store->get(snap->manifestKey), config_.encryption);
        auto manifestRaw = zstdDecompress(manifestCompressed);
        auto manifest = json::parse(toString(manifestRaw));
        for (const auto& jf : manifest.at("files")) {
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
                    result.errors++;
                    std::cerr << "verify mismatch: " << objectKey << "\n";
                    continue;
                }
                fileHash.update(plain);
                result.chunks++;
                result.bytes += static_cast<std::int64_t>(plain.size());
            }
            const auto actualFileHash = fileHash.finalHex();
            const auto expectedFileHash = jf.value("sha256", "");
            if (!expectedFileHash.empty() && actualFileHash != expectedFileHash) {
                result.errors++;
                std::cerr << "verify file mismatch: " << jf.at("path").get<std::string>() << "\n";
            }
            result.files++;
        }
    } catch (const std::exception& e) {
        result.errors++;
        std::cerr << "verify error: " << e.what() << "\n";
    }
    return result;
}

std::vector<VerifyResult> VerifyEngine::runAll() {
    LocalDb db(config_.dbPath);
    db.migrate();
    std::vector<VerifyResult> results;
    for (const auto& snap : db.listSnapshots()) {
        if (snap.status == "completed") results.push_back(run(snap.id));
    }
    return results;
}

}
