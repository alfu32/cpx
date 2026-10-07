/// C stdio façade; buffering and flushing belong to this layer.
void* stdin;
void* stdout;
void* stderr;
int fflush(void* stream);
int fgetc(void* stream);
int fputc(int value, void* stream);
