package io.github.emryschegn.jtool.log;

import java.util.logging.Level;
import java.util.logging.Logger;
/**
 * 日志唯一入口
 * 
 * @author chengpeng
 * @since 2026/5/24 21:46
 */
public final class Log {

    private final Logger logger;

    // 通过 LoggerFactory 获取实例，保证初始化和缓存逻辑统一
    Log(Class<?> clazz) {
        logger = Logger.getLogger(clazz.getName());
    }

    public static Log getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    public void debug(String msg, Object... args) {
        log(Level.CONFIG, msg, null, args);
    }

    public void info(String msg, Object... args) {
        log(Level.INFO, msg, null, args);
    }

    public void warn(String msg, Object... args) {
        log(Level.WARNING, msg, null, args);
    }

    public void error(String msg, Object... args) {
        log(Level.SEVERE, msg, null, args);
    }

    public void error(String msg, Throwable e) {
        log(Level.SEVERE, msg, e);
    }

    private void log(Level level, String pattern, Throwable throwable, Object... args) {
        if (!logger.isLoggable(level)) {
            return;
        }
        String message = format(pattern, args);
        if (throwable == null) {
            logger.log(level, message);
        } else {
            logger.log(level, message, throwable);
        }
    }

    // 使用 indexOf 替代 replaceFirst 正则，避免每次占位符都编译正则表达式
    private String format(String pattern, Object... args) {
        if (args == null || args.length == 0) {
            return pattern;
        }
        StringBuilder sb = new StringBuilder(pattern.length() + 32);
        int argIdx = 0, start = 0;
        int pos;
        while ((pos = pattern.indexOf("{}", start)) != -1 && argIdx < args.length) {
            sb.append(pattern, start, pos);
            sb.append(args[argIdx++]);
            start = pos + 2;
        }
        sb.append(pattern, start, pattern.length());
        return sb.toString();
    }
}