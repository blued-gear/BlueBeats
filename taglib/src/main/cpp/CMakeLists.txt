# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)

# list all header paths in the given dir and its subdirs
MACRO(HEADER_DIRECTORIES return_list dir)
    FILE(GLOB_RECURSE new_list "${dir}/*.h")
    SET(dir_list "")
    FOREACH(file_path ${new_list})
        GET_FILENAME_COMPONENT(dir_path ${file_path} PATH)
        SET(dir_list ${dir_list} ${dir_path})
    ENDFOREACH()
    LIST(REMOVE_DUPLICATES dir_list)
    SET(${return_list} ${dir_list})
ENDMACRO()

# Declares and names the project.

project("taglib")

# determines build-type (https://stackoverflow.com/a/50690288/8288367)
if (CMAKE_CURRENT_BINARY_DIR MATCHES "Debug")
    set(build_type "debug")
    #add_compile_options("-DANDROID_DEBUG")
else()
    set(build_type "release")
endif()

# include lib_taglib
set(libtaglib_proj "../../../lib_taglib")
get_filename_component(libtaglib_proj ${libtaglib_proj} ABSOLUTE)
set(libtaglib_src "${libtaglib_proj}/src/main/cpp/")
set(libtaglib_out "${libtaglib_proj}/build/intermediates/cmake/${build_type}/obj/${CMAKE_ANDROID_ARCH_ABI}")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             taglib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             taglib.cpp )

add_library(libtaglib SHARED IMPORTED)
set_target_properties(libtaglib PROPERTIES IMPORTED_LOCATION ${libtaglib_out}/libtag.so)
HEADER_DIRECTORIES(libtaglib_include ${libtaglib_src})
include_directories(${libtaglib_include})

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       taglib

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib}

                       # lib_taglib
                       libtaglib
)