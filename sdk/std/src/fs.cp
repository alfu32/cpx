/// Portable filesystem façade; paths are UTF-8 strings with `/` separators.
/// The PAL translates this canonical representation to the target OS API.
long long std_fs_open(char* path, long long mode);
long long std_fs_read(long long handle, void* buffer, long long size);
long long std_fs_write(long long handle, void* buffer, long long size);
int std_fs_close(long long handle);
int std_fs_rename(char* source, char* target);
