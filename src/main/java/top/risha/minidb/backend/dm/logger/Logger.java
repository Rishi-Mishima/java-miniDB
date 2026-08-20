package top.risha.minidb.backend.dm.logger;

import top.risha.minidb.backend.utils.Panic;
import top.risha.minidb.backend.utils.Parser;
import top.risha.minidb.common.Error;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public interface Logger {
    static Logger create(String path) {
        File f = new File(path + LoggerImpl.LOG_SUFFIX);
        try {
            if(!f.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e) {
            Panic.panic(e);
        }
        if(!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }
        RandomAccessFile raf = null;
        FileChannel fc = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
            fc.position(0);
            fc.write(ByteBuffer.wrap(Parser.int2Byte(0)));
            fc.force(false);
        } catch (Exception e) {
            Panic.panic(e);
        }
        return new LoggerImpl(raf, fc, 0);
    }

    static Logger open(String path) {
        File f = new File(path + LoggerImpl.LOG_SUFFIX);
        if(!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        if(!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }
        RandomAccessFile raf = null;
        FileChannel fc = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
            ByteBuffer buf = ByteBuffer.allocate(4);
            fc.position(0);
            fc.read(buf);
            int xChecksum = Parser.parseInt(buf.array());
            return new LoggerImpl(raf, fc, xChecksum);
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        } catch (Exception e) {
            Panic.panic(e);
        }
        return null;
    }

    void log(byte[] data);
    void truncate(long x) throws Exception;
    byte[] next();
    void rewind();
    void close();
}
