#!/usr/bin/env bash
set -euo pipefail
: "${VCPKG_ROOT:?set VCPKG_ROOT first}"
cmake -S . -B build -DCMAKE_TOOLCHAIN_FILE="$VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake"
cmake --build build -j"$(nproc)"
