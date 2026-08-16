package top.risha.minidb.backend.dm;

import top.risha.minidb.backend.common.AbstractCache;
import top.risha.minidb.backend.dm.dataItem.DataItem;
import top.risha.minidb.backend.dm.dataItem.DataItemImpl;
import top.risha.minidb.backend.dm.logger.Logger;
import top.risha.minidb.backend.dm.page.Page;
import top.risha.minidb.backend.dm.page.PageOne;
import top.risha.minidb.backend.dm.page.PageX;
import top.risha.minidb.backend.dm.pageCache.PageCache;
import top.risha.minidb.backend.dm.pageIndex.PageIndex;
import top.risha.minidb.backend.tm.TransactionManager;
import top.risha.minidb.backend.utils.Panic;

public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager{


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

    @Override
    protected DataItem getForCache(long uid) throws Exception {
        short offset = (short)(uid & ((1L << 16) - 1));
        uid >>>= 32;
        int pgno = (int)(uid & ((1L << 32) - 1));
        Page pg = pc.getPage(pgno);
        return DataItem.parseDataItem(pg, offset, this);
    }

    @Override
    protected void releaseForCache(DataItem di) {
        di.page().release();
    }

    @Override
    public DataItem read(long uid) throws Exception {
        return null;
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        return 0;
    }

    @Override
    public void close() {

    }

    // 在创建文件时初始化PageOne
    void initPageOne() {
        int pgno = pc.newPage(PageOne.InitRaw());
        assert pgno == 1;
        try {
            pageOne = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    // 在打开已有文件时时读入PageOne，并验证正确性
    boolean loadCheckPageOne() {
        try {
            pageOne = pc.getPage(1);
        } catch (Exception e) {
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }
}
