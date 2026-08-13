package top.risha.minidb.backend.dm.page;

import top.risha.minidb.backend.dm.pageCache.PageCache;
import top.risha.minidb.backend.utils.RandomUtil;

import java.util.Arrays;

/**
 * 特殊管理第一页
 * 用于判断上一次数据库是否正常关闭
 */
public class PageOne {
    private static final int OF_VC = 100;
    private static final int LEN_VC = 8;

    public static byte[] InitRaw() {
        // 1. 在内存中分配一个标准页大小（如 16KB）的空字节数组
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        // 2. 写入随机校验码 VC1，将状态标记为“打开/运行中”
        setVcOpen(raw);
        // 3. 返回这组准备好的字节，作为 Page 1 的初始数据
        return raw;
    }

    public static void setVcOpen(Page pg) {
        pg.setDirty(true);
        setVcOpen(pg.getData());
    }

    private static void setVcOpen(byte[] raw) {
        System.arraycopy(RandomUtil.randomBytes(LEN_VC), 0, raw, OF_VC, LEN_VC);
    }

    public static void setVcClose(Page pg) {
        pg.setDirty(true);
        setVcClose(pg.getData());
    }

    private static void setVcClose(byte[] raw) {
        System.arraycopy(raw, OF_VC, raw, OF_VC+LEN_VC, LEN_VC);
    }

    public static boolean checkVc(Page pg) {
        return checkVc(pg.getData());
    }

    private static boolean checkVc(byte[] raw) {
        return Arrays.equals(Arrays.copyOfRange(raw, OF_VC, OF_VC+LEN_VC), Arrays.copyOfRange(raw, OF_VC+LEN_VC, OF_VC+2*LEN_VC));
    }
}
