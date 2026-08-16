package top.risha.minidb.backend.vm;

import top.risha.minidb.backend.tm.TransactionManager;

public class Visibility {

    private static boolean readCommitted(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();
        if(xmin == xid && xmax == 0) return true;

        if(tm.isCommitted(xmin)) {
            if(xmax == 0) return true;
            if(xmax != xid) {
                if(!tm.isCommitted(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }
}
