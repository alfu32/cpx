struct point_t {
    int x;
    int y;
};

int add(int a, int b);
int main();

int add(int a, int b) {
    return (a + b);
}

int main() {
    return add(1, 2);
}
