cmake_minimum_required(VERSION 3.22.1)
project("retrogba" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

# Flags de otimização de performance para emulação
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")

# Configurações para compilar apenas a libmgba estática para GBA sem dependências GUI/pesadas
set(LIBMGBA_ONLY ON CACHE BOOL "" FORCE)
set(BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC ON CACHE BOOL "" FORCE)
set(M_CORE_GBA ON CACHE BOOL "" FORCE)
set(M_CORE_GB OFF CACHE BOOL "" FORCE)
set(BUILD_QT OFF CACHE BOOL "" FORCE)
set(BUILD_SDL OFF CACHE BOOL "" FORCE)
set(BUILD_GL OFF CACHE BOOL "" FORCE)
set(BUILD_GLES2 OFF CACHE BOOL "" FORCE)
set(BUILD_GLES3 OFF CACHE BOOL "" FORCE)
set(BUILD_SUITE OFF CACHE BOOL "" FORCE)
set(BUILD_TEST OFF CACHE BOOL "" FORCE)
set(BUILD_PERF OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLE OFF CACHE BOOL "" FORCE)
set(USE_FFMPEG OFF CACHE BOOL "" FORCE)
set(USE_ZLIB OFF CACHE BOOL "" FORCE)
set(USE_MINIZIP OFF CACHE BOOL "" FORCE)
set(USE_PNG OFF CACHE BOOL "" FORCE)
set(USE_LIBZIP OFF CACHE BOOL "" FORCE)
set(USE_SQLITE3 OFF CACHE BOOL "" FORCE)
set(USE_ELF OFF CACHE BOOL "" FORCE)
set(USE_LZMA OFF CACHE BOOL "" FORCE)
set(USE_LUA OFF CACHE BOOL "" FORCE)
set(USE_JSON_C OFF CACHE BOOL "" FORCE)
set(USE_FREETYPE OFF CACHE BOOL "" FORCE)
set(ENABLE_DEBUGGERS OFF CACHE BOOL "" FORCE)
set(ENABLE_GDB_STUB OFF CACHE BOOL "" FORCE)
set(ENABLE_SCRIPTING OFF CACHE BOOL "" FORCE)
set(DISABLE_DEPS ON CACHE BOOL "" FORCE)
set(DISABLE_FRONTENDS ON CACHE BOOL "" FORCE)
set(SKIP_LIBRARY OFF CACHE BOOL "" FORCE)

add_subdirectory(mgba)

# Fontes do runtime oficial rcheevos (RetroAchievements em C)
set(RCHEEVOS_SRC
    rcheevos/src/runtime/rc_alloc.c
    rcheevos/src/runtime/rc_condition.c
    rcheevos/src/runtime/rc_condset.c
    rcheevos/src/runtime/rc_consoleinfo.c
    rcheevos/src/runtime/rc_format.c
    rcheevos/src/runtime/rc_lboard.c
    rcheevos/src/runtime/rc_memref.c
    rcheevos/src/runtime/rc_operand.c
    rcheevos/src/runtime/rc_richpresence.c
    rcheevos/src/runtime/rc_runtime.c
    rcheevos/src/runtime/rc_runtime_progress.c
    rcheevos/src/runtime/rc_trigger.c
    rcheevos/src/runtime/rc_typed_value.c
    rcheevos/src/runtime/rc_value.c
    rcheevos/src/util/md5.c
    rcheevos/src/util/rc_compat.c
    rcheevos/src/util/rc_util.c
    rcheevos/src/util/rc_version.c
)

add_library(rcheevos STATIC ${RCHEEVOS_SRC})
target_include_directories(rcheevos PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/rcheevos/include
)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/mgba/include
    ${CMAKE_CURRENT_BINARY_DIR}/mgba/include
    ${CMAKE_CURRENT_SOURCE_DIR}/rcheevos/include
)

add_library(retrogba SHARED
    retrogba_jni.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(retrogba
    mgba
    rcheevos
    ${log-lib}
    ${jnigraphics-lib}
)
