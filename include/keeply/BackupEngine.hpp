#pragma once

#include "keeply/Config.hpp"

#include <filesystem>
#include <functional>
#include <string>

namespace keeply {

struct BackupResult {
    std::string snapshotId;
    std::string manifestKey;
    std::int64_t files{0};
    std::int64_t bytes{0};
    std::int64_t uploadedChunks{0};
    std::int64_t reusedChunks{0};
};

using BackupProgressFn = std::function<void(std::int64_t done, std::int64_t total, std::int64_t bytes, const std::string& currentFile)>;

class BackupEngine {
public:
    explicit BackupEngine(Config config);
    BackupResult run(const std::filesystem::path& source,
                     BackupProgressFn progress = nullptr);
private:
    Config config_;
};

}
