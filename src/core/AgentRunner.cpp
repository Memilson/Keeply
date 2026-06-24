#include "keeply/AgentRunner.hpp"
#include "keeply/BackupEngine.hpp"
#include "keeply/LocalDb.hpp"
#include "keeply/PruneEngine.hpp"
#include "keeply/Util.hpp"

#include <chrono>
#include <algorithm>
#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <thread>
#include <utility>

namespace keeply {

AgentRunner::AgentRunner(std::filesystem::path configPath) : configPath_(std::move(configPath)) {}

bool AgentRunner::due(const JobConfig& job, const std::optional<JobStateRow>& state) const {
    const auto lastRunAt = state && !state->lastRunAt.empty() ? state->lastRunAt : job.lastRunAt;
    if (lastRunAt.empty()) return true;
    const auto last = utcIsoToUnix(lastRunAt);
    if (last <= 0) return true;
    return nowUnix() - last >= static_cast<std::int64_t>(std::max(1, job.intervalMinutes)) * 60;
}

void AgentRunner::runJob(Config& config, JobConfig& job) {
    if (job.source.empty()) throw std::runtime_error("job source is empty: " + job.name);
    std::cout << "job: " << job.name << "\n";
    LocalDb db(config.dbPath);
    db.migrate();
    auto previous = db.getJobState(job.name);
    db.updateJobState(JobStateRow{job.name, previous ? previous->lastRunAt : job.lastRunAt, "running", "", previous ? previous->lastSnapshotId : ""});
    db.appendDaemonEvent("info", job.name, "job started");
    BackupEngine backup(config);
    try {
        auto result = backup.run(job.source);
        const auto finishedAt = nowUtcIso();
        job.lastRunAt = finishedAt;
        db.updateJobState(JobStateRow{job.name, finishedAt, "completed", "", result.snapshotId});
        db.appendDaemonEvent("info", job.name, "job completed: " + result.snapshotId);
        config.save(configPath_);
        if (job.retentionKeepLast >= 0) {
            PruneEngine prune(config);
            auto pr = prune.keepLast(job.retentionKeepLast, std::filesystem::absolute(job.source).string());
            if (pr.snapshotsDeleted > 0 || pr.chunksDeleted > 0) {
                std::cout << "prune: snapshots=" << pr.snapshotsDeleted << " chunks=" << pr.chunksDeleted << " errors=" << pr.errors << "\n";
            }
        }
        std::cout << "job_done: " << result.snapshotId << "\n";
    } catch (const std::exception& e) {
        db.updateJobState(JobStateRow{job.name, nowUtcIso(), "failed", e.what(), ""});
        db.appendDaemonEvent("error", job.name, e.what());
        throw;
    }
}

int AgentRunner::runOnce(const std::string& jobName) {
    auto config = Config::load(configPath_);
    LocalDb db(config.dbPath);
    db.migrate();
    db.setDaemonState("status", "running-once");
    db.setDaemonState("last_seen_at", nowUtcIso());
    db.setDaemonState("last_run_once_at", nowUtcIso());
    int count = 0;
    for (auto& job : config.jobs) {
        if (!job.enabled) continue;
        if (!jobName.empty() && job.name != jobName) continue;
        runJob(config, job);
        count++;
    }
    db.setDaemonState("status", "idle");
    db.setDaemonState("last_seen_at", nowUtcIso());
    if (count == 0) {
        db.appendDaemonEvent("info", "daemon", "no jobs matched");
        std::cout << "no jobs matched\n";
    }
    return count;
}

int AgentRunner::runLoop(const std::string& jobName) {
    std::cout << "agent running\n";
    for (;;) {
        auto config = Config::load(configPath_);
        LocalDb db(config.dbPath);
        db.migrate();
        db.setDaemonState("status", "running");
        db.setDaemonState("last_seen_at", nowUtcIso());
        int ran = 0;
        for (auto& job : config.jobs) {
            if (!job.enabled) continue;
            if (!jobName.empty() && job.name != jobName) continue;
            auto state = db.getJobState(job.name);
            if (!due(job, state)) continue;
            try {
                runJob(config, job);
                ran++;
            } catch (const std::exception& e) {
                std::cerr << "job_error: " << job.name << ": " << e.what() << "\n";
            }
        }
        const int sleepSeconds = std::max(5, config.agent.pollSeconds);
        if (ran == 0) std::this_thread::sleep_for(std::chrono::seconds(sleepSeconds));
    }
}

}
