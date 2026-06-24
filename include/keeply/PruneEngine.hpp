#pragma once

#include "keeply/Config.hpp"

#include <cstdint>
#include <string>

namespace keeply {

struct PruneResult {
    std::int64_t snapshotsDeleted{0};
    std::int64_t chunksDeleted{0};
    std::int64_t chunksKept{0};
    std::int64_t errors{0};
};

class PruneEngine {
public:
    explicit PruneEngine(Config config);
    PruneResult keepLast(int keepLast, const std::string& source = "");
private:
    Config config_;
};

}
