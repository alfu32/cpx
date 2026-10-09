comptime cpx<decl> addMetadata(type T) {
    return {
        struct box_metadata_{T}_t {
            T value;
        };
    };
}

pub comptime cpx<decl> boxWithMetadata(type T) {
    return {
        addMetadata(T);
        struct box_with_metadata_{T}_t {
            int marker;
        };
    };
}
