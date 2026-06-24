#pragma once

#include "keeply/Config.hpp"

#include <filesystem>
#include <functional>
#include <string>

namespace keeply {

struct RestoreResult {
    std::string snapshotId;
    std::int64_t files{0};
    std::int64_t bytes{0};
};

using RestoreProgressFn = std::function<void(std::int64_t done, std::int64_t total, std::int64_t bytes, const std::string& currentFile)>;

class RestoreEngine {
public:
    explicit RestoreEngine(Config config);
    RestoreResult run(const std::string& snapshotIdOrLatest,
                      const std::filesystem::path& target,
                      RestoreProgressFn progress = nullptr);
private:
    Config config_;
};

}
