package io.github.emryschegn.jtool.log;

import java.util.HashMap;
import java.util.Map;

/**
 * 链路追踪上下文（Mapped Diagnostic Context）
 *
 * <p>基于 {@link ThreadLocal} 实现线程隔离，每个线程拥有独立的上下文 Map。
 * 选用 {@link HashMap} 而非 ConcurrentHashMap，因为每个线程只访问自己的 Map，不存在并发竞争。
 *
 * <p>在线程池环境中使用时，任务结束应调用 {@link #clear()} 清理，避免上下文泄漏到后续任务。
 *
 * @author chengpeng
 * @since 2026/5/24 21:37
 */
public final class MDC {

    public static final String TRACE_ID_KEY = "traceId";

    private static final int MAX_CONTEXT_SIZE = 64;

    private MDC() {
    }

    private static final ThreadLocal<Map<String, String>> CONTEXT =
            ThreadLocal.withInitial(HashMap::new);

    public static void put(String key, String value) {
        Map<String, String> ctx = CONTEXT.get();
        if (ctx.size() >= MAX_CONTEXT_SIZE && !ctx.containsKey(key)) {
            throw new IllegalStateException("MDC context size exceeded " + MAX_CONTEXT_SIZE);
        }
        ctx.put(key, value);
    }

    public static void putAll(Map<String, String> map) {
        if (map == null) {
            return;
        }
        Map<String, String> ctx = CONTEXT.get();
        ctx.putAll(map);
    }

    public static String get(String key) {
        return CONTEXT.get().get(key);
    }

    public static void remove(String key) {
        CONTEXT.get().remove(key);
    }

    /** 获取当前线程上下文的快照副本 */
    public static Map<String, String> getCopy() {
        return new HashMap<>(CONTEXT.get());
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
