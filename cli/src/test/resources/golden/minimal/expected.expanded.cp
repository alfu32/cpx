Program
  Struct point_t
    Field int x
    Field int y
  Function int add
    Parameter int a
    Parameter int b
    Block
      Return (a + b)
  Function int main
    Block
      Return add(1, 2)
