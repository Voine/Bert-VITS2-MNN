# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

project("cppjieba")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(${CMAKE_PROJECT_NAME} SHARED
        cppjieba.cpp)

include_directories(
        cppjieba/deps/limonp/include
        cppjieba/include
)


target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log)