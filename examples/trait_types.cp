pub struct Counter {
    int value;
};

pub typedef unsigned long long WideCount;

pub enum State {
    Ready = 1,
    Done = 2
};

pub union Word {
    int low;
    unsigned int bits;
};
