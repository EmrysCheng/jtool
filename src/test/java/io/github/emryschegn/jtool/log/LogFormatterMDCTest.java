package io.github.emryschegn.jtool.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogFormatter + MDC 集成测试
 *
 * <p>验证 LogFormatter 输出中 traceId 的渲染、MDC 上下文与日志格式的交互、
 * 以及无 MDC 时的输出格式正确性。
 */
class LogFormatterMDCTest {

    private LogFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new LogFormatter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // --- 无 MDC 时的格式 ---

    @Test
    void formatWithoutTraceId() {
        LogRecord record = new LogRecord(Level.INFO, "hello");
        record.setLoggerName("com.example.Service");
        String output = formatter.format(record);

        // 无 traceId 时不应该出现连续两个 [] 块
        // 格式：时间 [thread] LEVEL logger - message\n
        assertTrue(output.startsWith("20"));  // timestamp starts with year
        assertTrue(output.contains("[main]"));
        assertTrue(output.contains("INFO"));
        assertTrue(output.contains(" - hello"));
        assertTrue(output.endsWith("\n"));

        // 无 traceId：格式为 "时间 [thread] LEVEL ..."  只有一个 [] 段
        String afterThread = output.substring(output.indexOf(']') + 1);
        assertFalse(afterThread.startsWith(" ["));
    }

    @Test
    void formatOutputStructure() {
        LogRecord record = new LogRecord(Level.INFO, "test msg");
        record.setLoggerName("io.github.emryschegn.jtool.log.LogFormatterMDCTest");
        String output = formatter.format(record);

        // 验证完整格式：时间 [线程名] LEVEL 缩写包名.类名 - 消息\n
        assertTrue(output.contains(" ["), "should contain thread bracket");
        assertTrue(output.contains("] "), "should close thread bracket");
        assertTrue(output.contains("INFO"), "should contain level");
        assertTrue(output.contains("i.g.e.j.l.LogFormatterMDCTest"), "should contain abbreviated logger");
        assertTrue(output.contains(" - test msg"), "should contain message with separator");
    }

    // --- TraceId 在格式化输出中的渲染 ---

    @Test
    void formatWithTraceId() {
        MDC.put(MDC.TRACE_ID_KEY, "trace-abc-123");

        LogRecord record = new LogRecord(Level.INFO, "with trace");
        record.setLoggerName("com.example.Service");
        String output = formatter.format(record);

        assertTrue(output.contains("[trace-abc-123]"), "traceId should appear in brackets");
        assertTrue(output.contains(" - with trace"));
    }

    @Test
    void traceIdRenderedBetweenThreadAndLevel() {
        MDC.put(MDC.TRACE_ID_KEY, "tid-999");

        LogRecord record = new LogRecord(Level.INFO, "msg");
        record.setLoggerName("Test");
        String output = formatter.format(record);

        int threadEnd = output.indexOf(']');
        int traceStart = output.indexOf("[tid-999]");
        int levelPos = output.indexOf("INFO");

        assertTrue(threadEnd > 0, "thread bracket should exist");
        assertTrue(traceStart > threadEnd, "traceId should come after thread name");
        assertTrue(levelPos > traceStart, "level should come after traceId");
    }

    @Test
    void emptyTraceIdNotRendered() {
        MDC.put(MDC.TRACE_ID_KEY, "");

        LogRecord record = new LogRecord(Level.INFO, "msg");
        record.setLoggerName("Test");
        String output = formatter.format(record);

        assertFalse(output.contains("[]"), "empty traceId should not produce empty brackets");
    }

    @Test
    void traceIdRemovedBeforeFormat() {
        MDC.put(MDC.TRACE_ID_KEY, "trace-remove");
        MDC.remove(MDC.TRACE_ID_KEY);

        LogRecord record = new LogRecord(Level.INFO, "msg");
        record.setLoggerName("Test");
        String output = formatter.format(record);

        assertFalse(output.contains("trace-remove"));
    }

    @Test
    void traceIdClearedBeforeFormat() {
        MDC.put(MDC.TRACE_ID_KEY, "trace-clear");
        MDC.clear();

        LogRecord record = new LogRecord(Level.INFO, "msg");
        record.setLoggerName("Test");
        String output = formatter.format(record);

        assertFalse(output.contains("trace-clear"));
    }

    // --- 非 traceId 的 MDC key 不影响输出 ---

    @Test
    void nonTraceIdKeyNotRendered() {
        MDC.put("userId", "user-123");
        MDC.put("sessionId", "sess-456");

        LogRecord record = new LogRecord(Level.INFO, "msg");
        record.setLoggerName("Test");
        String output = formatter.format(record);

        assertFalse(output.contains("user-123"), "non-traceId keys should not appear");
        assertFalse(output.contains("sess-456"), "non-traceId keys should not appear");
    }

    @Test
    void onlyTraceIdRenderedAmongMultipleKeys() {
        MDC.put(MDC.TRACE_ID_KEY, "visible-trace");
        MDC.put("userId", "invisible-user");
        MDC.put("sessionId", "invisible-session");

        LogRecord record = new LogRecord(Level.INFO, "msg");
        record.setLoggerName("Test");
        String output = formatter.format(record);

        assertTrue(output.contains("[visible-trace]"));
        assertFalse(output.contains("invisible-user"));
        assertFalse(output.contains("invisible-session"));
    }

    // --- TraceId 线程隔离 ---

    @Test
    void traceIsolatedAcrossThreads() throws Exception {
        MDC.put(MDC.TRACE_ID_KEY, "main-trace");

        String[] workerOutput = new String[1];
        Thread t = new Thread(() -> {
            assertNull(MDC.get(MDC.TRACE_ID_KEY));
            MDC.put(MDC.TRACE_ID_KEY, "worker-trace");

            LogRecord record = new LogRecord(Level.INFO, "worker msg");
            record.setLoggerName("Worker");
            workerOutput[0] = formatter.format(record);
        });
        t.start();
        t.join();

        assertTrue(workerOutput[0].contains("[worker-trace]"));
        assertEquals("main-trace", MDC.get(MDC.TRACE_ID_KEY));
    }

    @Test
    void tracePropagatedToWorkerThread() throws Exception {
        MDC.put(MDC.TRACE_ID_KEY, "parent-trace");
        java.util.Map<String, String> parentCtx = MDC.getCopy();

        String[] workerOutput = new String[1];
        Thread t = new Thread(() -> {
            MDC.putAll(parentCtx);

            LogRecord record = new LogRecord(Level.INFO, "propagated msg");
            record.setLoggerName("Worker");
            workerOutput[0] = formatter.format(record);
        });
        t.start();
        t.join();

        assertTrue(workerOutput[0].contains("[parent-trace]"));
    }

    // --- 各级别日志与 MDC 的组合 ---

    @Test
    void allLevelsWithTraceId() {
        MDC.put(MDC.TRACE_ID_KEY, "multi-level");

        Level[] levels = {Level.CONFIG, Level.INFO, Level.WARNING, Level.SEVERE};
        String[] expectedLevelStr = {"DEBUG", "INFO ", "WARN ", "ERROR"};

        for (int i = 0; i < levels.length; i++) {
            LogRecord record = new LogRecord(levels[i], "msg-" + i);
            record.setLoggerName("Test");
            String output = formatter.format(record);

            assertTrue(output.contains("[multi-level]"),
                    "traceId should appear for " + levels[i]);
            assertTrue(output.contains(expectedLevelStr[i]),
                    "level " + expectedLevelStr[i] + " should appear");
        }
    }

    // --- 异常堆栈与 MDC ---

    @Test
    void exceptionWithTraceId() {
        MDC.put(MDC.TRACE_ID_KEY, "err-trace");

        LogRecord record = new LogRecord(Level.SEVERE, "something failed");
        record.setLoggerName("com.example.Service");
        record.setThrown(new RuntimeException("test exception"));
        String output = formatter.format(record);

        assertTrue(output.contains("[err-trace]"));
        assertTrue(output.contains("something failed"));
        assertTrue(output.contains("RuntimeException"));
        assertTrue(output.contains("test exception"));
    }

    // --- 格式一致性 ---

    @Test
    void sameTraceIdProducesSameOutputAcrossRecords() {
        MDC.put(MDC.TRACE_ID_KEY, "consistent-id");

        LogRecord r1 = new LogRecord(Level.INFO, "first");
        r1.setLoggerName("Test");
        LogRecord r2 = new LogRecord(Level.INFO, "second");
        r2.setLoggerName("Test");

        String o1 = formatter.format(r1);
        String o2 = formatter.format(r2);

        // traceId 段应该相同
        String traceSegment = " [consistent-id]";
        assertTrue(o1.contains(traceSegment));
        assertTrue(o2.contains(traceSegment));
    }

    @Test
    void traceIdChangedBetweenFormats() {
        MDC.put(MDC.TRACE_ID_KEY, "trace-A");
        LogRecord r1 = new LogRecord(Level.INFO, "first");
        r1.setLoggerName("Test");
        String o1 = formatter.format(r1);

        MDC.put(MDC.TRACE_ID_KEY, "trace-B");
        LogRecord r2 = new LogRecord(Level.INFO, "second");
        r2.setLoggerName("Test");
        String o2 = formatter.format(r2);

        assertTrue(o1.contains("[trace-A]"));
        assertFalse(o1.contains("[trace-B]"));
        assertTrue(o2.contains("[trace-B]"));
        assertFalse(o2.contains("[trace-A]"));
    }

    // --- 特殊 traceId 值 ---

    @Test
    void traceIdWithSpecialCharacters() {
        MDC.put(MDC.TRACE_ID_KEY, "trace-abc-123-XYZ");

        LogRecord record = new LogRecord(Level.INFO, "msg");
        record.setLoggerName("Test");
        String output = formatter.format(record);

        assertTrue(output.contains("[trace-abc-123-XYZ]"));
    }

    @Test
    void traceIdWithUnicodeCharacters() {
        MDC.put(MDC.TRACE_ID_KEY, "追踪-001");

        LogRecord record = new LogRecord(Level.INFO, "msg");
        record.setLoggerName("Test");
        String output = formatter.format(record);

        assertTrue(output.contains("[追踪-001]"));
    }

    @Test
    void traceIdWithLongValue() {
        StringBuilder sb = new StringBuilder("trace-");
        for (int i = 0; i < 200; i++) sb.append('a');
        String longId = sb.toString();
        MDC.put(MDC.TRACE_ID_KEY, longId);

        LogRecord record = new LogRecord(Level.INFO, "msg");
        record.setLoggerName("Test");
        String output = formatter.format(record);

        assertTrue(output.contains("[" + longId + "]"));
    }
}
