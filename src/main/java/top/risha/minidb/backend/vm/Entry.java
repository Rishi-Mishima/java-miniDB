package top.risha.minidb.backend.vm;

import com.google.common.primitives.Bytes;
import top.risha.minidb.backend.common.SubArray;
import top.risha.minidb.backend.dm.dataItem.DataItem;
import top.risha.minidb.backend.utils.Parser;

public class Entry {
    private static final int OF_XMIN = 0;
    private static final int OF_XMAX = OF_XMIN+8;
    private static final int OF_DATA = OF_XMAX+8;

    private long uid;
    private DataItem dataItem;
    private VersionManager vm;

    public static Entry loadEntry(VersionManager vm, long uid) throws Exception {
        DataItem di = ((VersionManagerImpl)vm).dm.read(uid);
        return newEntry(vm, di, uid);
    }

    private static Entry newEntry(VersionManager vm, DataItem di, long uid) {
        return null;
    }

    public void remove() {
        dataItem.release();
    }

    // 把用户真正的数据 data 包装成 VM 层需要的 Entry 格式：xmin + xmax + data。
    public static byte[] wrapEntryRaw(long xid, byte[] data) {
        byte[] xmin = Parser.long2Byte(xid);
        byte[] xmax = new byte[8];
        return Bytes.concat(xmin, xmax, data);
    }

    // 以拷贝的形式返回内容
    // 加读锁 → 获取 DataItem → 跳过 xmin+xmax 16 字节 → 拷贝真正的数据 → 释放锁 → 返回。
    public byte[] data() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            byte[] data = new byte[sa.end - sa.start - OF_DATA];
            System.arraycopy(sa.raw, sa.start+OF_DATA, data, 0, data.length);
            return data;
        } finally {
            dataItem.rUnLock();
        }
    }


}