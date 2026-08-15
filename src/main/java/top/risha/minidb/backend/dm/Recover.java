package top.risha.minidb.backend.dm;

import top.risha.minidb.backend.dm.logger.Logger;
import top.risha.minidb.backend.dm.pageCache.PageCache;
import top.risha.minidb.backend.tm.TransactionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Recover {

    private static final byte LOG_TYPE_INSERT = 0;
    private static final byte LOG_TYPE_UPDATE = 1;

    private static final int REDO = 0;
    private static final int UNDO = 1;

    // updateLog:
    // [LogType] [XID] [UID] [OldRaw] [NewRaw]

    // insertLog:
    // [LogType] [XID] [Pgno] [Offset] [Raw]

    static class InsertLogInfo {
        long xid;
        int pgno;
        short offset;
        byte[] raw;
    }

    static class UpdateLogInfo {
        long xid;
        int pgno;
        short offset;
        byte[] oldRaw;
        byte[] newRaw;
    }

    private static void redoTranscations(TransactionManager tm, Logger lg, PageCache pc) {
        // 将日志读取游标移到最开始
        lg.rewind();
        while(true) {
            // 通过 while 循环不断调用 lg.next()正序（从前到后） 逐条读取日志记录，直到文件末尾（返回 null
            byte[] log = lg.next();
            if(log == null) break;
            if(isInsertLog(log)) {
                InsertLogInfo li = parseInsertLog(log);
                long xid = li.xid;
                // 说明该事务在系统崩溃前已经完成（已提交 Committed 或已被显式回滚 Aborted）
                if(!tm.isActive(xid)) {
                    doInsertLog(pc, log, REDO);
                }
            } else {
                UpdateLogInfo xi = parseUpdateLog(log);
                long xid = xi.xid;
                if(!tm.isActive(xid)) {
                    doUpdateLog(pc, log, REDO);
                }
            }
        }
    }

    private static void undoTranscations(TransactionManager tm, Logger lg, PageCache pc) {
        Map<Long, List<byte[]>> logCache = new HashMap<>();
        lg.rewind();
        while(true) {
            byte[] log = lg.next();
            if(log == null) break;
            if(isInsertLog(log)) {
                InsertLogInfo li = parseInsertLog(log);
                long xid = li.xid;
                if(tm.isActive(xid)) {
                    if(!logCache.containsKey(xid)) {
                        logCache.put(xid, new ArrayList<>());
                    }
                    logCache.get(xid).add(log);
                }
            } else {
                UpdateLogInfo xi = parseUpdateLog(log);
                long xid = xi.xid;
                if(tm.isActive(xid)) {
                    if(!logCache.containsKey(xid)) {
                        logCache.put(xid, new ArrayList<>());
                    }
                    logCache.get(xid).add(log);
                }
            }
        }

        // 对所有active log进行倒序undo
        for(Map.Entry<Long, List<byte[]>> entry : logCache.entrySet()) {
            List<byte[]> logs = entry.getValue();
            for (int i = logs.size()-1; i >= 0; i --) {
                byte[] log = logs.get(i);
                if(isInsertLog(log)) {
                    doInsertLog(pc, log, UNDO);
                } else {
                    doUpdateLog(pc, log, UNDO);
                }
            }
            tm.abort(entry.getKey());
        }
    }
    
    private static void doUpdateLog(PageCache pc, byte[] log, int redo) {
    }

    private static UpdateLogInfo parseUpdateLog(byte[] log) {
        return null;
    }

    private static void doInsertLog(PageCache pc, byte[] log, int redo) {
    }

    private static InsertLogInfo parseInsertLog(byte[] log) {
        return null;
    }

    private static boolean isInsertLog(byte[] log) {
        return false;
    }
}
