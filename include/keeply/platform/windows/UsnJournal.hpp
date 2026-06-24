#pragma once

#include <filesystem>
#include <string>
#include <vector>

namespace keeply::platform::windows {

struct UsnChange {
    std::filesystem::path path;
    std::string reason;
};

class UsnJournal {
public:
    explicit UsnJournal(std::filesystem::path volumeRoot);
    std::vector<UsnChange> readChangesSince(unsigned long long lastUsn);
private:
    std::filesystem::path volumeRoot_;
};

}
