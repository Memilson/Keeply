#include "keeply/Crypto.hpp"
#include "keeply/Util.hpp"

#include <openssl/evp.h>
#include <openssl/rand.h>
#include <cctype>
#include <cstring>
#include <memory>
#include <stdexcept>

namespace keeply {

static const unsigned char magic[] = {'K','P','L','Y','E','N','C','1'};

static int hexValue(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static std::vector<unsigned char> keyFromHex(const std::string& hex) {
    if (hex.size() != 64) throw std::runtime_error("encryption key must be 64 hex chars");
    std::vector<unsigned char> key(32);
    for (std::size_t i = 0; i < key.size(); ++i) {
        const int hi = hexValue(hex[i * 2]);
        const int lo = hexValue(hex[i * 2 + 1]);
        if (hi < 0 || lo < 0) throw std::runtime_error("encryption key must be hex");
        key[i] = static_cast<unsigned char>((hi << 4) | lo);
    }
    return key;
}

static bool hasMagic(const std::vector<unsigned char>& data) {
    return data.size() >= sizeof(magic) && std::memcmp(data.data(), magic, sizeof(magic)) == 0;
}

std::string generateEncryptionKeyHex() {
    unsigned char key[32];
    if (RAND_bytes(key, sizeof(key)) != 1) throw std::runtime_error("RAND_bytes failed");
    return toHex(key, sizeof(key));
}

std::vector<unsigned char> protectData(const std::vector<unsigned char>& data, const EncryptionConfig& config) {
    if (!config.enabled) return data;
    auto key = keyFromHex(config.keyHex);
    unsigned char nonce[12];
    unsigned char tag[16];
    if (RAND_bytes(nonce, sizeof(nonce)) != 1) throw std::runtime_error("RAND_bytes failed");
    using Ctx = std::unique_ptr<EVP_CIPHER_CTX, decltype(&EVP_CIPHER_CTX_free)>;
    Ctx ctx(EVP_CIPHER_CTX_new(), EVP_CIPHER_CTX_free);
    if (!ctx) throw std::runtime_error("EVP_CIPHER_CTX_new failed");
    if (EVP_EncryptInit_ex(ctx.get(), EVP_aes_256_gcm(), nullptr, nullptr, nullptr) != 1) throw std::runtime_error("encrypt init failed");
    if (EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_SET_IVLEN, sizeof(nonce), nullptr) != 1) throw std::runtime_error("encrypt ivlen failed");
    if (EVP_EncryptInit_ex(ctx.get(), nullptr, nullptr, key.data(), nonce) != 1) throw std::runtime_error("encrypt key failed");
    std::vector<unsigned char> cipher(data.size());
    int outLen = 0;
    int total = 0;
    if (!data.empty() && EVP_EncryptUpdate(ctx.get(), cipher.data(), &outLen, data.data(), static_cast<int>(data.size())) != 1) throw std::runtime_error("encrypt update failed");
    total += outLen;
    if (EVP_EncryptFinal_ex(ctx.get(), cipher.data() + total, &outLen) != 1) throw std::runtime_error("encrypt final failed");
    total += outLen;
    cipher.resize(static_cast<std::size_t>(total));
    if (EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_GET_TAG, sizeof(tag), tag) != 1) throw std::runtime_error("encrypt tag failed");
    std::vector<unsigned char> out;
    out.insert(out.end(), magic, magic + sizeof(magic));
    out.insert(out.end(), nonce, nonce + sizeof(nonce));
    out.insert(out.end(), tag, tag + sizeof(tag));
    out.insert(out.end(), cipher.begin(), cipher.end());
    return out;
}

std::vector<unsigned char> unprotectData(const std::vector<unsigned char>& data, const EncryptionConfig& config) {
    if (!hasMagic(data)) return data;
    auto key = keyFromHex(config.keyHex);
    constexpr std::size_t nonceLen = 12;
    constexpr std::size_t tagLen = 16;
    const std::size_t headerLen = sizeof(magic) + nonceLen + tagLen;
    if (data.size() < headerLen) throw std::runtime_error("encrypted object is truncated");
    const unsigned char* nonce = data.data() + sizeof(magic);
    const unsigned char* tag = nonce + nonceLen;
    const unsigned char* cipher = tag + tagLen;
    const std::size_t cipherLen = data.size() - headerLen;
    using Ctx = std::unique_ptr<EVP_CIPHER_CTX, decltype(&EVP_CIPHER_CTX_free)>;
    Ctx ctx(EVP_CIPHER_CTX_new(), EVP_CIPHER_CTX_free);
    if (!ctx) throw std::runtime_error("EVP_CIPHER_CTX_new failed");
    if (EVP_DecryptInit_ex(ctx.get(), EVP_aes_256_gcm(), nullptr, nullptr, nullptr) != 1) throw std::runtime_error("decrypt init failed");
    if (EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_SET_IVLEN, nonceLen, nullptr) != 1) throw std::runtime_error("decrypt ivlen failed");
    if (EVP_DecryptInit_ex(ctx.get(), nullptr, nullptr, key.data(), nonce) != 1) throw std::runtime_error("decrypt key failed");
    std::vector<unsigned char> plain(cipherLen);
    int outLen = 0;
    int total = 0;
    if (cipherLen > 0 && EVP_DecryptUpdate(ctx.get(), plain.data(), &outLen, cipher, static_cast<int>(cipherLen)) != 1) throw std::runtime_error("decrypt update failed");
    total += outLen;
    if (EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_SET_TAG, tagLen, const_cast<unsigned char*>(tag)) != 1) throw std::runtime_error("decrypt tag failed");
    if (EVP_DecryptFinal_ex(ctx.get(), plain.data() + total, &outLen) != 1) throw std::runtime_error("decrypt final failed");
    total += outLen;
    plain.resize(static_cast<std::size_t>(total));
    return plain;
}

}
