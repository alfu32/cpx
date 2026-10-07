static char __cplus_c_locale[] = "C";

char* setlocale(int category, const char* locale) {
    (void)category;
    if (locale == (const char*)0 || locale[0] == 0) return __cplus_c_locale;
    return locale[0] == 'C' && locale[1] == 0 ? __cplus_c_locale : (char*)0;
}
