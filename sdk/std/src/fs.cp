/// Portable filesystem façade; paths are UTF-8 strings with `/` separators.
/// The PAL translates this canonical representation to the target OS API.
import { isize, usize } from std.core;
import { int64_t, uint32_t, uint64_t } from c.stdint;

pub struct std_file_metadata_t {
    uint64_t size_bytes;
    int64_t modified_seconds_utc;
    uint32_t modified_nanoseconds;
    uint32_t kind;
    uint32_t reserved0;
    uint32_t reserved1;
};

pub int64_t std_fs_open(const char* path, uint64_t mode);
pub isize std_fs_read(int64_t handle, void* buffer, usize size);
pub isize std_fs_write(int64_t handle, const void* buffer, usize size);
pub int std_fs_close(int64_t handle);
pub int std_fs_rename(const char* source, const char* target);
pub int64_t std_fs_seek(int64_t handle, int64_t offset, uint32_t origin);
pub int std_fs_metadata(const char* path, std_file_metadata_t* metadata);
pub int std_fs_create_directory(const char* path);
pub int std_fs_remove_file(const char* path);
pub int std_fs_remove_directory(const char* path);
pub int64_t std_fs_directory_open(const char* path);
pub isize std_fs_directory_read(int64_t handle, char* utf8_name, usize capacity);
pub int std_fs_directory_close(int64_t handle);

pub uint64_t std_fs_mode_read();
pub uint64_t std_fs_mode_write();
pub uint64_t std_fs_mode_create();
pub uint64_t std_fs_mode_truncate();
pub int64_t std_fs_error_invalid_argument();
pub int64_t std_fs_error_buffer_too_small();
