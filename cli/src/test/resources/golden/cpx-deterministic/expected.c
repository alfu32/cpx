struct result_int_2_t {
    int value;
};

int main();
int make_int_2(int value);

int main() {
    struct result_int_2_t result;
    (result.value = 40);
    return ((make_int_2(result.value) == 42) ? 0 : 1);
}

int make_int_2(int value) {
    return (value + 2);
}
