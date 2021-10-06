cmake_minimum_required(VERSION 3.0)
project(snapshot-server-core)

if (NOT CMAKE_CXX_STANDARD)
  set(CMAKE_CXX_STANDARD 20)
  set(CMAKE_CXX_STANDARD_REQUIRED ON)
  set(CMAKE_CXX_EXTENSIONS OFF)
endif()

add_executable(core
  main.cpp
  ../ext/libminecraft-file/include/minecraft-file.hpp)

target_include_directories(core PRIVATE ../ext/libminecraft-file/include)

list(APPEND core_link_libraries "z")

if (NOT "${CMAKE_C_COMPILER_ID}" STREQUAL "MSVC")
  list(APPEND core_link_libraries pthread)
endif()

target_link_libraries(core ${core_link_libraries})
