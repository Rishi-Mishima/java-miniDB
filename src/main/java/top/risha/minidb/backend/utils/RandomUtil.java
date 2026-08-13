package top.risha.minidb.backend.utils;

import java.security.SecureRandom;


/**
 * 生成指定长度随机字节数组的工具方法。
 */
public class RandomUtil {
    // 全局复用单例，避免频繁初始化 SecureRandom 的高昂开销
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static byte[] randomBytes(int length) {
        byte[] buf = new byte[length];
        SECURE_RANDOM.nextBytes(buf);
        return buf;
    }
}
