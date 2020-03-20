cmake_minimum_required(VERSION 3.0)
project(snapshot-server-validate-preparation)

include(CheckCCompilerFlag)

include_directories(ext/libminecraft-file/include ext/hwm.task)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17")
add_executable(validate-preparation main.cpp
                                    ext/libminecraft-file/include/minecraft-file.hpp)
list(APPEND validate_preparation_link_libraries "z")

set(CMAKE_REQUIRED_FLAGS "-lstdc++fs")
check_c_compiler_flag("" TEST_RESULT_STDCXXFS)
if (TEST_RESULT_STDCXXFS AND "${CMAKE_C_COMPILER_ID}" STREQUAL "GNU")
  list(APPEND validate_preparation_link_libraries stdc++fs)
endif()

set(CMAKE_REQUIRED_FLAGS "-pthread")
check_c_compiler_flag("" TEST_RESULT_PTHREAD)
if (TEST_RESULT_PTHREAD)
  list(APPEND validate_preparation_link_libraries pthread)
endif()

target_link_libraries(validate-preparation ${validate_preparation_link_libraries})
