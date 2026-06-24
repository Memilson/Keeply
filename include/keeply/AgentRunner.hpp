#pragma once

#include "keeply/Config.hpp"
#include "keeply/LocalDb.hpp"

#include <filesystem>
#include <string>

namespace keeply {

class AgentRunner {
public:
    explicit AgentRunner(std::filesystem::path configPath);
    int runOnce(const std::string& jobName = "");
    int runLoop(const std::string& jobName = "");
private:
    std::filesystem::path configPath_;
    bool due(const JobConfig& job, const std::optional<JobStateRow>& state) const;
    void runJob(Config& config, JobConfig& job);
};

}
