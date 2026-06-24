#include "keeply/platform/windows/UsnJournal.hpp"

namespace keeply::platform::windows {

UsnJournal::UsnJournal(std::filesystem::path volumeRoot) : volumeRoot_(std::move(volumeRoot)) {}

std::vector<UsnChange> UsnJournal::readChangesSince(unsigned long long) {
    return {};
}

}
