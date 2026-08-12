package top.risha.minidb.backend.tm;

import top.risha.minidb.backend.utils.Panic;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import top.risha.minidb.common.Error;
import top.risha.minidb.backend.utils.Parser;

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

    // 开始一个事务，并返回XID
    // begin() = 加锁 → 生成新 XID → 记录事务 ACTIVE → 更新计数器 → 返回 XID → 解锁
    @Override
    public long begin() {
        counterLock.lock();
        try{
            // 先写数据，再更新元数据（metadata）
            long xid = xidCounter + 1;
            // 真正把事务注册到事务管理器里。
            updateXID(xid, FIELD_TRAN_ACTIVE);
            // 正式把全局事务计数器 +1。
            incrXIDCounter();
            return xid;
        }finally {
            // 必须释放锁
            counterLock.unlock();
        }

    }

    @Override
    public void commit(long xid) {

    }

    @Override
    public void abort (long xid) {
        updateXID(xid, FIELD_TRAN_ABORTED);
    }

    @Override
    public boolean isActive(long xid) {
        if(xid == SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_ACTIVE);
    }

    @Override
    // 用于判断指定的事务（xid）是否已经成功提交（Committed）。
    public boolean isCommitted(long xid) {
        if(xid == SUPER_XID) return true;
        return checkXID(xid, FIELD_TRAN_COMMITTED);
    }

    @Override
    public boolean isAborted(long xid) {
        if(xid == SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_ABORTED);
    }

    @Override
    public void close() {
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    // 更新 xid 事务的状态为 status
    private void updateXID(long xid, byte status) {
        // 1. 计算磁盘偏移量
        long offset = getXidPosition(xid);
        // 2. 准备字节缓冲区
        byte[] tmp = new byte[XID_FIELD_SIZE];
        // 构建一个大小为 XID_FIELD_SIZE（通常为 1 字节）的数组，装入新的状态值
        tmp[0] = status;
        // 使用 ByteBuffer.wrap() 将字节数组封装成 NIO 可读写的缓冲区。
        ByteBuffer buf = ByteBuffer.wrap(tmp);

        // 3. 定位并写入内存缓存
        try {
            // 将 FileChannel 的读写指针直接移动到之前算出的 offset 偏移处（利用随机读写特性）。
            fc.position(offset);
            // 将新状态写入文件。
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }

        // 4. 强制刷盘（关键点：持久化保障）
        try {
            // fc.force() 的作用：强制将 OS 内存缓存中的数据立即同步刷入物理磁盘
            // false: 只同步修改的数据，不同步元数据。
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    // 将 XID 加一，并更新 XID Header
    private void incrXIDCounter() {
        // 1. 内存计数器➕1, 获取当前刚刚分配出的最新事务
        xidCounter ++;
        // 2. 将 long 类型转为字节缓冲区
        // Parser.long2Byte(xidCounter)：把 64 位的 long 型数值转换成 8 个字节的 byte[] 数组。
        // ByteBuffer.wrap(...)：将该字节数组包装成 NIO 可直接处理的缓冲区，准备写入文件
        ByteBuffer buf = ByteBuffer.wrap(Parser.long2Byte(xidCounter));

        // 3. 重置指针并覆盖写入文件头
        try {
            // fc.position(0)：将 FileChannel 的游标移动到文件的绝对开头（offset = 0）。
            fc.position(0);
            // fc.write(buf)：将最新的 8 字节计数器数据覆盖写入文件头。
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }

        // 4. 强制刷盘（防崩溃安全保障）
        try {
            // fc.force(false)：强制将 OS 缓存中的这 8 字节立刻同步落盘（磁盘 fsync）。
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    // 检测XID事务是否处于Status的状态
    private boolean checkXID (long xid, byte status){
        // 1. 计算磁盘偏移量
        long offset = getXidPosition(xid);
        // 2. 准备接受读取数据的缓冲区
        ByteBuffer buf = ByteBuffer.wrap(new byte[XID_FIELD_SIZE]);
        // 3. 从文件精确读取 1 个字节
        try {
            // fc.position(offset)：把 FileChannel 的游标直接移到算出的 offset 位置。
            fc.position(offset);
            // fc.read(buf)：读取 1 个字节填充到 buf 中。如果遇到读取失败等 I/O 异常，直接抛出 Panic 终止程序。
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        // 4. 比对状态并返回结果 - buf.array()[0]：取出读到的那 1 个字节（状态码）
        return buf.array()[0] == status; // 相等返回true
    }
}
