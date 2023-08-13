cmake_minimum_required(VERSION 3.0)
project(snapshot-server)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_subdirectory(ext/libminecraft-file)
add_subdirectory(core)
add_subdirectory(squash)
