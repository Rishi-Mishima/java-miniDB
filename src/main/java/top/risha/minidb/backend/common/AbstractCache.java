package top.risha.minidb.backend.common;
import top.risha.minidb.common.Error;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * AbstractCache 实现了一个引用计数策略的缓存
 */
public abstract class AbstractCache<T> {
    private HashMap<Long, T> cache;                     // 实际缓存的数据
    private HashMap<Long, Integer> references;          // 资源的引用个数
    private HashMap<Long, Boolean> getting;             // 正在被获取的资源            // 正在获取某资源的线程

    private int maxResource;                            // 缓存的最大缓存资源数
    private int count = 0;                              // 缓存中元素的个数
    private Lock lock;

    public AbstractCache(int maxResource) {
        this.maxResource = maxResource;
        cache = new HashMap<>();
        references = new HashMap<>();
        getting = new HashMap<>();
        lock = new ReentrantLock();
    }

    protected T get(long key) throws Exception {
        // 1. 锁内状态检查与“抢占”加载权 (while(true))
        // 因为无法预知其他线程什么时候能把数据从磁盘读完装入缓存，所以采用循环不断尝试，直到成功拿到资源或发起加载为止
        while(true) {
            // 保护状态
            lock.lock();
            // 检查冲突： 判断是否有其他线程已经在去磁盘加载该 key 的路上了
            if(getting.containsKey(key)) {
                // 请求的资源正在被其他线程获取
                //  释放锁（极为关键！）： 在准备睡眠等待之前，必须先释放锁！如果带着锁睡觉，真正负责去磁盘读数据的线程就永远拿不到锁来更新 cache 和移除 getting 标记，导致死锁（Deadlock）
                lock.unlock();
                try {
                    // 避免 CPU 空转（Spinlock with sleep）： 放弃 CPU 时间片，挂起当前线程 1 毫秒
                    // 如果不 sleep 直接 continue，当前线程会以 100% 的 CPU 使用率疯狂死循环，造成极大的 CPU 资源浪费。
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                continue;
            }

            //  2. 缓存命中（Cache Hit）
            if(cache.containsKey(key)) {
                // 资源在缓存中，直接返回
                T obj = cache.get(key);
                // // 引用计数 +1
                references.put(key, references.get(key) + 1);
                // 解锁后直接返回数据，不再执行后续逻辑
                lock.unlock();
                return obj;
            }

            // 3. 缓存未命中，准备去磁盘加载：检查缓存上限
            // 尝试获取该资源
            if(maxResource > 0 && count == maxResource) {
                lock.unlock();
                throw Error.CacheFullException;
            }

            // 4. 抢占加载权, 如果还有容量
            count ++; // 记录缓存节点数增加。
            getting.put(key, true); // 宣告自己抢到了加载权，其他后来的线程看到这个标记就会在步骤 1 挂起等待。
            lock.unlock(); //释放锁并跳出 while 循环。
            break; // // 跳出循环，去执行磁盘 I/O
        }

        T obj = null;
        try {
            // 抽象方法：从物理磁盘加载
            obj = getForCache(key);
        } catch(Exception e) {
            //  读取异常时的清理归位
            lock.lock();
            count --;
            getting.remove(key);
            lock.unlock();
            throw e;
        }

        // 成功读出数据，重新加锁写回缓存
        lock.lock();
        getting.remove(key); // 移除“正在获取”标记，唤醒/允许后续线程读取
        cache.put(key, obj); // 放入缓存表
        references.put(key, 1); // 初始化引用计数为 1（因为是当前线程要用）
        lock.unlock();

        return obj;
    }

    /**
     * 强行释放一个缓存 - 资源释放与驱逐逻辑
     * 当上层模块使用完某个缓存资源后，通过调用 release(key) 声明放弃对该资源的使用。
     * 它的核心逻辑是递减引用计数；当引用计数归零时，安全地将该资源从内存驱逐（Evict），并触发写回/刷盘操作。
     */
    protected void release(long key) {
        // releaseForCache(obj) 是在锁内执行
        lock.lock();
        try {
            // 将该资源在 references 映射中的引用次数减 1，并暂存计算后的值
            int ref = references.get(key)-1;
            // 分支1: 引用归零，触发驱逐
            if(ref == 0) {
                // 取出对象：T obj = cache.get(key); 从 cache 中拿到资源实例。
                T obj = cache.get(key);
                releaseForCache(obj); // 写回/持久化
                // 清理内存 Map
                references.remove(key); // 从引用计数表中移除。
                cache.remove(key); //从实际缓存表中移除（真正从内存释放）
                count --; // 释放缓存额度：count --; 将已用缓存资源数减 1
            } else {
                // 分支二：尚有其他模块在使用（ref > 0）
                // 如果引用数依然大于 0，说明还有别的线程/模块在共享使用该资源，不能驱逐。仅更新 references 中的计数即可。
                references.put(key, ref);
            }
        } finally {
            lock.unlock();
        }
    }


    /**
     * 当资源不在缓存时的获取行为
     */
    protected abstract T getForCache(long key) throws Exception;
    /**
     * 当资源被驱逐时的写回行为
     */
    protected abstract void releaseForCache(T obj);
}
