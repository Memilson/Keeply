#include "keeply/ObjectStoreFactory.hpp"
#include "keeply/FsObjectStore.hpp"
#include "keeply/S3ObjectStore.hpp"

#include <stdexcept>

namespace keeply {

std::unique_ptr<ObjectStore> makeObjectStore(const Config& config) {
    if (config.repository.type == "local") {
        return std::make_unique<FsObjectStore>(config.repository.path);
    }
    if (config.repository.type == "s3") {
        return std::make_unique<S3ObjectStore>(config.repository);
    }
    throw std::runtime_error("unsupported repository type: " + config.repository.type);
}

}
