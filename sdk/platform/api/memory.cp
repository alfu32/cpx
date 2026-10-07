/// Narrow PAL contract consumed by std.alloc.
void* platform_page_allocate(long page_count);
void platform_page_release(void* address, long page_count);
