cmake_minimum_required(VERSION 3.0)
project(snapshot-server-core)

add_executable(core main.cpp)

list(APPEND core_link_libraries z mcfile)

if (NOT "${CMAKE_C_COMPILER_ID}" STREQUAL "MSVC")
  list(APPEND core_link_libraries pthread)
endif()

target_link_libraries(core ${core_link_libraries})
