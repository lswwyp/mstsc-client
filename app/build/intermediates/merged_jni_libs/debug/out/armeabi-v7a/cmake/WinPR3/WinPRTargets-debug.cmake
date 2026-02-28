#----------------------------------------------------------------
# Generated CMake target import file for configuration "Debug".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "winpr" for configuration "Debug"
set_property(TARGET winpr APPEND PROPERTY IMPORTED_CONFIGURATIONS DEBUG)
set_target_properties(winpr PROPERTIES
  IMPORTED_LINK_DEPENDENT_LIBRARIES_DEBUG "cjson"
  IMPORTED_LOCATION_DEBUG "${_IMPORT_PREFIX}/./libwinpr3.so"
  IMPORTED_SONAME_DEBUG "libwinpr3.so"
  )

list(APPEND _cmake_import_check_targets winpr )
list(APPEND _cmake_import_check_files_for_winpr "${_IMPORT_PREFIX}/./libwinpr3.so" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
