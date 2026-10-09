import { box } from "./box.cp";

box(int);

int main() {
    struct box_int_t item;
    item.value = 42;
    return item.value;
}
