/// Portable filesystem façade; paths are UTF-8 strings with `/` separators.
/// The PAL translates this canonical representation to the target OS API.
int std_fs_open(char* path, int mode);
int std_fs_close(int handle);
int std_fs_remove(char* path);
