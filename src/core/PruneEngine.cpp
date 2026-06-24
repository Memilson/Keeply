#include "keeply/PruneEngine.hpp"
#include "keeply/LocalDb.hpp"
#include "keeply/ObjectStoreFactory.hpp"

#include <iostream>
#include <stdexcept>
#include <unordered_set>
#include <utility>
#include <vector>

namespace keeply {

PruneEngine::PruneEngine(Config config) : config_(std::move(config)) {}

PruneResult PruneEngine::keepLast(int keepLast, const std::string& source) {
    if (keepLast < 0) throw std::runtime_error("keep-last must be zero or greater");
    LocalDb db(config_.dbPath);
    db.migrate();
    auto store = makeObjectStore(config_);
    auto all = db.listSnapshots(source);
    std::vector<SnapshotRow> snapshots;
    for (const auto& snap : all) {
        if (snap.status == "completed") snapshots.push_back(snap);
    }
    PruneResult result;
    if (static_cast<int>(snapshots.size()) <= keepLast) return result;
    for (std::size_t i = static_cast<std::size_t>(keepLast); i < snapshots.size(); ++i) {
        const auto& snap = snapshots[i];
        auto hashes = db.listSnapshotChunkHashes(snap.id);
        std::unordered_set<std::string> unique(hashes.begin(), hashes.end());
        try {
            if (!snap.manifestKey.empty()) store->remove(snap.manifestKey);
        } catch (const std::exception& e) {
            result.errors++;
            std::cerr << "prune manifest error: " << e.what() << "\n";
        }
        db.deleteSnapshot(snap.id);
        result.snapshotsDeleted++;
        for (const auto& hash : unique) {
            if (db.chunkRefCount(hash) > 0) {
                result.chunksKept++;
                continue;
            }
            auto objectKey = db.getChunkObjectKey(hash);
            if (!objectKey) continue;
            try {
                store->remove(*objectKey);
                db.deleteChunkRecord(hash);
                result.chunksDeleted++;
            } catch (const std::exception& e) {
                result.errors++;
                std::cerr << "prune chunk error: " << e.what() << "\n";
            }
        }
    }
    return result;
}

}
