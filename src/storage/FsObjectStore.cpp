#include "keeply/FsObjectStore.hpp"
#include "keeply/Util.hpp"

#include <fstream>
#include <stdexcept>

namespace keeply {

FsObjectStore::FsObjectStore(std::filesystem::path root) : root_(std::move(root)) {
    std::filesystem::create_directories(root_);
}

std::filesystem::path FsObjectStore::resolve(const std::string& key) const {
    auto p = root_ / std::filesystem::path(key);
    return p.lexically_normal();
}

bool FsObjectStore::exists(const std::string& key) {
    return std::filesystem::exists(resolve(key));
}

void FsObjectStore::put(const std::string& key, const std::vector<unsigned char>& data) {
    auto path = resolve(key);
    ensureParentDir(path);
    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    if (!out) throw std::runtime_error("cannot write object: " + path.string());
    out.write(reinterpret_cast<const char*>(data.data()), static_cast<std::streamsize>(data.size()));
}

std::vector<unsigned char> FsObjectStore::get(const std::string& key) {
    auto path = resolve(key);
    std::ifstream in(path, std::ios::binary);
    if (!in) throw std::runtime_error("cannot read object: " + path.string());
    return std::vector<unsigned char>((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
}

void FsObjectStore::remove(const std::string& key) {
    std::filesystem::remove(resolve(key));
}

}
