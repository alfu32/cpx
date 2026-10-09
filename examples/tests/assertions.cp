int main() {
    return 0;
}

test arithmetic assertions {
    int expected = 42;

    assert(expected > 0)
    assert("the result is the expected value", expected == 42);
    assertEquals(40 + 2, expected)
    assertEquals("multiplication agrees", 6 * 7, expected);
}
