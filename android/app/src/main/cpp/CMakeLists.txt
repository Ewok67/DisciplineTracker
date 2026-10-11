cmake_minimum_required(VERSION 3.22)
project(ai_agent)

# Vendored at build time and pinned to a known-good commit, so the repo stays
# small and the engine version is reproducible.
include(FetchContent)
FetchContent_Declare(
        llama
        GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
        GIT_TAG f2cc7282ce0042641df55e0e563b75e824e2b647
        GIT_SHALLOW TRUE
)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(llama)

add_library(ai_agent SHARED ai_agent.cpp)
target_link_libraries(ai_agent PRIVATE llama android log)
