#include "keeply/AgentRunner.hpp"

#include <iostream>
#include <map>
#include <stdexcept>
#include <string>

using namespace keeply;

static void usage() {
    std::cout << R"USAGE(keeply-daemon 0.1.0

Usage:
  keeply-daemon --config FILE
  keeply-daemon --config FILE --job NAME
  keeply-daemon --config FILE --once
  keeply-daemon --config FILE --once --job NAME
)USAGE";
}

static std::map<std::string, std::string> parseArgs(int argc, char** argv) {
    std::map<std::string, std::string> args;
    for (int i = 1; i < argc; ++i) {
        std::string key = argv[i];
        if (key == "--help" || key == "-h") {
            args["help"] = "true";
            continue;
        }
        if (key == "--once") {
            args["once"] = "true";
            continue;
        }
        if (key.rfind("--", 0) != 0) throw std::runtime_error("expected --flag, got: " + key);
        if (i + 1 >= argc) throw std::runtime_error("missing value for: " + key);
        args[key.substr(2)] = argv[++i];
    }
    return args;
}

static std::string required(const std::map<std::string, std::string>& args, const std::string& key) {
    auto it = args.find(key);
    if (it == args.end() || it->second.empty()) throw std::runtime_error("missing --" + key);
    return it->second;
}

int main(int argc, char** argv) {
    try {
        auto args = parseArgs(argc, argv);
        if (args.count("help") || argc < 2) {
            usage();
            return argc < 2 ? 1 : 0;
        }
        const auto configPath = required(args, "config");
        const auto jobName = args.count("job") ? args["job"] : "";
        AgentRunner runner(configPath);
        if (args.count("once")) return runner.runOnce(jobName) > 0 ? 0 : 1;
        return runner.runLoop(jobName);
    } catch (const std::exception& e) {
        std::cerr << "error: " << e.what() << "\n";
        return 2;
    }
}
