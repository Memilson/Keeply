#pragma once

#include "keeply/Config.hpp"
#include "keeply/ObjectStore.hpp"

#include <memory>

namespace keeply {

std::unique_ptr<ObjectStore> makeObjectStore(const Config& config);

}
