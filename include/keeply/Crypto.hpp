#pragma once

#include "keeply/Config.hpp"

#include <string>
#include <vector>

namespace keeply {

std::string generateEncryptionKeyHex();
std::vector<unsigned char> protectData(const std::vector<unsigned char>& data, const EncryptionConfig& config);
std::vector<unsigned char> unprotectData(const std::vector<unsigned char>& data, const EncryptionConfig& config);

}
