import {puts} from c.stdio;
import {Counter as LocalCounter, WideCount, State, Word} from "./trait_types.cp";
import {read, increment, doubled, code, lowBits} from "./trait_extensions.cp";

int main() {
    LocalCounter counter;
    counter.value = 4;
    counter.increment();

    WideCount count = 3;
    State state = Ready;
    Word word;
    word.low = 2;

    int total = counter.read() + count.doubled() + state.code() + word.lowBits();
    if (total != 14) return 1;
    puts("compile-time trait example passed");
    return 0;
}
