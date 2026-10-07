comptime cpx<decl> optional(type T) {
    return {
        struct optional_{T}_t {
            bool valid;
            T value;
        };
    };
}

optional(int);

int main() {
    optional_int_t value;
    value.valid = 1;
    value.value = 9;
    return value.value;
}
