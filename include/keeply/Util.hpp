#pragma once

#include <cstdint>
#include <filesystem>
#include <string>
#include <vector>

namespace keeply {

std::string nowUtcIso();
std::string dateStampUtc();
std::string amzDateUtc();
std::int64_t nowUnix();
std::int64_t utcIsoToUnix(const std::string& value);
std::string randomHex(std::size_t bytes);
std::string makeSnapshotId();
std::string toHex(const unsigned char* data, std::size_t len);
std::vector<unsigned char> fromString(const std::string& s);
std::string toString(const std::vector<unsigned char>& data);
std::int64_t fileTimeToUnix(const std::filesystem::file_time_type& ftime);
std::filesystem::file_time_type unixToFileTime(std::int64_t timestamp);
std::string normalizeKeyPrefix(std::string prefix);
std::string joinObjectKey(const std::string& prefix, const std::string& key);
void ensureParentDir(const std::filesystem::path& path);
std::string genericRelativePath(const std::filesystem::path& path, const std::filesystem::path& root);

}
