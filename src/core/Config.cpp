#include "keeply/Config.hpp"

#include <fstream>
#include <nlohmann/json.hpp>
#include <stdexcept>
#include <utility>

namespace keeply {

using json = nlohmann::json;

Config Config::load(const std::filesystem::path& path) {
    std::ifstream in(path);
    if (!in) throw std::runtime_error("cannot open config: " + path.string());
    json j;
    in >> j;

    Config c;
    c.dbPath = j.value("db_path", c.dbPath);
    c.chunkSize = j.value("chunk_size", c.chunkSize);
    c.compressionLevel = j.value("compression_level", c.compressionLevel);

    auto r = j.value("repository", json::object());
    c.repository.type = r.value("type", c.repository.type);
    c.repository.path = r.value("path", c.repository.path);
    c.repository.endpoint = r.value("endpoint", c.repository.endpoint);
    c.repository.bucket = r.value("bucket", c.repository.bucket);
    c.repository.accessKey = r.value("access_key", c.repository.accessKey);
    c.repository.secretKey = r.value("secret_key", c.repository.secretKey);
    c.repository.region = r.value("region", c.repository.region);
    c.repository.prefix = r.value("prefix", c.repository.prefix);

    auto w = j.value("windows", json::object());
    c.windows.useVss = w.value("use_vss", c.windows.useVss);
    c.windows.useUsn = w.value("use_usn", c.windows.useUsn);

    auto e = j.value("encryption", json::object());
    c.encryption.enabled = e.value("enabled", c.encryption.enabled);
    c.encryption.keyHex = e.value("key_hex", c.encryption.keyHex);

    auto a = j.value("agent", json::object());
    c.agent.pollSeconds = a.value("poll_seconds", c.agent.pollSeconds);

    for (const auto& item : j.value("jobs", json::array())) {
        JobConfig job;
        job.name = item.value("name", job.name);
        job.source = item.value("source", job.source);
        job.enabled = item.value("enabled", job.enabled);
        job.intervalMinutes = item.value("interval_minutes", job.intervalMinutes);
        job.retentionKeepLast = item.value("retention_keep_last", job.retentionKeepLast);
        job.lastRunAt = item.value("last_run_at", job.lastRunAt);
        if (!job.name.empty() && !job.source.empty()) c.jobs.push_back(std::move(job));
    }
    return c;
}

void Config::save(const std::filesystem::path& path) const {
    json j;
    j["db_path"] = dbPath;
    j["chunk_size"] = chunkSize;
    j["compression_level"] = compressionLevel;
    j["repository"] = {
        {"type", repository.type},
        {"path", repository.path},
        {"endpoint", repository.endpoint},
        {"bucket", repository.bucket},
        {"access_key", repository.accessKey},
        {"secret_key", repository.secretKey},
        {"region", repository.region},
        {"prefix", repository.prefix}
    };
    j["windows"] = {
        {"use_vss", windows.useVss},
        {"use_usn", windows.useUsn}
    };
    j["encryption"] = {
        {"enabled", encryption.enabled},
        {"key_hex", encryption.keyHex}
    };
    j["agent"] = {
        {"poll_seconds", agent.pollSeconds}
    };
    j["jobs"] = json::array();
    for (const auto& job : jobs) {
        j["jobs"].push_back({
            {"name", job.name},
            {"source", job.source},
            {"enabled", job.enabled},
            {"interval_minutes", job.intervalMinutes},
            {"retention_keep_last", job.retentionKeepLast},
            {"last_run_at", job.lastRunAt}
        });
    }

    std::ofstream out(path);
    if (!out) throw std::runtime_error("cannot write config: " + path.string());
    out << j.dump(2) << "\n";
}

}
