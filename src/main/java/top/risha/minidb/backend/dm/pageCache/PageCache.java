package top.risha.minidb.backend.dm.pageCache;

import top.risha.minidb.backend.dm.page.Page;

public interface PageCache {
    public static final int PAGE_SIZE = 1 << 13;

    static PageCache create(String path, long mem) {
        return null;
    }

    static PageCache open(String path, long mem) {
        return null;
    }

    int newPage(byte[] initData);
    Page getPage(int pgno) throws Exception;
    void close();
    void release(Page page);

    void truncateByBgno(int maxPgno);
    int getPageNumber();
    void flushPage(Page pg);

}
