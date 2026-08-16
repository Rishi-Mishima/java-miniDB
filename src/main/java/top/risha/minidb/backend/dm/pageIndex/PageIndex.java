package top.risha.minidb.backend.dm.pageIndex;

import top.risha.minidb.backend.dm.pageCache.PageCache;

import java.util.List;

public class PageIndex {
    // 将一页划成 40 个区间
    private static final int INTERVALS_NO = 40;
    private static final int THRESHOLD = PageCache.PAGE_SIZE / INTERVALS_NO;

    private List[] lists;
}
