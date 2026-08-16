package top.risha.minidb.backend.dm.pageIndex;

import top.risha.minidb.backend.dm.pageCache.PageCache;

import java.util.List;
import java.util.concurrent.locks.Lock;

public class PageIndex {
    // 将一页划成 40 个区间
    private static final int INTERVALS_NO = 40;
    private static final int THRESHOLD = PageCache.PAGE_SIZE / INTERVALS_NO;

    private List[] lists;

    private Lock lock;



    // 从 PageIndex 中获取页面，算出区间号，直接取即可 - 空闲空间页面选择器（Page Selector）。
    // 当需要向数据库插入一条新数据（长度为 spaceSize）时，快速在内存中挑选出一个“剩余空间足够”的数据页（Page）。
    public PageInfo select(int spaceSize) {
        lock.lock();
        try {
            int number = spaceSize / THRESHOLD;
            if(number < INTERVALS_NO) number ++;
            while(number <= INTERVALS_NO) {
                if(lists[number].size() == 0) {
                    number ++;
                    continue;
                }
                return (PageInfo) lists[number].remove(0);
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    public void add(int pgno, int freeSpace) {
        lock.lock();
        try {
            int number = freeSpace / THRESHOLD;
            lists[number].add(new PageInfo(pgno, freeSpace));
        } finally {
            lock.unlock();
        }
    }

}
