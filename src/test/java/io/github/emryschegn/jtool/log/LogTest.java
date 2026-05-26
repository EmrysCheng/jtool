package io.github.emryschegn.jtool.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

import static org.junit.jupiter.api.Assertions.*;

class LogTest {

    private Log log;
    private TestHandler testHandler;

    @BeforeEach
    void setUp() {
        JulBootstrap.init();
        log = Log.getLogger(LogTest.class);
        testHandler = new TestHandler();
        Logger.getLogger(LogTest.class.getName()).addHandler(testHandler);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        Logger.getLogger(LogTest.class.getName()).removeHandler(testHandler);
        MDC.clear();
    }

    // --- 基本日志级别 ---

    @Test
    void debugLogsAtConfigLevel() {
        log.debug("test debug");
        assertEquals(Level.CONFIG, testHandler.lastLevel());
        assertTrue(testHandler.lastMessage().contains("test debug"));
    }

    @Test
    void infoLogsAtInfoLevel() {
        log.info("test info");
        assertEquals(Level.INFO, testHandler.lastLevel());
    }

    @Test
    void warnLogsAtWarningLevel() {
        log.warn("test warn");
        assertEquals(Level.WARNING, testHandler.lastLevel());
    }

    @Test
    void errorLogsAtSevereLevel() {
        log.error("test error");
        assertEquals(Level.SEVERE, testHandler.lastLevel());
    }

    @Test
    void errorWithThrowable() {
        RuntimeException ex = new RuntimeException("boom");
        log.error("failed", ex);
        assertTrue(testHandler.lastMessage().contains("failed"));
        assertNotNull(testHandler.lastThrown());
        assertEquals("boom", testHandler.lastThrown().getMessage());
    }

    // --- 占位符格式化 ---

    @Test
    void placeholderSubstitution() {
        log.info("hello {} world {}", "foo", 42);
        assertTrue(testHandler.lastMessage().contains("hello foo world 42"));
    }

    @Test
    void noArgsReturnsPattern() {
        log.info("plain message");
        assertEquals("plain message", testHandler.lastMessage());
    }

    @Test
    void excessPlaceholdersLeftVerbatim() {
        log.info("a={} b={}");
        assertEquals("a={} b={}", testHandler.lastMessage());
    }

    @Test
    void excessArgsIgnored() {
        log.info("no placeholders", "extra1", "extra2");
        assertEquals("no placeholders", testHandler.lastMessage());
    }

    @Test
    void placeholderWithNullArg() {
        log.info("value is {}", (Object) null);
        assertEquals("value is null", testHandler.lastMessage());
    }

    @Test
    void multiplePlaceholderssWithSameValue() {
        log.info("a={} b={} c={}", "x", "x", "x");
        assertEquals("a=x b=x c=x", testHandler.lastMessage());
    }

    @Test
    void placeholderWithNumericTypes() {
        log.info("int={} long={} double={}", 1, 2L, 3.0);
        assertTrue(testHandler.lastMessage().contains("int=1"));
        assertTrue(testHandler.lastMessage().contains("long=2"));
        assertTrue(testHandler.lastMessage().contains("double=3.0"));
    }

    // --- Log 实例缓存 ---

    @Test
    void getLoggerReturnsSameInstance() {
        Log a = Log.getLogger(LogTest.class);
        Log b = Log.getLogger(LogTest.class);
        assertSame(a, b);
    }

    @Test
    void getLoggerReturnsDifferentInstanceForDifferentClass() {
        Log a = Log.getLogger(LogTest.class);
        Log b = Log.getLogger(String.class);
        assertNotSame(a, b);
    }

    // --- 并发安全 ---

    @Test
    void concurrentStepDoesNotThrow() throws Exception {
        int threadCount = 10;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    log.info("concurrent msg from thread");
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(0, errors.get());
    }

    // --- MDC + Log 集成 ---

    @Test
    void logWithTraceIdInMdc() {
        MDC.put(MDC.TRACE_ID_KEY, "trace-log-001");
        log.info("msg with trace");

        assertEquals("msg with trace", testHandler.lastMessage());
        assertEquals(Level.INFO, testHandler.lastLevel());
        assertEquals("trace-log-001", MDC.get(MDC.TRACE_ID_KEY));
    }

    @Test
    void logWithoutTraceIdInMdc() {
        log.info("msg without trace");

        assertEquals("msg without trace", testHandler.lastMessage());
        assertNull(MDC.get(MDC.TRACE_ID_KEY));
    }

    @Test
    void traceIdPersistsAcrossMultipleLogs() {
        MDC.put(MDC.TRACE_ID_KEY, "persistent-trace");

        log.info("first");
        log.info("second");
        log.info("third");

        assertEquals(3, testHandler.records.size());
        for (LogRecord ignored : testHandler.records) {
            assertEquals("persistent-trace", MDC.get(MDC.TRACE_ID_KEY));
        }
    }

    @Test
    void traceIdChangedBetweenLogs() {
        MDC.put(MDC.TRACE_ID_KEY, "trace-A");
        log.info("msg-A");

        MDC.put(MDC.TRACE_ID_KEY, "trace-B");
        log.info("msg-B");

        assertEquals("trace-B", MDC.get(MDC.TRACE_ID_KEY));
        assertEquals("msg-A", testHandler.records.get(0).getMessage());
        assertEquals("msg-B", testHandler.records.get(1).getMessage());
    }

    @Test
    void traceIdClearedBetweenLogs() {
        MDC.put(MDC.TRACE_ID_KEY, "trace-before");
        log.info("with trace");

        MDC.clear();
        log.info("without trace");

        assertNull(MDC.get(MDC.TRACE_ID_KEY));
        assertEquals(2, testHandler.records.size());
    }

    @Test
    void multipleMdcKeysDoNotAffectLogMessage() {
        MDC.put(MDC.TRACE_ID_KEY, "tid-001");
        MDC.put("userId", "user-abc");
        MDC.put("sessionId", "sess-xyz");

        log.info("multi-mdc msg");

        assertEquals("multi-mdc msg", testHandler.lastMessage());
        assertEquals("tid-001", MDC.get(MDC.TRACE_ID_KEY));
        assertEquals("user-abc", MDC.get("userId"));
        assertEquals("sess-xyz", MDC.get("sessionId"));
    }

    @Test
    void logInWorkerThreadWithPropagatedContext() throws Exception {
        MDC.put(MDC.TRACE_ID_KEY, "parent-trace");
        MDC.put("userId", "parent-user");
        Map<String, String> parentCtx = MDC.getCopy();


        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Log workerLog = Log.getLogger(LogTest.class);
            AtomicInteger errors = new AtomicInteger(0);

            executor.submit(() -> {
                try {
                    assertNull(MDC.get(MDC.TRACE_ID_KEY));
                    MDC.putAll(parentCtx);

                    assertEquals("parent-trace", MDC.get(MDC.TRACE_ID_KEY));
                    workerLog.info("worker msg");
                    assertEquals("worker msg", testHandler.lastMessage());
                } finally {
                    MDC.clear();
                    assertNull(MDC.get(MDC.TRACE_ID_KEY));
                }
            }).get(5, TimeUnit.SECONDS);

            assertEquals(0, errors.get());
            assertEquals("parent-trace", MDC.get(MDC.TRACE_ID_KEY));
        }
    }

    @Test
    void logInWorkerThreadWithoutPropagation() throws Exception {
        MDC.put(MDC.TRACE_ID_KEY, "main-only");

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                assertNull(MDC.get(MDC.TRACE_ID_KEY));
                log.info("worker without trace");
            }).get(5, TimeUnit.SECONDS);

            assertEquals("main-only", MDC.get(MDC.TRACE_ID_KEY));
        }
    }

    @Test
    void concurrentLogsWithIndependentMdc() throws Exception {
        int threadCount = 10;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    start.await();
                    MDC.put(MDC.TRACE_ID_KEY, "trace-" + id);
                    log.info("msg from thread {}", id);
                    assertEquals("trace-" + id, MDC.get(MDC.TRACE_ID_KEY));
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    MDC.clear();
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(0, errors.get());
        assertEquals(threadCount, testHandler.records.size());
    }

    // --- MDC 完整生命周期 + Log ---

    @Test
    void mdcLifecycleWithLog() {
        assertNull(MDC.get(MDC.TRACE_ID_KEY));

        log.info("before trace");
        assertNull(MDC.get(MDC.TRACE_ID_KEY));

        MDC.put(MDC.TRACE_ID_KEY, "trace-life");
        log.info("during trace");
        assertEquals("trace-life", MDC.get(MDC.TRACE_ID_KEY));

        MDC.remove(MDC.TRACE_ID_KEY);
        log.info("after remove");
        assertNull(MDC.get(MDC.TRACE_ID_KEY));

        MDC.put(MDC.TRACE_ID_KEY, "trace-new");
        log.info("re-added trace");
        assertEquals("trace-new", MDC.get(MDC.TRACE_ID_KEY));

        MDC.clear();
        assertNull(MDC.get(MDC.TRACE_ID_KEY));
        log.info("after clear");
        assertNull(MDC.get(MDC.TRACE_ID_KEY));
    }

    // --- Helper ---

    private static class TestHandler extends Handler {

        final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        Level lastLevel() {
            return records.get(records.size() - 1).getLevel();
        }

        String lastMessage() {
            return records.get(records.size() - 1).getMessage();
        }

        Throwable lastThrown() {
            return records.get(records.size() - 1).getThrown();
        }
    }
}
