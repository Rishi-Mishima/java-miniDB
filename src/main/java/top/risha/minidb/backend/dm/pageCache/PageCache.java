package top.risha.minidb.backend.dm.pageCache;

import top.risha.minidb.backend.dm.page.Page;

public interface PageCache {
    int newPage(byte[] initData);
    Page getPage(int pgno) throws Exception;
    void close();
    void release(Page page);

    void truncateByBgno(int maxPgno);
    int getPageNumber();
    void flushPage(Page pg);
}
