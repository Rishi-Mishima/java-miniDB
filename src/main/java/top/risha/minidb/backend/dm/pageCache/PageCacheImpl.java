package top.risha.minidb.backend.dm.pageCache;

import top.risha.minidb.backend.common.AbstractCache;
import org.checkerframework.checker.nullness.qual.Nullable;
import top.risha.minidb.backend.dm.page.Page;
import top.risha.minidb.backend.dm.page.PageImpl;
import top.risha.minidb.backend.utils.Panic;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

public class PageCacheImpl extends AbstractCache<Page> implements PageCache {
    private static final int MEM_MIN_LIM = 10;
    public static final String DB_SUFFIX = ".db";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock fileLock;

    private AtomicInteger pageNumbers;



    @Override
    public int newPage(byte[] initData) {
        int pgno = pageNumbers.incrementAndGet();
        Page pg = new PageImpl(pgno, initData, null);
        flush(pg);
        return pgno;
    }

    @Override
    public Page getPage(int pgno) throws Exception {
        return null;
    }

    @Override
    public void close() {

    }

    @Override
    public void release(Page page) {

    }

    @Override
    public void truncateByBgno(int maxPgno) {

    }

    @Override
    public int getPageNumber() {
        return 0;
    }

    @Override
    public void flushPage(Page pg) {

    }

    /**
     * 根据pageNumber从数据库文件中读取页数据，并包裹成Page
     */
    @Override
    protected Page getForCache(long key) throws Exception {
        int pgno = (int)key;
        long offset = PageCacheImpl.pageOffset(pgno);

        ByteBuffer buf = ByteBuffer.allocate(PAGE_SIZE);
        fileLock.lock();
        try {
            fc.position(offset);
            fc.read(buf);
        } catch(IOException e) {
            Panic.panic(e);
        }
        fileLock.unlock();
        return new PageImpl(pgno, buf.array(), this);
    }

    @Override
    protected void releaseForCache(Page pg) {
        if(pg.isDirty()) {
            flush(pg);
            pg.setDirty(false);
        }
    }

    private static long pageOffset(int pgno) {
        return (pgno-1) * PAGE_SIZE;
    }

    private void flush(Page pg){
        int pgno = pg.getPageNumber();
        long offset = pageOffset(pgno);

        fileLock.lock(); // 加文件锁
        try {
            // 把内存中 Page 对象里最新修改过的字节数组，包装成 NIO 写入需要的 ByteBuffer
            ByteBuffer buf = ByteBuffer.wrap(pg.getData());
            fc.position(offset); // 精准定位
            fc.write(buf); // 把内存里最新的数据覆盖写入到硬盘文件
            fc.force(false); // 强制操作系统立刻把内核缓冲区的数据冲刷到物理硬盘硬件
        } catch(IOException e) {
            Panic.panic(e);
        } finally {
            fileLock.unlock();
        }
    }
}
