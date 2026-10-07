int isalpha(int value) { return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z'); }
int isdigit(int value) { return value >= '0' && value <= '9'; }
int isalnum(int value) { return isalpha(value) || isdigit(value); }
int isspace(int value) { return value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\f' || value == '\v'; }
int isupper(int value) { return value >= 'A' && value <= 'Z'; }
int islower(int value) { return value >= 'a' && value <= 'z'; }
int tolower(int value) { return isupper(value) ? value + ('a' - 'A') : value; }
int toupper(int value) { return islower(value) ? value - ('a' - 'A') : value; }
