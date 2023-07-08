cmake_minimum_required(VERSION 3.0)
project(snapshot-server-squash)

if (NOT CMAKE_CXX_STANDARD)
  set(CMAKE_CXX_STANDARD 20)
  set(CMAKE_CXX_STANDARD_REQUIRED ON)
  set(CMAKE_CXX_EXTENSIONS OFF)
endif()

add_executable(squash main.cpp)

list(APPEND squash_link_libraries z mcfile)

if (NOT "${CMAKE_C_COMPILER_ID}" STREQUAL "MSVC")
  list(APPEND squash_link_libraries pthread)
endif()

target_link_libraries(squash ${squash_link_libraries})
