/// Linux PAL uses the compiler intrinsic family; the registry selects the
/// architecture-specific lowering and keeps syscall numbers out of std code.
long linux_syscall0(long number);
long linux_syscall1(long number, long first);
long linux_syscall2(long number, long first, long second);
long linux_syscall3(long number, long first, long second, long third);
