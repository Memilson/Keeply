#include "keeply/InteractiveShell.hpp"
#include "keeply/AgentRunner.hpp"
#include "keeply/BackupEngine.hpp"
#include "keeply/Config.hpp"
#include "keeply/Crypto.hpp"
#include "keeply/LocalDb.hpp"
#include "keeply/PruneEngine.hpp"
#include "keeply/RestoreEngine.hpp"
#include "keeply/VerifyEngine.hpp"

#include <algorithm>
#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <string>
#include <utility>

namespace keeply {

static std::string ask(const std::string& label, const std::string& fallback = "") {
    std::cout << label;
    if (!fallback.empty()) std::cout << " [" << fallback << "]";
    std::cout << ": ";
    std::string value;
    std::getline(std::cin, value);
    return value.empty() ? fallback : value;
}

static int askInt(const std::string& label, int fallback) {
    auto value = ask(label, std::to_string(fallback));
    return std::stoi(value);
}

static JobConfig* findJob(Config& config, const std::string& name) {
    for (auto& job : config.jobs) {
        if (job.name == name) return &job;
    }
    return nullptr;
}

static void listJobs(const Config& config) {
    if (config.jobs.empty()) {
        std::cout << "nenhum job\n";
        return;
    }
    for (const auto& job : config.jobs) {
        std::cout << job.name << " | " << (job.enabled ? "ativo" : "inativo") << " | " << job.intervalMinutes << "min | keep=" << job.retentionKeepLast << " | " << job.source << "\n";
    }
}

InteractiveShell::InteractiveShell(std::filesystem::path configPath) : configPath_(std::move(configPath)) {}

int InteractiveShell::run() {
    for (;;) {
        auto config = Config::load(configPath_);
        std::cout << "\nKeeply Agent\n";
        std::cout << "1 jobs\n";
        std::cout << "2 adicionar job\n";
        std::cout << "3 executar job\n";
        std::cout << "4 backup manual\n";
        std::cout << "5 snapshots\n";
        std::cout << "6 restore\n";
        std::cout << "7 verify\n";
        std::cout << "8 prune\n";
        std::cout << "9 criptografia\n";
        std::cout << "0 sair\n";
        auto choice = ask("opcao");
        try {
            if (choice == "0") return 0;
            if (choice == "1") {
                listJobs(config);
            } else if (choice == "2") {
                JobConfig job;
                job.name = ask("nome");
                job.source = ask("source");
                job.intervalMinutes = askInt("intervalo minutos", 60);
                job.retentionKeepLast = askInt("manter ultimos", 10);
                if (job.name.empty() || job.source.empty()) throw std::runtime_error("nome e source sao obrigatorios");
                if (findJob(config, job.name)) throw std::runtime_error("job ja existe");
                config.jobs.push_back(job);
                config.save(configPath_);
                std::cout << "salvo\n";
            } else if (choice == "3") {
                listJobs(config);
                auto name = ask("job");
                AgentRunner runner(configPath_);
                runner.runOnce(name);
            } else if (choice == "4") {
                auto source = ask("source");
                BackupEngine engine(config);
                auto r = engine.run(source);
                std::cout << "snapshot: " << r.snapshotId << "\n";
            } else if (choice == "5") {
                LocalDb db(config.dbPath);
                db.migrate();
                for (const auto& row : db.listSnapshots()) {
                    std::cout << row.id << " | " << row.createdAt << " | " << row.status << " | files=" << row.totalFiles << " | bytes=" << row.totalBytes << " | " << row.source << "\n";
                }
            } else if (choice == "6") {
                auto snapshot = ask("snapshot", "latest");
                auto target = ask("target");
                RestoreEngine engine(config);
                auto r = engine.run(snapshot, target);
                std::cout << "restored: files=" << r.files << " bytes=" << r.bytes << "\n";
            } else if (choice == "7") {
                auto snapshot = ask("snapshot", "latest");
                VerifyEngine engine(config);
                if (snapshot == "all") {
                    for (const auto& r : engine.runAll()) std::cout << r.snapshotId << " | files=" << r.files << " chunks=" << r.chunks << " errors=" << r.errors << "\n";
                } else {
                    auto r = engine.run(snapshot);
                    std::cout << r.snapshotId << " | files=" << r.files << " chunks=" << r.chunks << " errors=" << r.errors << "\n";
                }
            } else if (choice == "8") {
                int keep = askInt("manter ultimos", 10);
                auto source = ask("source vazio=todos");
                if (!source.empty()) source = std::filesystem::absolute(source).string();
                PruneEngine engine(config);
                auto r = engine.keepLast(keep, source);
                std::cout << "snapshots=" << r.snapshotsDeleted << " chunks=" << r.chunksDeleted << " errors=" << r.errors << "\n";
            } else if (choice == "9") {
                auto enabled = ask("ativar s/n", config.encryption.enabled ? "s" : "n");
                config.encryption.enabled = enabled == "s" || enabled == "S";
                if (config.encryption.enabled && config.encryption.keyHex.empty()) config.encryption.keyHex = generateEncryptionKeyHex();
                config.save(configPath_);
                std::cout << "enabled=" << (config.encryption.enabled ? "true" : "false") << "\n";
                if (config.encryption.enabled) std::cout << "key_hex: " << config.encryption.keyHex << "\n";
            }
        } catch (const std::exception& e) {
            std::cerr << "erro: " << e.what() << "\n";
        }
    }
}

}
