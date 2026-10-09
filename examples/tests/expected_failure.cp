int main() {
    return 0;
}

// Intentionally failing example: `cplus test examples/tests/expected_failure.cp` exits 1.
test intentional assertion failure {
    assert("zero is false", 0);
}
