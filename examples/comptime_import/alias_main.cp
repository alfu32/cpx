import { box as makeBox } from "./box.cp";

makeBox(long);

int main() {
    struct box_long_t item;
    item.value = 42;
    return (int)item.value;
}
