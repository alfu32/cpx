Program
  Function int main
    Block
      Variable result_int_2_t result
      Expression (result.value = 40)
      Return ((make_int_2(result.value) == 42) ? 0 : 1)
  Struct result_int_2_t
    Field int value
  Function int make_int_2
    Parameter int value
    Block
      Return (value + 2)
