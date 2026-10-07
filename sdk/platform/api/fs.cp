/// Uniform PAL filesystem ABI. Paths are canonical UTF-8 strings using `/`.
/// Mode bits and negative failures are defined by cplus_platform.h.
import { int64_t, uint64_t } from c.stdint;

pub int64_t platform_file_open(char* path, uint64_t mode);
pub int64_t platform_file_read(int64_t handle, void* buffer, uint64_t length);
pub int64_t platform_file_write(int64_t handle, void* buffer, uint64_t length);
pub int platform_file_close(int64_t handle);
pub int platform_file_rename(char* source, char* target);
