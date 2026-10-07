/// Portable filesystem façade; paths are UTF-8 byte strings.
int std_fs_open(char* path, int mode);
int std_fs_close(int handle);
int std_fs_remove(char* path);
