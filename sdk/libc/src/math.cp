double fabs(double value) { return value < 0.0 ? -value : value; }

double sqrt(double value) {
    double estimate = value > 1.0 ? value : 1.0;
    int iteration = 0;
    if (value < 0.0) return 0.0 / 0.0;
    if (value == 0.0) return 0.0;
    while (iteration < 32) {
        estimate = (estimate + value / estimate) * 0.5;
        iteration = iteration + 1;
    }
    return estimate;
}
