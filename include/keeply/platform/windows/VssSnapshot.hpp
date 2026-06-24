#pragma once

#include <filesystem>
#include <string>

namespace keeply::platform::windows {

class VssSnapshot {
public:
    explicit VssSnapshot(std::filesystem::path volumeRoot);
    ~VssSnapshot();
    std::filesystem::path snapshotRoot() const;
    bool active() const;
private:
    std::filesystem::path root_;
};

}
