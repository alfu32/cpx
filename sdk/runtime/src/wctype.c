int iswalpha(long value) { return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z'); }
int iswdigit(long value) { return value >= '0' && value <= '9'; }
int iswspace(long value) { return value == ' ' || value == '\t' || value == '\n' || value == '\r'; }
