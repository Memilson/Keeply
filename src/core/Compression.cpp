#include "keeply/Compression.hpp"

#include <stdexcept>
#include <zstd.h>

namespace keeply {

std::vector<unsigned char> zstdCompress(const std::vector<unsigned char>& input, int level) {
    const auto bound = ZSTD_compressBound(input.size());
    std::vector<unsigned char> out(bound);
    const auto written = ZSTD_compress(out.data(), out.size(), input.data(), input.size(), level);
    if (ZSTD_isError(written)) {
        throw std::runtime_error(std::string("ZSTD_compress failed: ") + ZSTD_getErrorName(written));
    }
    out.resize(written);
    return out;
}

std::vector<unsigned char> zstdDecompress(const std::vector<unsigned char>& input) {
    unsigned long long const rSize = ZSTD_getFrameContentSize(input.data(), input.size());
    if (rSize == ZSTD_CONTENTSIZE_ERROR) throw std::runtime_error("not a zstd frame");
    if (rSize == ZSTD_CONTENTSIZE_UNKNOWN) throw std::runtime_error("zstd frame has unknown decompressed size");
    std::vector<unsigned char> out(static_cast<std::size_t>(rSize));
    const auto written = ZSTD_decompress(out.data(), out.size(), input.data(), input.size());
    if (ZSTD_isError(written)) {
        throw std::runtime_error(std::string("ZSTD_decompress failed: ") + ZSTD_getErrorName(written));
    }
    out.resize(written);
    return out;
}

}
