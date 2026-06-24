#pragma once

#include <vector>

namespace keeply {

std::vector<unsigned char> zstdCompress(const std::vector<unsigned char>& input, int level);
std::vector<unsigned char> zstdDecompress(const std::vector<unsigned char>& input);

}
