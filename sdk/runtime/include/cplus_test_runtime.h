#ifndef CPLUS_TEST_RUNTIME_H
#define CPLUS_TEST_RUNTIME_H

typedef enum {
    CPLUS_TEST_VALUE_UNKNOWN = 0,
    CPLUS_TEST_VALUE_SIGNED_INTEGER = 1,
    CPLUS_TEST_VALUE_UNSIGNED_INTEGER = 2,
    CPLUS_TEST_VALUE_PLAIN_INTEGER = 3,
    CPLUS_TEST_VALUE_BOOLEAN = 4,
    CPLUS_TEST_VALUE_FLOAT = 5,
    CPLUS_TEST_VALUE_DOUBLE = 6,
    CPLUS_TEST_VALUE_LONG_DOUBLE = 7,
    CPLUS_TEST_VALUE_COMPLEX_FLOAT = 8,
    CPLUS_TEST_VALUE_COMPLEX_DOUBLE = 9,
    CPLUS_TEST_VALUE_COMPLEX_LONG_DOUBLE = 10,
    CPLUS_TEST_VALUE_POINTER = 11
} cplus_test_value_kind_t;

int __cplus_test_begin(const char* fixture_identity, const char* result_path);
int __cplus_test_finish(void);
int __cplus_test_dispatch_match(const char* actual, const char* expected);
void __cplus_test_report_truth(const char* description, const char* expression,
                               const void* value, const char* type_name,
                               unsigned long long value_size, int value_kind,
                               int is_null_pointer, int passed);
void __cplus_test_report_equality(const char* description,
                                  const char* expected_expression,
                                  const char* actual_expression,
                                  const void* expected_value,
                                  const char* expected_type,
                                  unsigned long long expected_size,
                                  int expected_kind,
                                  int expected_is_null_pointer,
                                  const void* actual_value,
                                  const char* actual_type,
                                  unsigned long long actual_size,
                                  int actual_kind,
                                  int actual_is_null_pointer,
                                  int passed);

#endif
