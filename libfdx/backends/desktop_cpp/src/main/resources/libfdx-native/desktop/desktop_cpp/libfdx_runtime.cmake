find_package(Freetype QUIET)
if(TARGET Freetype::Freetype)
    target_link_libraries(jnative_classes PRIVATE Freetype::Freetype)
    target_link_libraries(jnative_app PRIVATE Freetype::Freetype)
else()
    include(FetchContent)
    set(FT_DISABLE_ZLIB ON CACHE BOOL "" FORCE)
    set(FT_DISABLE_BZIP2 ON CACHE BOOL "" FORCE)
    set(FT_DISABLE_PNG ON CACHE BOOL "" FORCE)
    set(FT_DISABLE_HARFBUZZ ON CACHE BOOL "" FORCE)
    set(FT_DISABLE_BROTLI ON CACHE BOOL "" FORCE)
    FetchContent_Declare(libfdx_freetype
        URL https://download.savannah.gnu.org/releases/freetype/freetype-2.14.3.tar.xz
        URL_HASH SHA256=36bc4f1cc413335368ee656c42afca65c5a3987e8768cc28cf11ba775e785a5f)
    FetchContent_MakeAvailable(libfdx_freetype)
    target_link_libraries(jnative_classes PRIVATE freetype)
    target_link_libraries(jnative_app PRIVATE freetype)
endif()

if(WIN32)
    target_link_libraries(jnative_app PRIVATE windowscodecs ole32)
    set(LIBFDX_RUNTIME_NAME fdx.dll)
    set(LIBFDX_AUDIO_NAME OpenAL.dll)
elseif(APPLE)
    set(LIBFDX_RUNTIME_NAME libfdx.dylib)
    set(LIBFDX_AUDIO_NAME libopenal.dylib)
else()
    target_link_libraries(jnative_app PRIVATE ${CMAKE_DL_LIBS})
    set(LIBFDX_RUNTIME_NAME libfdx.so)
    set(LIBFDX_AUDIO_NAME libopenal.so)
endif()
add_custom_command(TARGET jnative_app POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "${CMAKE_CURRENT_LIST_DIR}/${LIBFDX_RUNTIME_NAME}"
        "$<TARGET_FILE_DIR:jnative_app>/${LIBFDX_RUNTIME_NAME}"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "${CMAKE_CURRENT_LIST_DIR}/${LIBFDX_AUDIO_NAME}"
        "$<TARGET_FILE_DIR:jnative_app>/${LIBFDX_AUDIO_NAME}"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "${CMAKE_CURRENT_LIST_DIR}/OpenAL-Soft-COPYING.txt"
        "${CMAKE_CURRENT_LIST_DIR}/OpenAL-Soft-NOTICE.txt"
        "$<TARGET_FILE_DIR:jnative_app>")
