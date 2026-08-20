package top.risha.minidb.backend.utils;

public class Types {
    public static long addressToUid(int pageNumber, short offset) {
        long uOffset = offset & 0xFFFFL;
        return ((long)pageNumber << 32) | uOffset;
    }
}
