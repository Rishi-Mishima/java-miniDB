package top.risha.minidb.backend.dm.logger;

import com.google.common.primitives.Bytes;
import top.risha.minidb.backend.utils.Panic;
import top.risha.minidb.backend.utils.Parser;
import top.risha.minidb.common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 日志文件读写
 *
 * 日志文件标准格式为：
 * [XChecksum] [Log1] [Log2] ... [LogN] [BadTail]
 * XChecksum 为后续所有日志计算的Checksum，int类型
 *
 * 每条正确日志的格式为：
 * [Size] [Checksum] [Data]
 * Size 4字节int 标识Data长度
 * Checksum 4字节int
 */

public class LoggerImpl implements Logger{
    private static final int SEED = 13331;

    private static final int OF_SIZE = 0;
    private static final int OF_CHECKSUM = OF_SIZE + 4;
    private static final int OF_DATA = OF_CHECKSUM + 4;

    public static final String LOG_SUFFIX = ".log";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock lock;

    private long position;  // 当前日志指针的位置
    private long fileSize;  // 初始化时记录，log操作不更新
    private int xChecksum;

    public LoggerImpl(RandomAccessFile file, FileChannel fc, int xChecksum) {
        this.file = file;
        this.fc = fc;
        this.xChecksum = xChecksum;
        this.lock = new ReentrantLock();
        try {
            this.fileSize = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }
        try {
            checkAndRemoveTail();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    private int calChecksum(int xCheck, final byte[] log) {
        // 1. 空指针防护：若传入为空数组，直接返回初始校验码
        if (log == null) {
            return xCheck;
        }

        // 2. 遍历计算
        for (final byte b : log) {
            // (b & 0xFF) 将有符号的 byte (-128~127) 转为无符号的 int (0~255)
            // 避免了高位符号扩展填充 1 导致的计算异常
            xCheck = xCheck * SEED + (b & 0xFF);
        }

        return xCheck;
    }

    // 对比是否是合规的logger
    private byte[] internNext() throws IOException {
        if(position + OF_DATA >= fileSize) {
            return null;
        }
        // 读取 size
        ByteBuffer tmp = ByteBuffer.allocate(4);
        fc.position(position);
        fc.read(tmp);
        int size = Parser.parseInt(tmp.array());
        if(position + size + OF_DATA > fileSize) {
            return null;
        }

        // 读取 checksum+data
        ByteBuffer buf = ByteBuffer.allocate(OF_DATA + size);
        fc.position(position);
        fc.read(buf);
        byte[] log = buf.array();

        // 校验 checksum
        int checkSum1 = calChecksum(0, Arrays.copyOfRange(log, OF_DATA, log.length));
        int checkSum2 = Parser.parseInt(Arrays.copyOfRange(log, OF_CHECKSUM, OF_DATA));
        if(checkSum1 != checkSum2) {
            return null;
        }
        position += log.length;
        return log;
    }

    // 检查并移除bad Tail
    private void checkAndRemoveTail() throws IOException {
        // 1. 重制指针: 将文件读取游标 position 重新归零，准备从头开始扫描整个文件
        rewind();

        // 2. check
        int xCheck = 0;
        while(true){
            byte[] log = internNext();
            if(log == null){
                break;
            }
            xCheck = calChecksum(xCheck, log);
        }

        // 3. 整文件校验判定 (xCheck != xChecksum)
        if(xCheck != xChecksum){
            Panic.panic(Error.BadLogFileException);
        }

        // 4. 物理裁剪 (truncate(position))
        //直接把磁盘文件在 position 处截断。position 之后所有的残留字节（比如写到一半突然断电留下的废数据）全部被丢弃抹去。
        try {
            truncate(position);
        } catch (Exception e) {
            Panic.panic(e);
        }
        try {
            file.seek(position);
        } catch (IOException e) {
            Panic.panic(e);
        }
        rewind();
    }

    // wrapLog 方法负责将原始业务数据 data 打包（Wrap）成标准二进制格式，为数据加上“长度标识”和“防伪/防损坏校验码”，以便写入磁盘。
    private byte[] wrapLog(byte[] data) {
        byte[] checksum = Parser.int2Byte(calChecksum(0, data));
        byte[] size = Parser.int2Byte(data.length);
        return Bytes.concat(size, checksum, data);
    }

    // 并发追加写入
    // 负责线程安全地把打包好的日志追加（Append）到文件末尾
    @Override
    public void log(byte[] data) {
        byte[] log = wrapLog(data);
        ByteBuffer buf = ByteBuffer.wrap(log);
        lock.lock();
        try {
            fc.position(fc.size());
            fc.write(buf);
            updateXChecksum(log); // 必须在锁的内部执行！
            fileSize += log.length;
        } catch(IOException e) {
            Panic.panic(e);
        } finally {
            lock.unlock();
        }
    }

    // 更新整个日志文件的全局校验和（xChecksum），并强行将其刷入物理磁盘，以确保文件开头的元数据与磁盘中的日志记录保持同步
    private void updateXChecksum(byte[] log) throws IOException {
        this.xChecksum = calChecksum(this.xChecksum, log);
        fc.position(0);
        fc.write(ByteBuffer.wrap(Parser.int2Byte(xChecksum)));
        fc.force(false);
    }


    @Override
    public void truncate(long x) throws Exception {
        lock.lock();
        try {
            fc.truncate(x);
            fileSize = x;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public byte[] next() {
        lock.lock();
        try {
            byte[] log = internNext();
            if(log == null) {
                return null;
            }
            return Arrays.copyOfRange(log, OF_DATA, log.length);
        } catch(IOException e) {
            Panic.panic(e);
        } finally {
            lock.unlock();
        }
        return null;
    }

    @Override
    public void rewind() {
        position = 4;
    }

    @Override
    public void close() {
        try {
            fc.close();
            file.close();
        } catch(IOException e) {
            Panic.panic(e);
        }
    }
}
