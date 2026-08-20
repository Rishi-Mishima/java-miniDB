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
import top.risha.minidb.backend.dm.pageIndex.PageInfo;
import top.risha.minidb.backend.tm.TransactionManager;
import top.risha.minidb.backend.utils.Panic;
import top.risha.minidb.backend.utils.Types;
import top.risha.minidb.common.Error;

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
        byte[] log = Recover.updateLog(xid, dataItem);
        logger.log(log);
    }

    public void releaseDataItem(DataItemImpl dataItem) {
        super.release(dataItem.getUid());
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
        DataItemImpl di = (DataItemImpl)super.get(uid);
        if(!di.isValid()) {
            di.release();
            return null;
        }
        return di;
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        byte[] raw = DataItem.wrapDataItemRaw(data);
        if(raw.length > PageX.MAX_FREE_SPACE) {
            throw Error.DataTooLargeException;
        }

        // 尝试获取可用页
        PageInfo pi = null;
        for(int i = 0; i < 5; i ++) {
            pi = pIndex.select(raw.length);
            if (pi != null) {
                break;
            } else {
                int newPgno = pc.newPage(PageX.initRaw());
                pIndex.add(newPgno, PageX.MAX_FREE_SPACE);
            }
        }
        if(pi == null) {
            throw Error.DatabaseBusyException;
        }

        Page pg = null;
        int freeSpace = 0;
        try {
            pg = pc.getPage(pi.pgno);
            // 首先做日志
            byte[] log = Recover.insertLog(xid, pg, raw);
            logger.log(log);
            // 再执行插入操作
            short offset = PageX.insert(pg, raw);

            pg.release();
            return Types.addressToUid(pi.pgno, offset);

        } finally {
            // 将取出的 pg 重新插入 pIndex
            if(pg != null) {
                pIndex.add(pi.pgno, PageX.getFreeSpace(pg));
            } else {
                pIndex.add(pi.pgno, freeSpace);
            }
        }
    }

    @Override
    public void close() {
        super.close();
        logger.close();

        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
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
