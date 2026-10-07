comptime cpx<decl> make(type T, int N) {
    return {
        struct result_{T}_{N}_t { T value; };
        int make_{T}_{N}(T value) { return value + N; }
    };
}

make(int, 02);

int main() {
    result_int_2_t result;
    result.value = 40;
    return make_int_2(result.value) == 42 ? 0 : 1;
}
