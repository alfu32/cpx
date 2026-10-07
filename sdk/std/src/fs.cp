/// Portable filesystem façade; paths are UTF-8 strings with `/` separators.
/// The PAL translates this canonical representation to the target OS API.
import { int64_t, uint64_t } from c.stdint;

int64_t std_fs_open(char* path, uint64_t mode);
int64_t std_fs_read(int64_t handle, void* buffer, uint64_t size);
int64_t std_fs_write(int64_t handle, void* buffer, uint64_t size);
int std_fs_close(int64_t handle);
int std_fs_rename(char* source, char* target);
