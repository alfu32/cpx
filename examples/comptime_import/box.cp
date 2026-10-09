/// Generate a struct that stores one value of the requested type.
pub comptime cpx<decl> box(type T) {
    return {
        struct box_{T}_t {
            T value;
        };
    };
}
