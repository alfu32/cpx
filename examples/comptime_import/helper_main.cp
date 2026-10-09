import { boxWithMetadata as makeBox } from "./helper_box.cp";

makeBox(int);

int main() {
    struct box_metadata_int_t value;
    value.value = 42;
    return value.value;
}
