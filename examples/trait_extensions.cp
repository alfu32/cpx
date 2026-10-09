import {Counter, WideCount, State, Word} from "./trait_types.cp";

pub comptime trait Counter {
    int read(self) { return self.value; }
    void increment(self*) { self->value += 1; }
}

comptime trait Counter {
    int debugValue(self) { return self.value; }
}

pub comptime trait WideCount {
    WideCount doubled(self) { return self * 2; }
}

pub comptime trait State {
    int code(self) { return self; }
}

pub comptime trait Word {
    int lowBits(self) { return self.low; }
}

int localOnly(Counter counter) {
    return counter.debugValue();
}
