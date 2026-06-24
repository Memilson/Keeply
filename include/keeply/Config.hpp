#pragma once

#include <cstdint>
#include <filesystem>
#include <string>
#include <vector>

namespace keeply {

struct RepositoryConfig {
    std::string type{"local"};
    std::string path{"./repo"};
    std::string endpoint;
    std::string bucket;
    std::string accessKey;
    std::string secretKey;
    std::string region{"us-east-1"};
    std::string prefix{"repos/default"};
};

struct WindowsConfig {
    bool useVss{false};
    bool useUsn{false};
};

struct EncryptionConfig {
    bool enabled{false};
    std::string keyHex;
};

struct AgentConfig {
    int pollSeconds{30};
};

struct JobConfig {
    std::string name;
    std::string source;
    bool enabled{true};
    int intervalMinutes{60};
    int retentionKeepLast{10};
    std::string lastRunAt;
};

struct Config {
    std::string dbPath{"./keeply.db"};
    std::uint64_t chunkSize{4ULL * 1024ULL * 1024ULL};
    int compressionLevel{3};
    RepositoryConfig repository;
    WindowsConfig windows;
    EncryptionConfig encryption;
    AgentConfig agent;
    std::vector<JobConfig> jobs;

    static Config load(const std::filesystem::path& path);
    void save(const std::filesystem::path& path) const;
};

}
