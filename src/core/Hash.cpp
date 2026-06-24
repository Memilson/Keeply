#include "keeply/Hash.hpp"
#include "keeply/Util.hpp"

#include <openssl/hmac.h>
#include <stdexcept>

namespace keeply {

std::string sha256Hex(const unsigned char* data, std::size_t len) {
    unsigned char out[SHA256_DIGEST_LENGTH];
    SHA256(data, len, out);
    return toHex(out, SHA256_DIGEST_LENGTH);
}

std::string sha256Hex(const std::vector<unsigned char>& data) {
    return sha256Hex(data.data(), data.size());
}

std::string sha256Hex(const std::string& data) {
    return sha256Hex(reinterpret_cast<const unsigned char*>(data.data()), data.size());
}

std::vector<unsigned char> hmacSha256(const std::vector<unsigned char>& key, const std::string& data) {
    unsigned int len = 0;
    unsigned char out[EVP_MAX_MD_SIZE];
    if (!HMAC(EVP_sha256(), key.data(), static_cast<int>(key.size()),
              reinterpret_cast<const unsigned char*>(data.data()), data.size(), out, &len)) {
        throw std::runtime_error("HMAC-SHA256 failed");
    }
    return std::vector<unsigned char>(out, out + len);
}

std::vector<unsigned char> hmacSha256(const std::string& key, const std::string& data) {
    return hmacSha256(std::vector<unsigned char>(key.begin(), key.end()), data);
}

Sha256State::Sha256State() {
    SHA256_Init(&ctx_);
}

void Sha256State::update(const unsigned char* data, std::size_t len) {
    if (finalized_) throw std::runtime_error("sha256 state already finalized");
    SHA256_Update(&ctx_, data, len);
}

void Sha256State::update(const std::vector<unsigned char>& data) {
    update(data.data(), data.size());
}

std::string Sha256State::finalHex() {
    if (finalized_) throw std::runtime_error("sha256 state already finalized");
    unsigned char out[SHA256_DIGEST_LENGTH];
    SHA256_Final(out, &ctx_);
    finalized_ = true;
    return toHex(out, SHA256_DIGEST_LENGTH);
}

}
