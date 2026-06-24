#pragma once

#include <string>
#include <vector>

namespace keeply {

class ObjectStore {
public:
    virtual ~ObjectStore() = default;
    virtual bool exists(const std::string& key) = 0;
    virtual void put(const std::string& key, const std::vector<unsigned char>& data) = 0;
    virtual std::vector<unsigned char> get(const std::string& key) = 0;
    virtual void remove(const std::string& key) = 0;
};

}
