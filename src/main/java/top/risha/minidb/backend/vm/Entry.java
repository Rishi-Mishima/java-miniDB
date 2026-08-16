package top.risha.minidb.backend.vm;

import top.risha.minidb.backend.dm.dataItem.DataItem;

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
}