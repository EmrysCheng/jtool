package io.github.emryschegn.jtool.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.*;

/**
 * 自动初始化 JUL（Java Util Logging）
 *
 * <p>移除默认 Handler，配置自定义的 {@link LogFormatter}，同时输出到控制台和滚动日志文件。
 * 通过 {@code LoggerFactory} 的 static 块触发，应用代码无需手动初始化。
 *
 * @author chengpeng
 * @since 2026/5/24 21:44
 */
final class JulBootstrap {

    static final String LOG_DIR = "logs";

    // volatile + synchronized 配合使用：synchronized 保证初始化互斥，
    // volatile 保证后续线程进入 synchronized 前能看到 initialized 的最新值
    private static volatile boolean initialized;

    private JulBootstrap() {
    }

    static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
            Logger root = Logger.getLogger("");
            // 移除 JVM 默认的 ConsoleHandler，避免重复输出
            for (Handler h : root.getHandlers()) {
                root.removeHandler(h);
            }
            Formatter formatter = new LogFormatter();

            ConsoleHandler console = new ConsoleHandler();
            console.setLevel(Level.ALL);
            console.setFormatter(formatter);
            root.addHandler(console);

            // 滚动日志：单文件 100MB，最多 10 个世代，总计约 1GB 磁盘占用
            // %g 为 JUL 内置世代占位符
            FileHandler file = new FileHandler(LOG_DIR + "/app-%g.log", 100 * 1024 * 1024, 10, true);
            file.setLevel(Level.ALL);
            file.setFormatter(formatter);
            root.addHandler(file);
            root.setLevel(Level.ALL);

            // JVM 关闭时确保日志缓冲刷盘；异常静默忽略，因为关闭阶段日志不可恢复
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                for (Handler h : root.getHandlers()) {
                    try {
                        h.flush();
                        h.close();
                    } catch (Exception ignored) {
                    }
                }
            }, "jtool-log-shutdown"));
            initialized = true;
        } catch (IOException e) {
            throw new RuntimeException("log init failed", e);
        }
    }
}
