/// Varargs and context operations are ABI contracts, not portable algorithms.
void* va_start_intrinsic(void* state, void* last);
void* va_arg_intrinsic(void* state, int type);
void va_end_intrinsic(void* state);
int context_save(void* context);
void context_restore(void* context, int value);
