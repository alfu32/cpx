/// Version-3 uniform PAL filesystem ABI. Paths are canonical UTF-8 strings using `/`.
/// Constants and stable negative failures are defined by cplus_platform.h.
import { int64_t, uint32_t, uint64_t } from c.stdint;

pub struct cplus_file_metadata_t {
    uint64_t size_bytes;
    int64_t modified_seconds_utc;
    uint32_t modified_nanoseconds;
    uint32_t kind;
    uint32_t reserved0;
    uint32_t reserved1;
};

pub int64_t platform_file_open(const char* path, uint64_t mode);
pub int64_t platform_file_read(int64_t handle, void* buffer, uint64_t length);
pub int64_t platform_file_write(int64_t handle, const void* buffer, uint64_t length);
pub int platform_file_close(int64_t handle);
pub int platform_file_rename(const char* source, const char* target);
pub int64_t platform_file_seek(int64_t handle, int64_t offset, uint32_t origin);
pub int platform_file_metadata(const char* path, cplus_file_metadata_t* metadata);
pub int platform_directory_create(const char* path);
pub int platform_file_remove(const char* path);
pub int platform_directory_remove(const char* path);
pub int64_t platform_directory_open(const char* path);
pub int64_t platform_directory_read(int64_t handle, char* utf8_name, uint64_t capacity);
pub int platform_directory_close(int64_t handle);
