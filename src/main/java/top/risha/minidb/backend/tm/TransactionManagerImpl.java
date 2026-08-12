package top.risha.minidb.backend.tm;

import top.risha.minidb.backend.utils.Panic;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import top.risha.minidb.common.Error;

public class TransactionManagerImpl implements TransactionManager{
    // XID文件头长度
    static final int LEN_XID_HEADER_LENGTH = 8;
    // 每个事务的占用长度
    static final int XID_FIELD_SIZE = 1;

    // 事务的三种状态
    private static final byte FIELD_TRAN_ACTIVE   = 0;
    private static final byte FIELD_TRAN_COMMITTED = 1;
    private static final byte FIELD_TRAN_ABORTED  = 2;

    // 超级事务，永远为commited状态
    public static final long SUPER_XID = 0;
    // XID 文件后缀
    static final String XID_SUFFIX = ".xid";

    private RandomAccessFile file;
    private FileChannel fc;
    private long xidCounter;
    private Lock counterLock;

    TransactionManagerImpl(RandomAccessFile raf, FileChannel fc) {
        this.file = raf;
        this.fc = fc;
        counterLock = new ReentrantLock();
        checkXIDCounter();
    }

    /**
     * 检查XID文件是否合法
     * 读取XID_FILE_HEADER中的xidcounter，根据它计算文件的理论长度，对比实际长度
     * 如果检查发现文件损坏或长度不对，系统会直接挂起或抛出致命错误（Panic）
     */
    private void checkXIDCounter(){
        // 1. 检查文件的物理长度 - 获取硬盘上 XID 文件的真实物理字节数
        long fileLen = 0;

        try {
            fileLen = file.length();
        } catch (IOException e) {
            Panic.panic(Error.BadXIDFileException);
        }
        if(fileLen < LEN_XID_HEADER_LENGTH) {
            Panic.panic(Error.BadXIDFileException);
        }

        // 2. 从文件头读取 xidCounter - 利用 NIO 的 FileChannel 读取前 8 个字节，并将其解析为 long 类型的整数
        ByteBuffer buf = ByteBuffer.allocate(LEN_XID_HEADER_LENGTH);
        try {
            fc.position(0); // 将通道指针指向文件开头 fc.position(0)。
            fc.read(buf); // 将前 8 字节读入 buf
        } catch (IOException e) {
            Panic.panic(e);
        }
        // 通过 Parser.parseLong() 工具方法将字节数组还原为 long 值，保存在内存变量 this.xidCounter 中。这个值代表当前系统已经分配出去的最大 XID。
        this.xidCounter = Parser.parseLong(buf.array());

        // 3. 校验理论文件长度 vs 实际物理长度 - 精确定位并验证文件是否损坏或只写了一半。
        // getXidPosition(xid) 是一个计算偏移量的函数，用于算出“某个 XID 在文件中的起始字节位置”
        // 如果当前最大 XID 是 N，那么第 N + 1 个 XID 应该开始写入的位置，正好就是整个文件的末尾（EOF）。因此，getXidPosition(this.xidCounter + 1) 计算出的 end 就是文件的理论总长度。
        long end = getXidPosition(this.xidCounter + 1);
        // 如果 end 与真实文件大小 fileLen 不相等（例如上一次数据库崩溃导致只写了一半数据，或者文件被外部修改），则判定 XID 文件已损坏，触发 Panic 阻止数据库启动。
        if(end != fileLen) {
            Panic.panic(Error.BadXIDFileException);
        }
    }

    // 根据事务 xid 取得其在 xid 文件中对应的位置
    private long getXidPosition(long xid){
        return LEN_XID_HEADER_LENGTH + (xid-1)*XID_FIELD_SIZE;
    }

    @Override
    public long begin() {
        return 0;
    }

    @Override
    public void commit(long xid) {

    }

    @Override
    public void abort(long xid) {

    }

    @Override
    public boolean isActive(long xid) {
        return false;
    }

    @Override
    public boolean isCommitted(long xid) {
        return false;
    }

    @Override
    public boolean isAborted(long xid) {
        return false;
    }
}
