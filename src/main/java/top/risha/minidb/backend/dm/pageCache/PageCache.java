package top.risha.minidb.backend.dm.pageCache;

import top.risha.minidb.backend.dm.page.Page;
import top.risha.minidb.backend.utils.Panic;
import top.risha.minidb.common.Error;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public interface PageCache {
    public static final int PAGE_SIZE = 1 << 13;

    static PageCache create(String path, long mem) {
        File f = new File(path + PageCacheImpl.DB_SUFFIX);
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
        return openFile(f, mem);
    }

    static PageCache open(String path, long mem) {
        File f = new File(path + PageCacheImpl.DB_SUFFIX);
        if(!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        if(!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }
        return openFile(f, mem);
    }

    private static PageCache openFile(File f, long mem) {
        RandomAccessFile raf = null;
        FileChannel fc = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }
        return new PageCacheImpl(raf, fc, (int)(mem / PAGE_SIZE));
    }

    int newPage(byte[] initData);
    Page getPage(int pgno) throws Exception;
    void close();
    void release(Page page);

    void truncateByBgno(int maxPgno);
    int getPageNumber();
    void flushPage(Page pg);

}
