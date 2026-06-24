#pragma once

#include <filesystem>

namespace keeply {

class InteractiveShell {
public:
    explicit InteractiveShell(std::filesystem::path configPath);
    int run();
private:
    std::filesystem::path configPath_;
};

}
