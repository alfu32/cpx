/// Optional, explicitly imported fixed-width integer aliases for application code.
package std;

import {
    int8_t, uint8_t,
    int16_t, uint16_t,
    int32_t, uint32_t,
    int64_t, uint64_t
} from c.stdint;

pub typedef int8_t i8;
pub typedef int16_t i16;
pub typedef int32_t i32;
pub typedef int64_t i64;

pub typedef uint8_t u8;
pub typedef uint16_t u16;
pub typedef uint32_t u32;
pub typedef uint64_t u64;
