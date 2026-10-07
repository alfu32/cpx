import { printf } from c.stdio;

int main() {
    defer printf("leaving main\n");
    printf("working\n");
    return 0;
}
