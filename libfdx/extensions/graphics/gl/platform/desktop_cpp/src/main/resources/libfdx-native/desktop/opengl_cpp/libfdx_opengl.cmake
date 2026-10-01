if(WIN32)
    include(FetchContent)
    FetchContent_Declare(libfdx_cpp_glew
        URL https://github.com/nigels-com/glew/releases/download/glew-2.3.0/glew-2.3.0.tgz
        URL_HASH SHA256=b261a06dfc8b970e0a1974488530e58dd2390acf68acb05b45235cd6fb17a086
        SOURCE_SUBDIR libfdx-source-only)
    FetchContent_MakeAvailable(libfdx_cpp_glew)
    add_library(libfdx_cpp_glew STATIC "${libfdx_cpp_glew_SOURCE_DIR}/src/glew.c")
    target_include_directories(libfdx_cpp_glew PUBLIC "${libfdx_cpp_glew_SOURCE_DIR}/include")
    target_compile_definitions(libfdx_cpp_glew PUBLIC GLEW_STATIC)
    target_link_libraries(libfdx_cpp_glew PUBLIC opengl32)
    target_link_libraries(jnative_classes PRIVATE libfdx_cpp_glew)
    target_link_libraries(jnative_app PRIVATE libfdx_cpp_glew)
else()
    find_package(GLEW REQUIRED)
    find_package(OpenGL REQUIRED)
    target_link_libraries(jnative_classes PRIVATE GLEW::GLEW OpenGL::GL)
    target_link_libraries(jnative_app PRIVATE GLEW::GLEW OpenGL::GL)
endif()
