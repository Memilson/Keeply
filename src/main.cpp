#include "keeply/AgentRunner.hpp"
#include "keeply/BackupEngine.hpp"
#include "keeply/Config.hpp"
#include "keeply/Crypto.hpp"
#include "keeply/InteractiveShell.hpp"
#include "keeply/LocalDb.hpp"
#include "keeply/PruneEngine.hpp"
#include "keeply/RestoreEngine.hpp"
#include "keeply/VerifyEngine.hpp"

#include <algorithm>
#include <filesystem>
#include <iostream>
#include <map>
#include <stdexcept>
#include <string>
#include <vector>

using namespace keeply;

static void usage() {
    std::cout << R"USAGE(keeply-agent 0.1.0

Commands:
  init-local --config FILE --repo PATH --db FILE
  init-s3 --config FILE --endpoint URL --bucket NAME --access-key KEY --secret-key SECRET --region REGION --prefix PREFIX --db FILE
  keygen
  encryption --config FILE --enabled true|false --key-hex HEX
  backup --config FILE --source PATH
  job-add --config FILE --name NAME --source PATH --interval-minutes N --keep-last N
  job-list --config FILE
  job-remove --config FILE --name NAME
  job-enable --config FILE --name NAME
  job-disable --config FILE --name NAME
  run --config FILE
  run-once --config FILE --job NAME
  status --config FILE
  events --config FILE --limit N
  list --config FILE
  verify --config FILE --snapshot ID|latest|all
  prune --config FILE --keep-last N
  restore --config FILE --snapshot ID|latest --target PATH
  ui --config FILE

Examples:
  keeply-agent init-local --config keeply.local.json --repo ./repo --db ./keeply.db
  keeply-agent backup --config keeply.local.json --source ./testdata
  keeply-agent restore --config keeply.local.json --snapshot latest --target ./restore
)USAGE";
}

static std::map<std::string, std::string> parseArgs(int argc, char** argv, int start) {
    std::map<std::string, std::string> m;
    for (int i = start; i < argc; ++i) {
        std::string k = argv[i];
        if (k.rfind("--", 0) != 0) throw std::runtime_error("expected --flag, got: " + k);
        if (i + 1 >= argc) throw std::runtime_error("missing value for: " + k);
        m[k.substr(2)] = argv[++i];
    }
    return m;
}

static std::string required(const std::map<std::string, std::string>& a, const std::string& k) {
    auto it = a.find(k);
    if (it == a.end() || it->second.empty()) throw std::runtime_error("missing --" + k);
    return it->second;
}

static JobConfig* findJob(Config& config, const std::string& name) {
    for (auto& job : config.jobs) {
        if (job.name == name) return &job;
    }
    return nullptr;
}

int main(int argc, char** argv) {
    try {
        if (argc < 2) {
            usage();
            return 1;
        }
        const std::string cmd = argv[1];
        if (cmd == "help" || cmd == "--help" || cmd == "-h") {
            usage();
            return 0;
        }

        auto args = parseArgs(argc, argv, 2);

        if (cmd == "keygen") {
            std::cout << generateEncryptionKeyHex() << "\n";
            return 0;
        }

        if (cmd == "init-local") {
            Config c;
            c.dbPath = required(args, "db");
            c.repository.type = "local";
            c.repository.path = required(args, "repo");
            c.repository.prefix = "";
            c.save(required(args, "config"));
            LocalDb db(c.dbPath);
            db.migrate();
            std::cout << "created local config: " << required(args, "config") << "\n";
            return 0;
        }

        if (cmd == "init-s3") {
            Config c;
            c.dbPath = required(args, "db");
            c.repository.type = "s3";
            c.repository.endpoint = required(args, "endpoint");
            c.repository.bucket = required(args, "bucket");
            c.repository.accessKey = required(args, "access-key");
            c.repository.secretKey = required(args, "secret-key");
            c.repository.region = args.count("region") ? args["region"] : "us-east-1";
            c.repository.prefix = args.count("prefix") ? args["prefix"] : "repos/default";
            c.save(required(args, "config"));
            LocalDb db(c.dbPath);
            db.migrate();
            std::cout << "created s3/minio config: " << required(args, "config") << "\n";
            return 0;
        }

        const auto configPath = required(args, "config");
        auto config = Config::load(configPath);

        if (cmd == "ui") {
            InteractiveShell shell(configPath);
            return shell.run();
        }

        if (cmd == "encryption") {
            const auto enabled = required(args, "enabled");
            config.encryption.enabled = enabled == "true" || enabled == "1" || enabled == "yes";
            if (args.count("key-hex")) config.encryption.keyHex = args["key-hex"];
            if (config.encryption.enabled && config.encryption.keyHex.empty()) config.encryption.keyHex = generateEncryptionKeyHex();
            config.save(configPath);
            std::cout << "encryption_enabled: " << (config.encryption.enabled ? "true" : "false") << "\n";
            if (config.encryption.enabled) std::cout << "key_hex: " << config.encryption.keyHex << "\n";
            return 0;
        }

        if (cmd == "job-add") {
            JobConfig job;
            job.name = required(args, "name");
            job.source = required(args, "source");
            job.intervalMinutes = args.count("interval-minutes") ? std::stoi(args["interval-minutes"]) : 60;
            job.retentionKeepLast = args.count("keep-last") ? std::stoi(args["keep-last"]) : 10;
            if (findJob(config, job.name)) throw std::runtime_error("job already exists: " + job.name);
            config.jobs.push_back(job);
            config.save(configPath);
            std::cout << "job added: " << job.name << "\n";
            return 0;
        }

        if (cmd == "job-list") {
            for (const auto& job : config.jobs) {
                std::cout << job.name << " | " << (job.enabled ? "enabled" : "disabled") << " | " << job.intervalMinutes << "min | keep=" << job.retentionKeepLast << " | " << job.source << "\n";
            }
            return 0;
        }

        if (cmd == "job-remove") {
            const auto name = required(args, "name");
            const auto before = config.jobs.size();
            config.jobs.erase(std::remove_if(config.jobs.begin(), config.jobs.end(), [&](const JobConfig& job) { return job.name == name; }), config.jobs.end());
            if (config.jobs.size() == before) throw std::runtime_error("job not found: " + name);
            config.save(configPath);
            std::cout << "job removed: " << name << "\n";
            return 0;
        }

        if (cmd == "job-enable" || cmd == "job-disable") {
            const auto name = required(args, "name");
            auto* job = findJob(config, name);
            if (!job) throw std::runtime_error("job not found: " + name);
            job->enabled = cmd == "job-enable";
            config.save(configPath);
            std::cout << "job " << (job->enabled ? "enabled" : "disabled") << ": " << name << "\n";
            return 0;
        }

        if (cmd == "run") {
            AgentRunner runner(configPath);
            return runner.runLoop(args.count("job") ? args["job"] : "");
        }

        if (cmd == "run-once") {
            AgentRunner runner(configPath);
            runner.runOnce(args.count("job") ? args["job"] : "");
            return 0;
        }

        if (cmd == "status") {
            LocalDb db(config.dbPath);
            db.migrate();
            std::cout << "daemon_status: " << db.getDaemonState("status").value_or("unknown") << "\n";
            std::cout << "last_seen_at: " << db.getDaemonState("last_seen_at").value_or("") << "\n";
            for (const auto& row : db.listJobStates()) {
                std::cout << row.jobName << " | " << row.lastStatus << " | " << row.lastRunAt << " | " << row.lastSnapshotId;
                if (!row.lastError.empty()) std::cout << " | error=" << row.lastError;
                std::cout << "\n";
            }
            return 0;
        }

        if (cmd == "events") {
            LocalDb db(config.dbPath);
            db.migrate();
            const int limit = args.count("limit") ? std::stoi(args["limit"]) : 50;
            for (const auto& row : db.listDaemonEvents(limit)) {
                std::cout << row.id << " | " << row.createdAt << " | " << row.level << " | " << row.source << " | " << row.message << "\n";
            }
            return 0;
        }

        if (cmd == "backup") {
            const auto source = required(args, "source");
            BackupEngine engine(config);
            auto r = engine.run(source);
            std::cout << "\nBackup completed\n";
            std::cout << "snapshot: " << r.snapshotId << "\n";
            std::cout << "manifest: " << r.manifestKey << "\n";
            std::cout << "files: " << r.files << "\n";
            std::cout << "bytes: " << r.bytes << "\n";
            std::cout << "uploaded_chunks: " << r.uploadedChunks << "\n";
            std::cout << "reused_chunks: " << r.reusedChunks << "\n";
            return 0;
        }

        if (cmd == "list") {
            LocalDb db(config.dbPath);
            db.migrate();
            auto rows = db.listSnapshots();
            for (const auto& r : rows) {
                std::cout << r.id << " | " << r.createdAt << " | " << r.status << " | files=" << r.totalFiles << " | bytes=" << r.totalBytes << " | " << r.source << "\n";
            }
            return 0;
        }

        if (cmd == "verify") {
            const auto snapshot = required(args, "snapshot");
            VerifyEngine engine(config);
            if (snapshot == "all") {
                std::int64_t errors = 0;
                for (const auto& r : engine.runAll()) {
                    errors += r.errors;
                    std::cout << r.snapshotId << " | files=" << r.files << " | chunks=" << r.chunks << " | bytes=" << r.bytes << " | errors=" << r.errors << "\n";
                }
                return errors == 0 ? 0 : 3;
            }
            auto r = engine.run(snapshot);
            std::cout << r.snapshotId << " | files=" << r.files << " | chunks=" << r.chunks << " | bytes=" << r.bytes << " | errors=" << r.errors << "\n";
            return r.errors == 0 ? 0 : 3;
        }

        if (cmd == "prune") {
            const int keepLast = std::stoi(required(args, "keep-last"));
            std::string source = args.count("source") ? std::filesystem::absolute(args["source"]).string() : "";
            PruneEngine engine(config);
            auto r = engine.keepLast(keepLast, source);
            std::cout << "snapshots_deleted: " << r.snapshotsDeleted << "\n";
            std::cout << "chunks_deleted: " << r.chunksDeleted << "\n";
            std::cout << "chunks_kept: " << r.chunksKept << "\n";
            std::cout << "errors: " << r.errors << "\n";
            return r.errors == 0 ? 0 : 3;
        }

        if (cmd == "restore") {
            const auto snapshot = required(args, "snapshot");
            const auto target = required(args, "target");
            RestoreEngine engine(config);
            auto r = engine.run(snapshot, target);
            std::cout << "\nRestore completed\n";
            std::cout << "snapshot: " << r.snapshotId << "\n";
            std::cout << "files: " << r.files << "\n";
            std::cout << "bytes: " << r.bytes << "\n";
            return 0;
        }

        throw std::runtime_error("unknown command: " + cmd);
    } catch (const std::exception& e) {
        std::cerr << "error: " << e.what() << "\n";
        return 2;
    }
}
