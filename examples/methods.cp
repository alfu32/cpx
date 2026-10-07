struct point_t {
    int x;

    int get(self) {
        return self.x;
    }

    int default_value() {
        return 4;
    }
};

int main() {
    point_t point;
    point.x = 3;
    return point.get() + point_t.default_value();
}
