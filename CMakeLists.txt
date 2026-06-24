cmake_minimum_required(VERSION 3.22)
project(keeply_agent_cpp VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(KEEPLY_BUILD_TESTS "Build tests" OFF)

find_package(zstd CONFIG REQUIRED)
find_package(unofficial-sqlite3 CONFIG QUIET)
if (NOT TARGET unofficial::sqlite3::sqlite3)
  find_package(SQLite3 REQUIRED)
endif()
find_package(OpenSSL REQUIRED)
find_package(CURL REQUIRED)
find_package(nlohmann_json CONFIG REQUIRED)

set(KEEPLY_ZSTD_TARGET "")
if (TARGET zstd::libzstd)
  set(KEEPLY_ZSTD_TARGET zstd::libzstd)
elseif (TARGET zstd::libzstd_shared)
  set(KEEPLY_ZSTD_TARGET zstd::libzstd_shared)
elseif (TARGET zstd::libzstd_static)
  set(KEEPLY_ZSTD_TARGET zstd::libzstd_static)
else()
  message(FATAL_ERROR "Could not find a usable zstd CMake target")
endif()

set(KEEPLY_SQLITE_TARGET "")
if (TARGET unofficial::sqlite3::sqlite3)
  set(KEEPLY_SQLITE_TARGET unofficial::sqlite3::sqlite3)
else()
  set(KEEPLY_SQLITE_TARGET SQLite::SQLite3)
endif()

set(KEEPLY_CORE_SOURCES
  src/core/BackupEngine.cpp
  src/core/RestoreEngine.cpp
  src/core/VerifyEngine.cpp
  src/core/PruneEngine.cpp
  src/core/AgentRunner.cpp
  src/core/Config.cpp
  src/core/LocalDb.cpp
  src/core/Compression.cpp
  src/core/Crypto.cpp
  src/core/Hash.cpp
  src/core/Util.cpp
  src/storage/FsObjectStore.cpp
  src/storage/S3ObjectStore.cpp
  src/storage/ObjectStoreFactory.cpp
  src/platform/windows/VssSnapshot.cpp
  src/platform/windows/UsnJournal.cpp
)

function(keeply_setup_target target)
  target_include_directories(${target} PRIVATE include)
  target_link_libraries(${target} PRIVATE
    ${KEEPLY_ZSTD_TARGET}
    ${KEEPLY_SQLITE_TARGET}
    OpenSSL::SSL
    OpenSSL::Crypto
    CURL::libcurl
    nlohmann_json::nlohmann_json
  )
  if (WIN32)
    target_compile_definitions(${target} PRIVATE NOMINMAX WIN32_LEAN_AND_MEAN)
    target_link_libraries(${target} PRIVATE ole32 uuid advapi32 shell32 comctl32)
  endif()
  if (MSVC)
    target_compile_options(${target} PRIVATE /W4 /permissive-)
  else()
    target_compile_options(${target} PRIVATE -Wall -Wextra -Wpedantic)
  endif()
endfunction()

add_executable(keeply-agent src/main.cpp src/core/InteractiveShell.cpp ${KEEPLY_CORE_SOURCES})
keeply_setup_target(keeply-agent)

add_executable(keeply-daemon src/daemon/DaemonMain.cpp ${KEEPLY_CORE_SOURCES})
keeply_setup_target(keeply-daemon)

install(TARGETS keeply-agent keeply-daemon RUNTIME DESTINATION bin)

find_package(Qt6 COMPONENTS Core Gui Widgets Svg QUIET)

if (Qt6_FOUND)
  set(CMAKE_AUTOMOC ON)
  set(CMAKE_AUTORCC ON)
  qt_add_executable(keeply-gui
    src/gui/main_gui.cpp
    src/gui/KeeplyMainWindow.cpp
    src/gui/KeeplyMainWindow.hpp
    src/gui/resources/icons.qrc
    ${KEEPLY_CORE_SOURCES}
  )
  keeply_setup_target(keeply-gui)
  target_include_directories(keeply-gui PRIVATE src/gui)
  target_link_libraries(keeply-gui PRIVATE Qt6::Core Qt6::Gui Qt6::Widgets Qt6::Svg)
  set_target_properties(keeply-gui PROPERTIES
    WIN32_EXECUTABLE ON
    MACOSX_BUNDLE ON
  )
  install(TARGETS keeply-gui RUNTIME DESTINATION bin)
else()
  message(STATUS "Qt6 not found — keeply-gui will not be built. Install qtbase via vcpkg.")
endif()
