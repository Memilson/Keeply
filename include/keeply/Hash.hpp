#pragma once

#include <cstddef>
#include <openssl/sha.h>
#include <string>
#include <vector>

namespace keeply {

std::string sha256Hex(const unsigned char* data, std::size_t len);
std::string sha256Hex(const std::vector<unsigned char>& data);
std::string sha256Hex(const std::string& data);
std::vector<unsigned char> hmacSha256(const std::vector<unsigned char>& key, const std::string& data);
std::vector<unsigned char> hmacSha256(const std::string& key, const std::string& data);

class Sha256State {
public:
    Sha256State();
    void update(const unsigned char* data, std::size_t len);
    void update(const std::vector<unsigned char>& data);
    std::string finalHex();
private:
    SHA256_CTX ctx_{};
    bool finalized_{false};
};

}
