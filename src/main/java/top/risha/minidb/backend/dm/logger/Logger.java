package top.risha.minidb.backend.dm.logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public interface Logger {
    void log(byte[] data);
    void truncate(long x) throws Exception;
    byte[] next();
    void rewind();
    void close();
}
