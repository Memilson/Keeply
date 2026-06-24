param(
  [string]$VcpkgRoot = "C:\vcpkg"
)

$ErrorActionPreference = "Stop"
cmake -S . -B build -DCMAKE_TOOLCHAIN_FILE="$VcpkgRoot\scripts\buildsystems\vcpkg.cmake"
cmake --build build --config Release
