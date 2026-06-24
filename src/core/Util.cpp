#include "keeply/Util.hpp"

#include <chrono>
#include <ctime>
#include <iomanip>
#include <random>
#include <sstream>
#include <stdexcept>

#ifdef _WIN32
#include <ctime>
#define timegm _mkgmtime
#endif

namespace keeply {

static std::tm utcTm(std::time_t t) {
    std::tm tm{};
#ifdef _WIN32
    gmtime_s(&tm, &t);
#else
    gmtime_r(&t, &tm);
#endif
    return tm;
}

std::string nowUtcIso() {
    auto now = std::chrono::system_clock::now();
    auto tt = std::chrono::system_clock::to_time_t(now);
    auto tm = utcTm(tt);
    std::ostringstream os;
    os << std::put_time(&tm, "%Y-%m-%dT%H:%M:%SZ");
    return os.str();
}

std::string dateStampUtc() {
    auto now = std::chrono::system_clock::now();
    auto tt = std::chrono::system_clock::to_time_t(now);
    auto tm = utcTm(tt);
    std::ostringstream os;
    os << std::put_time(&tm, "%Y%m%d");
    return os.str();
}

std::string amzDateUtc() {
    auto now = std::chrono::system_clock::now();
    auto tt = std::chrono::system_clock::to_time_t(now);
    auto tm = utcTm(tt);
    std::ostringstream os;
    os << std::put_time(&tm, "%Y%m%dT%H%M%SZ");
    return os.str();
}

std::int64_t nowUnix() {
    return static_cast<std::int64_t>(std::chrono::system_clock::to_time_t(std::chrono::system_clock::now()));
}

std::int64_t utcIsoToUnix(const std::string& value) {
    std::tm tm{};
    std::istringstream is(value);
    is >> std::get_time(&tm, "%Y-%m-%dT%H:%M:%SZ");
    if (!is) return 0;
    return static_cast<std::int64_t>(timegm(&tm));
}

std::string toHex(const unsigned char* data, std::size_t len) {
    std::ostringstream os;
    os << std::hex << std::setfill('0');
    for (std::size_t i = 0; i < len; ++i) os << std::setw(2) << static_cast<int>(data[i]);
    return os.str();
}

std::string randomHex(std::size_t bytes) {
    std::random_device rd;
    std::mt19937_64 gen(rd());
    std::uniform_int_distribution<int> dist(0, 255);
    std::vector<unsigned char> buf(bytes);
    for (auto& b : buf) b = static_cast<unsigned char>(dist(gen));
    return toHex(buf.data(), buf.size());
}

std::string makeSnapshotId() {
    auto now = std::chrono::system_clock::now();
    auto tt = std::chrono::system_clock::to_time_t(now);
    auto tm = utcTm(tt);
    std::ostringstream os;
    os << std::put_time(&tm, "%Y%m%dT%H%M%SZ") << "-" << randomHex(8);
    return os.str();
}

std::vector<unsigned char> fromString(const std::string& s) {
    return std::vector<unsigned char>(s.begin(), s.end());
}

std::string toString(const std::vector<unsigned char>& data) {
    return std::string(data.begin(), data.end());
}

std::int64_t fileTimeToUnix(const std::filesystem::file_time_type& ftime) {
    using namespace std::chrono;
    auto sctp = time_point_cast<system_clock::duration>(ftime - std::filesystem::file_time_type::clock::now() + system_clock::now());
    return static_cast<std::int64_t>(system_clock::to_time_t(sctp));
}

std::filesystem::file_time_type unixToFileTime(std::int64_t timestamp) {
    using namespace std::chrono;
    auto sys = system_clock::from_time_t(static_cast<std::time_t>(timestamp));
    return time_point_cast<std::filesystem::file_time_type::duration>(sys - system_clock::now() + std::filesystem::file_time_type::clock::now());
}

std::string normalizeKeyPrefix(std::string prefix) {
    while (!prefix.empty() && prefix.front() == '/') prefix.erase(prefix.begin());
    while (!prefix.empty() && prefix.back() == '/') prefix.pop_back();
    return prefix;
}

std::string joinObjectKey(const std::string& prefix, const std::string& key) {
    auto p = normalizeKeyPrefix(prefix);
    if (p.empty()) return key;
    if (key.empty()) return p;
    return p + "/" + key;
}

void ensureParentDir(const std::filesystem::path& path) {
    auto parent = path.parent_path();
    if (!parent.empty()) std::filesystem::create_directories(parent);
}

std::string genericRelativePath(const std::filesystem::path& path, const std::filesystem::path& root) {
    auto rel = std::filesystem::relative(path, root);
    return rel.generic_string();
}

}
