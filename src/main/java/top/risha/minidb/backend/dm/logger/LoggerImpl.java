package top.risha.minidb.backend.dm.logger;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;

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

public class LoggerImpl {
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
}
