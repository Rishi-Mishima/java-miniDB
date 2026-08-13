package top.risha.minidb.backend.dm.page;

import top.risha.minidb.backend.dm.pageCache.PageCache;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PageImpl implements Page {
    private int pageNumber; // pageNumber 是这个页面的页号，该页号从 1 开始
    private byte[] data; // data 就是这个页实际包含的字节数据
    private boolean dirty; // dirty 标志着这个页面是否是脏页面，在缓存驱逐的时候，脏页面需要被写回磁盘
    private Lock lock;

    private PageCache pc; // 来方便在拿到 Page 的引用时可以快速对这个页面的缓存进行释放操作


    public PageImpl(int pageNumber, byte[] data, PageCache pc) {
        this.pageNumber = pageNumber;
        this.data = data;
        this.pc = pc;
        lock = new ReentrantLock();
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public void release() {
        pc.release(this);
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return dirty;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public byte[] getData() {
        return data;
    }
}

