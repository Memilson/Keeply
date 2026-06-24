#pragma once

#include "keeply/ObjectStore.hpp"

#include <filesystem>

namespace keeply {

class FsObjectStore final : public ObjectStore {
public:
    explicit FsObjectStore(std::filesystem::path root);
    bool exists(const std::string& key) override;
    void put(const std::string& key, const std::vector<unsigned char>& data) override;
    std::vector<unsigned char> get(const std::string& key) override;
    void remove(const std::string& key) override;
private:
    std::filesystem::path resolve(const std::string& key) const;
    std::filesystem::path root_;
};

}
