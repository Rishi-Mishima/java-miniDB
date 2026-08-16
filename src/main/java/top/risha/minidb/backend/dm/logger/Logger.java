package top.risha.minidb.backend.dm.logger;

public interface Logger {
    static Logger create(String path) {
        return null;
    }

    static Logger open(String path) {
        return null;
    }

    void log(byte[] data);
    void truncate(long x) throws Exception;
    byte[] next();
    void rewind();
    void close();
}
