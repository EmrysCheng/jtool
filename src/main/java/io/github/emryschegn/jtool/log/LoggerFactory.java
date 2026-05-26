package io.github.emryschegn.jtool.log;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 日志工厂（自动触发初始化）
 *
 * <p>首次加载时通过 static 块触发 {@link JulBootstrap#init()}，保证 JUL 在首次 getLogger 调用前完成配置。
 * 类初始化由 JVM 保证线程安全（JLS 12.4.2），无需额外同步。
 *
 * @author chengpeng
 * @since 2026/5/24 21:46
 */
public final class LoggerFactory {

    private LoggerFactory() {
    }

    private static final ConcurrentHashMap<Class<?>, Log> CACHE = new ConcurrentHashMap<>();

    static {
        JulBootstrap.init();
    }

    public static Log getLogger(Class<?> clazz) {
        return CACHE.computeIfAbsent(clazz, Log::new);
    }
}
