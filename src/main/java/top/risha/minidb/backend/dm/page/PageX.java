package top.risha.minidb.backend.dm.page;

import top.risha.minidb.backend.dm.pageCache.PageCache;
import top.risha.minidb.backend.utils.Parser;

import java.util.Arrays;

/**
 * PageX管理普通页
 */
public class PageX {

    private static final short OF_FREE = 0; // FSO 在页面中的起始偏移位置，常数 0
    private static final short OF_DATA = 2; // ：FSO 占用的字节长度，常数 2
    public static final int MAX_FREE_SPACE = PageCache.PAGE_SIZE - OF_DATA;

    public static byte[] initRaw() {
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        setFSO(raw, OF_DATA);
        return raw;
    }

    private static void setFSO(byte[] raw, short ofData) {
        System.arraycopy(Parser.short2Byte(ofData),0, raw, OF_FREE, OF_DATA);
    }

    // 获取 pg 的 FSO
    public static short getFSO(Page pg) {
        return getFSO(pg.getData());
    }

    // 标出当前数据页位置, 从偏移量之后写
    private static short getFSO(byte[] raw) {
        // Arrays.copyOfRange(raw, 0, 2) 从页面的字节数组中截取最开头的 2 个字节（索引 0 和 1）。
        // 将这 2 个 byte 传入 Parser.parseShort()，还原出一个 short 类型的整数。
        return Parser.parseShort(Arrays.copyOfRange(raw, 0, 2));
    }

    // 获取页面的空闲空间大小
    public static int getFreeSpace(Page pg) {
        // 用整页的总容量减去当前的 FSO
        return PageCache.PAGE_SIZE - (int)getFSO(pg.getData());
    }

    // 将raw插入pg中，返回插入位置
    public static short insert(Page pg, byte[] raw) {
        pg.setDirty(true);
        short offset = getFSO(pg.getData());
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);
        setFSO(pg.getData(), (short)(offset + raw.length));
        return offset;
    }

    public static void recoverUpdate(Page pg, byte[] raw, short offset) {

    }

    public static void recoverInsert(Page pg, byte[] raw, short offset) {
    }
}
