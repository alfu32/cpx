double fabs(double value) { return value < 0.0 ? -value : value; }

double sqrt(double value) {
    double estimate;
    int iteration;
    if (value < 0.0) return 0.0 / 0.0;
    if (value == 0.0) return 0.0;
    estimate = value > 1.0 ? value : 1.0;
    for (iteration = 0; iteration < 32; iteration++) estimate = (estimate + value / estimate) * 0.5;
    return estimate;
}
