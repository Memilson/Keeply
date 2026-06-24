#pragma once

#include "keeply/Config.hpp"

#include <cstdint>
#include <string>
#include <vector>

namespace keeply {

struct VerifyResult {
    std::string snapshotId;
    std::int64_t files{0};
    std::int64_t chunks{0};
    std::int64_t bytes{0};
    std::int64_t errors{0};
};

class VerifyEngine {
public:
    explicit VerifyEngine(Config config);
    VerifyResult run(const std::string& snapshotIdOrLatest);
    std::vector<VerifyResult> runAll();
private:
    Config config_;
};

}
