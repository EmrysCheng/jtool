package io.github.emryschegn.jtool.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * 自定义日志格式化
 *
 * <p>输出格式：{@code yyyy-MM-dd HH:mm:ss [thread] [traceId] LEVEL logger - message}
 *
 * @author chengpeng
 * @since 2026/5/24 21:38
 */
public class LogFormatter extends Formatter {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 缓存系统时区，避免每条日志都调用 ZoneId.systemDefault() 查找
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    // 同毫秒内的时间戳缓存，避免每条日志都创建 LocalDateTime + Instant 对象
    private static final int TS_CACHE_SIZE = 8;
    private static final long[] tsCacheMillis = new long[TS_CACHE_SIZE];
    private static final String[] tsCacheFormatted = new String[TS_CACHE_SIZE];
    private static int tsCacheIndex;

    // 预计算已填充到5字符宽度的级别字符串，避免每条日志调用 String.format
    private static final Map<Level, String> LEVEL_MAP;

    static {
        LEVEL_MAP = new HashMap<>();
        LEVEL_MAP.put(Level.SEVERE, "ERROR");
        LEVEL_MAP.put(Level.WARNING, "WARN ");
        LEVEL_MAP.put(Level.INFO, "INFO ");
        LEVEL_MAP.put(Level.CONFIG, "DEBUG");
        LEVEL_MAP.put(Level.FINE, "DEBUG");
        LEVEL_MAP.put(Level.FINER, "TRACE");
        LEVEL_MAP.put(Level.FINEST, "TRACE");
    }

    /**
     * Logger名称显示风格
     */
    private enum LoggerNameStyle {

        /** 完整包名 */
        FULL,

        /** 仅类名 */
        SIMPLE,

        /**
         * 包名缩写: c.d.s.ServiceImpl
         */
        ABBREVIATE,

        /** 固定长度 */
        FIXED
    }

    private static final LoggerNameStyle LOGGER_STYLE = LoggerNameStyle.ABBREVIATE;

    private static final int LOGGER_WIDTH = 40;

    // Logger名称缩写缓存，类名数量有限（通常数百到数千），避免每条日志重新 split + 拼接
    private static final ConcurrentHashMap<String, String> ABBREVIATED_CACHE = new ConcurrentHashMap<>();

    @Override
    public String format(LogRecord record) {

        StringBuilder sb = new StringBuilder(256);

        // 时间（同毫秒缓存）
        sb.append(formatTimestamp(record.getMillis()));

        // 线程名
        sb.append(" [")
                .append(Thread.currentThread().getName())
                .append(']');

        // MDC TraceId —— 直接读取单key，避免 getCopy() 每次分配 HashMap
        String traceId = MDC.get(MDC.TRACE_ID_KEY);
        if (traceId != null && !traceId.isEmpty()) {
            sb.append(" [").append(traceId).append(']');
        }

        // 日志级别（预填充对齐）
        String levelStr = LEVEL_MAP.get(record.getLevel());
        if (levelStr == null) {
            levelStr = record.getLevel().getName();
        }
        sb.append(' ').append(levelStr);

        // Logger名称
        sb.append(' ').append(formatLoggerName(record.getLoggerName()));

        // 日志内容
        sb.append(" - ")
                .append(formatMessage(record))
                .append(System.lineSeparator());

        // 异常堆栈
        Throwable throwable = record.getThrown();
        if (throwable != null) {
            StringWriter sw = new StringWriter(512);
            throwable.printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }

        return sb.toString();
    }

    private String formatLoggerName(String loggerName) {
        if (loggerName == null) {
            return "";
        }
        switch (LOGGER_STYLE) {
            case SIMPLE:
                return simpleName(loggerName);
            case ABBREVIATE:
                return cachedAbbreviate(loggerName);
            case FIXED:
                return fixedWidth(loggerName);
            default:
                return loggerName;
        }
    }

    private String simpleName(String loggerName) {
        int idx = loggerName.lastIndexOf('.');
        return idx < 0 ? loggerName : loggerName.substring(idx + 1);
    }

    private String cachedAbbreviate(String loggerName) {
        String cached = ABBREVIATED_CACHE.get(loggerName);
        if (cached != null) {
            return cached;
        }
        return ABBREVIATED_CACHE.computeIfAbsent(loggerName, LogFormatter::abbreviate);
    }

    /**
     * 包名缩写: com.demo.service.UserServiceImpl => c.d.s.UserServiceImpl
     */
    static String abbreviate(String loggerName) {
        int len = loggerName.length();
        // 快速路径：无包名的简单类名
        int lastDot = loggerName.lastIndexOf('.');
        if (lastDot < 0) {
            return loggerName;
        }
        // 手动遍历提取每段首字符 + '.'，避免 split 正则开销
        StringBuilder sb = new StringBuilder(len);
        int segStart = 0;
        for (int i = 0; i <= lastDot; i++) {
            if (loggerName.charAt(i) == '.') {
                if (i > segStart) {
                    sb.append(loggerName.charAt(segStart)).append('.');
                }
                segStart = i + 1;
            }
        }
        sb.append(loggerName, lastDot + 1, len);
        return sb.toString();
    }

    private static synchronized String formatTimestamp(long epochMillis) {
        for (int i = 0; i < TS_CACHE_SIZE; i++) {
            if (tsCacheMillis[i] == epochMillis) {
                return tsCacheFormatted[i];
            }
        }
        LocalDateTime logTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochMillis), SYSTEM_ZONE);
        String formatted = logTime.format(DATE_FORMATTER);
        int idx = tsCacheIndex;
        tsCacheMillis[idx] = epochMillis;
        tsCacheFormatted[idx] = formatted;
        tsCacheIndex = (idx + 1) % TS_CACHE_SIZE;
        return formatted;
    }

    private String fixedWidth(String loggerName) {
        String shortName = cachedAbbreviate(loggerName);
        if (shortName.length() > LOGGER_WIDTH) {
            shortName = shortName.substring(shortName.length() - LOGGER_WIDTH);
        }
        return String.format("%-" + LOGGER_WIDTH + "s", shortName);
    }
}
