cmake_minimum_required(VERSION 3.22.1)

project("cpptokenizer")
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(${CMAKE_PROJECT_NAME} SHARED
        cpptokenizer.cpp)

add_library(tokenizers_rust_cpp STATIC IMPORTED)
set(TOKENIZER_RUST_CPP_LIB_DIR "${CMAKE_CURRENT_LIST_DIR}/../jniStaticLibs/${ANDROID_ABI}/libtokenizers_cpp.a")
set_target_properties(tokenizers_rust_cpp
        PROPERTIES
        IMPORTED_LOCATION
        ${TOKENIZER_RUST_CPP_LIB_DIR}
)

add_library(tokenizers_rust_c STATIC IMPORTED)
set(TOKENIZER_RUST_C_LIB_DIR "${CMAKE_CURRENT_LIST_DIR}/../jniStaticLibs/${ANDROID_ABI}/libtokenizers_c.a")
set_target_properties(tokenizers_rust_c
        PROPERTIES
        IMPORTED_LOCATION
        ${TOKENIZER_RUST_C_LIB_DIR}
)

set(tokenizer_path "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/tokenizers-cpp")
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE ${tokenizer_path}/include)
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        android
        log
        tokenizers_rust_cpp
        tokenizers_rust_c
)