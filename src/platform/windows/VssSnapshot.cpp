#include "keeply/platform/windows/VssSnapshot.hpp"

namespace keeply::platform::windows {

VssSnapshot::VssSnapshot(std::filesystem::path volumeRoot) : root_(std::move(volumeRoot)) {}

VssSnapshot::~VssSnapshot() = default;

std::filesystem::path VssSnapshot::snapshotRoot() const {
    return root_;
}

bool VssSnapshot::active() const {
    return false;
}

}
