/// Narrow PAL contract consumed by std.alloc. Page counts use explicit-width
/// values so the C ABI is stable under LP64 and LLP64 targets.
import { uint64_t } from c.stdint;

void* platform_page_allocate(uint64_t page_count);
int platform_page_release(void* address, uint64_t page_count);
