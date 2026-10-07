import {printf} from c.stdio;

struct point_t {
    int x;
    int y;

    int get(self) {
        return self.x;
    }

    int set_xy(self*, int x, int y) {
        self->x = x;
        self->y = y;
        return 0;
    }

    int default_value() {
        return 4;
    }
};

int main() {
    point_t point;
    point_t* point_ptr = &point;
    point_ptr.set_xy(3, 4);
    printf("Point x: %d, y: %d\n", point.x, point.y);
    return point.get() + point_t.default_value();
}
