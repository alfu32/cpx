#include <stddef.h>
#include <stdlib.h>

#if defined(_WIN32)

typedef unsigned long __cplus_dword;
typedef void* __cplus_handle;
typedef void (__stdcall *__cplus_fls_callback)(void* value);
typedef struct __cplus_init_once {
    void* pointer;
} __cplus_init_once;
typedef int (__stdcall *__cplus_init_callback)(
    __cplus_init_once* once,
    void* parameter,
    void** context);

typedef struct __cplus_emutls_control {
    size_t size;
    size_t alignment;
    union {
        size_t index;
        void* pointer;
    } location;
    void* initializer;
} __cplus_emutls_control;

typedef struct __cplus_emutls_state {
    size_t capacity;
    void* values[];
} __cplus_emutls_state;

__declspec(dllimport) __cplus_handle __stdcall GetProcessHeap(void);
__declspec(dllimport) void* __stdcall HeapAlloc(__cplus_handle heap, __cplus_dword flags, size_t bytes);
__declspec(dllimport) int __stdcall HeapFree(__cplus_handle heap, __cplus_dword flags, void* memory);
__declspec(dllimport) __cplus_dword __stdcall FlsAlloc(__cplus_fls_callback callback);
__declspec(dllimport) int __stdcall FlsFree(__cplus_dword index);
__declspec(dllimport) void* __stdcall FlsGetValue(__cplus_dword index);
__declspec(dllimport) int __stdcall FlsSetValue(__cplus_dword index, void* value);
__declspec(dllimport) int __stdcall InitOnceExecuteOnce(
    __cplus_init_once* once,
    __cplus_init_callback callback,
    void* parameter,
    void** context);

#define __CPLUS_HEAP_ZERO_MEMORY 0x00000008UL
#define __CPLUS_FLS_OUT_OF_INDEXES 0xffffffffUL

static __cplus_init_once __cplus_emutls_once;
static __cplus_dword __cplus_emutls_fls_index = __CPLUS_FLS_OUT_OF_INDEXES;
static size_t __cplus_emutls_next_index;

_Static_assert(sizeof(__cplus_emutls_control) == 4 * sizeof(void*), "GCC emutls control ABI");

static void __cplus_emutls_release_thread(void* value) {
    __cplus_emutls_state* state = (__cplus_emutls_state*)value;
    size_t index;
    __cplus_handle heap;
    if (!state) return;
    heap = GetProcessHeap();
    for (index = 1; index < state->capacity; index++) {
        void* allocation = state->values[index];
        if (allocation) HeapFree(heap, 0, ((void**)allocation)[-1]);
    }
    HeapFree(heap, 0, state);
}

static int __stdcall __cplus_emutls_initialize(
    __cplus_init_once* once,
    void* parameter,
    void** context) {
    (void)once;
    (void)parameter;
    (void)context;
    __cplus_emutls_fls_index = FlsAlloc(__cplus_emutls_release_thread);
    return __cplus_emutls_fls_index != __CPLUS_FLS_OUT_OF_INDEXES;
}

static void* __cplus_emutls_allocate(__cplus_emutls_control* control) {
    size_t alignment = control->alignment;
    size_t payload = control->size ? control->size : 1;
    size_t extra;
    size_t total;
    unsigned long long address;
    unsigned char* allocation;
    unsigned char* result;
    size_t index;
    __cplus_handle heap;

    if (alignment < sizeof(void*)) alignment = sizeof(void*);
    if ((alignment & (alignment - 1)) != 0) abort();
    extra = alignment - 1;
    if (payload > (size_t)-1 - sizeof(void*) || extra > (size_t)-1 - payload - sizeof(void*)) abort();
    total = payload + sizeof(void*) + extra;
    heap = GetProcessHeap();
    allocation = (unsigned char*)HeapAlloc(heap, 0, total);
    if (!allocation) abort();
    address = (unsigned long long)(allocation + sizeof(void*) + extra);
    address &= ~((unsigned long long)alignment - 1ULL);
    result = (unsigned char*)address;
    ((void**)result)[-1] = allocation;

    if (control->initializer) {
        const unsigned char* source = (const unsigned char*)control->initializer;
        for (index = 0; index < control->size; index++) result[index] = source[index];
    } else {
        for (index = 0; index < control->size; index++) result[index] = 0;
    }
    return result;
}

static __cplus_emutls_state* __cplus_emutls_state_for_thread(size_t index) {
    __cplus_emutls_state* state;
    size_t capacity;
    size_t bytes;
    size_t cursor;
    __cplus_handle heap;

    if (!InitOnceExecuteOnce(&__cplus_emutls_once, __cplus_emutls_initialize, (void*)0, (void**)0)) {
        abort();
    }
    state = (__cplus_emutls_state*)FlsGetValue(__cplus_emutls_fls_index);
    if (state && index < state->capacity) return state;

    capacity = state ? state->capacity : 16;
    while (capacity <= index) {
        if (capacity > (size_t)-1 / 2) abort();
        capacity *= 2;
    }
    if (capacity > ((size_t)-1 - sizeof(*state)) / sizeof(void*)) abort();
    bytes = sizeof(*state) + capacity * sizeof(void*);
    heap = GetProcessHeap();
    {
        __cplus_emutls_state* expanded = (__cplus_emutls_state*)HeapAlloc(
            heap, __CPLUS_HEAP_ZERO_MEMORY, bytes);
        if (!expanded) abort();
        expanded->capacity = capacity;
        if (state) {
            for (cursor = 0; cursor < state->capacity; cursor++) expanded->values[cursor] = state->values[cursor];
        }
        if (!FlsSetValue(__cplus_emutls_fls_index, expanded)) {
            HeapFree(heap, 0, expanded);
            abort();
        }
        if (state) HeapFree(heap, 0, state);
        return expanded;
    }
}

void* __emutls_get_address(void* opaque_control) {
    __cplus_emutls_control* control = (__cplus_emutls_control*)opaque_control;
    size_t index;
    __cplus_emutls_state* state;
    void* value;

    if (!control) abort();
    index = __atomic_load_n(&control->location.index, __ATOMIC_ACQUIRE);
    if (index == 0) {
        size_t assigned = __atomic_add_fetch(&__cplus_emutls_next_index, 1, __ATOMIC_RELAXED);
        size_t expected = 0;
        if (assigned == 0 || assigned > (size_t)-1 - 1) abort();
        if (!__atomic_compare_exchange_n(
                &control->location.index, &expected, assigned, 0, __ATOMIC_RELEASE, __ATOMIC_ACQUIRE)) {
            index = expected;
        } else {
            index = assigned;
        }
    }

    state = __cplus_emutls_state_for_thread(index);
    value = state->values[index];
    if (!value) {
        value = __cplus_emutls_allocate(control);
        state->values[index] = value;
    }
    return value;
}

void __emutls_register_common(void* opaque_control, size_t size, size_t alignment, void* initializer) {
    __cplus_emutls_control* control = (__cplus_emutls_control*)opaque_control;
    if (!control) abort();
    if (control->size < size) {
        control->size = size;
        control->initializer = (void*)0;
    }
    if (control->alignment < alignment) control->alignment = alignment;
    if (initializer && size == control->size) control->initializer = initializer;
}

#endif
