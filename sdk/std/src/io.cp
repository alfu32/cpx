/// Unbuffered file streams layered over the portable std.fs façade.
/// Buffering and formatted conversion are separate higher-level facilities.
import { int64_t, uint64_t } from c.stdint;
import { isize, usize } from std.core;
import {
    std_fs_close,
    std_fs_error_invalid_argument,
    std_fs_mode_create,
    std_fs_mode_read,
    std_fs_mode_truncate,
    std_fs_mode_write,
    std_fs_open,
    std_fs_read,
    std_fs_seek,
    std_fs_write
} from std.fs;

pub struct std_file_stream_t {
    int64_t handle;
    uint64_t mode;
    int is_open;
};

pub std_file_stream_t std_file_stream_open(
    const char* path,
    int readable,
    int writable,
    int create,
    int truncate
) {
    uint64_t mode = 0;
    std_file_stream_t stream;
    if (readable) mode = mode | std_fs_mode_read();
    if (writable) mode = mode | std_fs_mode_write();
    if (create) mode = mode | std_fs_mode_create();
    if (truncate) mode = mode | std_fs_mode_truncate();
    stream.handle = std_fs_open(path, mode);
    stream.mode = mode;
    stream.is_open = stream.handle >= 0;
    return stream;
}

pub int std_file_stream_is_open(std_file_stream_t* stream) {
    return stream != (void*)0 && stream->is_open;
}

pub isize std_file_stream_read(std_file_stream_t* stream, void* buffer, usize size) {
    if (!std_file_stream_is_open(stream) || (stream->mode & std_fs_mode_read()) == 0) {
        return (isize)std_fs_error_invalid_argument();
    }
    return std_fs_read(stream->handle, buffer, size);
}

pub isize std_file_stream_write(std_file_stream_t* stream, const void* buffer, usize size) {
    if (!std_file_stream_is_open(stream) || (stream->mode & std_fs_mode_write()) == 0) {
        return (isize)std_fs_error_invalid_argument();
    }
    return std_fs_write(stream->handle, buffer, size);
}

pub int64_t std_file_stream_seek(std_file_stream_t* stream, int64_t offset, unsigned int origin) {
    if (!std_file_stream_is_open(stream)) return std_fs_error_invalid_argument();
    return std_fs_seek(stream->handle, offset, origin);
}

pub int std_file_stream_close(std_file_stream_t* stream) {
    if (!std_file_stream_is_open(stream)) return (int)std_fs_error_invalid_argument();
    int result = std_fs_close(stream->handle);
    if (result == 0) {
        stream->handle = -1;
        stream->mode = 0;
        stream->is_open = 0;
    }
    return result;
}
