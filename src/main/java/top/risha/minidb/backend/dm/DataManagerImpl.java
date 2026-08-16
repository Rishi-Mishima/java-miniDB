package top.risha.minidb.backend.dm;

import top.risha.minidb.backend.dm.dataItem.DataItemImpl;
import top.risha.minidb.backend.dm.logger.Logger;
import top.risha.minidb.backend.dm.page.Page;
import top.risha.minidb.backend.dm.page.PageX;
import top.risha.minidb.backend.dm.pageCache.PageCache;
import top.risha.minidb.backend.dm.pageIndex.PageIndex;
import top.risha.minidb.backend.tm.TransactionManager;
import top.risha.minidb.backend.utils.Panic;

public class DataManagerImpl {


    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pIndex;
    Page pageOne;

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super();
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        this.pIndex = new PageIndex();
    }

    public void logDataItem(long xid, DataItemImpl dataItem) {
    }

    public void releaseDataItem(DataItemImpl dataItem) {

    }

    // 初始化pageIndex
    void fillPageIndex() {
        int pageNumber = pc.getPageNumber();
        for(int i = 2; i <= pageNumber; i ++) {
            Page pg = null;
            try {
                // 根据页号 i 从 PageCache 获取 Page。
                pg = pc.getPage(i);
            } catch (Exception e) {
                Panic.panic(e);
            }
            pIndex.add(
                    // 获取页号。
                    pg.getPageNumber(),
                    // 计算这个 Page 还有多少空间。
                    PageX.getFreeSpace(pg));
            pg.release();
        }
    }
}
