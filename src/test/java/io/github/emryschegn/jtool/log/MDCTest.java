package io.github.emryschegn.jtool.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MDCTest {

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // --- Basic CRUD ---

    @Test
    void putAndGet() {
        MDC.put("key", "value");
        assertEquals("value", MDC.get("key"));
    }

    @Test
    void getReturnsNullWhenAbsent() {
        assertNull(MDC.get("nonexistent"));
    }

    @Test
    void remove() {
        MDC.put("key", "value");
        MDC.remove("key");
        assertNull(MDC.get("key"));
    }

    @Test
    void removeNonExistentKeyIsNoOp() {
        assertDoesNotThrow(() -> MDC.remove("noSuchKey"));
    }

    @Test
    void clear() {
        MDC.put("a", "1");
        MDC.put("b", "2");
        MDC.clear();
        assertNull(MDC.get("a"));
        assertNull(MDC.get("b"));
    }

    @Test
    void clearOnEmptyContextIsNoOp() {
        assertDoesNotThrow(MDC::clear);
    }

    @Test
    void clearAfterPutAllowsReuse() {
        MDC.put("key", "value");
        MDC.clear();
        assertNull(MDC.get("key"));

        MDC.put("key", "newValue");
        assertEquals("newValue", MDC.get("key"));
    }

    // --- Value edge cases ---

    @Test
    void putNullValueStoresNull() {
        MDC.put("key", null);
        assertNull(MDC.get("key"));
        assertTrue(MDC.getCopy().containsKey("key"));
    }

    @Test
    void putEmptyStringValue() {
        MDC.put("key", "");
        assertEquals("", MDC.get("key"));
    }

    @Test
    void putOverwritesPreviousValue() {
        MDC.put("key", "first");
        assertEquals("first", MDC.get("key"));
        MDC.put("key", "second");
        assertEquals("second", MDC.get("key"));
    }

    @Test
    void putSameKeyMultipleTimes() {
        for (int i = 0; i < 10; i++) {
            MDC.put("key", "val" + i);
        }
        assertEquals("val9", MDC.get("key"));
        assertEquals(1, MDC.getCopy().size());
    }

    // --- getCopy ---

    @Test
    void getCopyReturnsSnapshot() {
        MDC.put("key", "value");
        Map<String, String> copy = MDC.getCopy();
        assertEquals("value", copy.get("key"));

        MDC.put("key", "changed");
        assertEquals("value", copy.get("key"));
    }

    @Test
    void getCopyIsIndependentOfContext() {
        MDC.put("x", "1");
        Map<String, String> copy = MDC.getCopy();
        copy.put("y", "2");
        assertNull(MDC.get("y"));
    }

    @Test
    void getCopyOnEmptyContext() {
        Map<String, String> copy = MDC.getCopy();
        assertNotNull(copy);
        assertTrue(copy.isEmpty());
    }

    @Test
    void getCopyReflectsAllKeys() {
        MDC.put("a", "1");
        MDC.put("b", "2");
        MDC.put("c", "3");
        Map<String, String> copy = MDC.getCopy();
        assertEquals(3, copy.size());
        assertEquals("1", copy.get("a"));
        assertEquals("2", copy.get("b"));
        assertEquals("3", copy.get("c"));
    }

    // --- putAll ---

    @Test
    void putAll() {
        Map<String, String> batch = new HashMap<>();
        batch.put("a", "1");
        batch.put("b", "2");
        MDC.putAll(batch);

        assertEquals("1", MDC.get("a"));
        assertEquals("2", MDC.get("b"));
    }

    @Test
    void putAllNullIsNoOp() {
        MDC.putAll(null);
        assertTrue(MDC.getCopy().isEmpty());
    }

    @Test
    void putAllEmptyMapIsNoOp() {
        MDC.put("existing", "value");
        MDC.putAll(new HashMap<>());
        assertEquals(1, MDC.getCopy().size());
    }

    @Test
    void putAllOverwrites() {
        MDC.put("key", "old");
        Map<String, String> batch = new HashMap<>();
        batch.put("key", "new");
        MDC.putAll(batch);
        assertEquals("new", MDC.get("key"));
    }

    @Test
    void putAllMergesWithExisting() {
        MDC.put("a", "1");
        Map<String, String> batch = new HashMap<>();
        batch.put("b", "2");
        MDC.putAll(batch);
        assertEquals("1", MDC.get("a"));
        assertEquals("2", MDC.get("b"));
    }

    // --- Size limit ---

    @Test
    void sizeLimitThrowsWhenExceeded() {
        for (int i = 0; i < 64; i++) {
            MDC.put("k" + i, "v" + i);
        }
        assertThrows(IllegalStateException.class, () -> MDC.put("extra", "overflow"));
    }

    @Test
    void sizeLimitAllowsOverwriteWithinLimit() {
        for (int i = 0; i < 64; i++) {
            MDC.put("k" + i, "v" + i);
        }
        assertDoesNotThrow(() -> MDC.put("k0", "updated"));
        assertEquals("updated", MDC.get("k0"));
    }

    @Test
    void sizeLimitAllowsRemoveThenPut() {
        for (int i = 0; i < 64; i++) {
            MDC.put("k" + i, "v" + i);
        }
        MDC.remove("k0");
        assertDoesNotThrow(() -> MDC.put("newKey", "newVal"));
        assertEquals("newVal", MDC.get("newKey"));
    }

    // --- Constants ---

    @Test
    void traceIdKeyConstant() {
        assertEquals("traceId", MDC.TRACE_ID_KEY);
    }

    // --- Thread isolation ---

    @Test
    void threadIsolation() throws Exception {
        MDC.put("key", "main");

        Thread t = new Thread(() -> {
            assertNull(MDC.get("key"));
            MDC.put("key", "worker");
            assertEquals("worker", MDC.get("key"));
        });
        t.start();
        t.join();

        assertEquals("main", MDC.get("key"));
    }

    @Test
    void childThreadDoesNotInheritParentContext() throws Exception {
        MDC.put("parentKey", "parentVal");
        MDC.put(MDC.TRACE_ID_KEY, "trace-123");

        String[] childValue = new String[1];
        Thread t = new Thread(() -> childValue[0] = MDC.get("parentKey"));
        t.start();
        t.join();

        assertNull(childValue[0]);
    }

    @Test
    void childThreadContextDoesNotLeakToParent() throws Exception {
        Thread t = new Thread(() -> MDC.put("childKey", "childVal"));
        t.start();
        t.join();

        assertNull(MDC.get("childKey"));
    }

    // --- Thread pool context propagation ---

    @Test
    void contextPropagationViaGetCopyPutAll() throws Exception {
        MDC.put(MDC.TRACE_ID_KEY, "trace-abc");
        MDC.put("userId", "user-1");
        Map<String, String> parentCtx = MDC.getCopy();


        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            String result = executor.submit(() -> {
                assertNull(MDC.get(MDC.TRACE_ID_KEY));
                MDC.putAll(parentCtx);
                return MDC.get(MDC.TRACE_ID_KEY) + "|" + MDC.get("userId");
            }).get(5, TimeUnit.SECONDS);

            assertEquals("trace-abc|user-1", result);
        }
    }

    @Test
    void threadPoolTaskCleanupPreventsLeak() throws Exception {
        MDC.put(MDC.TRACE_ID_KEY, "trace-xyz");
        Map<String, String> parentCtx = MDC.getCopy();

        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
            Runnable task = () -> {
                try {
                    MDC.putAll(parentCtx);
                    assertEquals("trace-xyz", MDC.get(MDC.TRACE_ID_KEY));
                } finally {
                    MDC.clear();
                }
            };

            executor.submit(task).get(5, TimeUnit.SECONDS);
            executor.submit(() -> assertNull(MDC.get(MDC.TRACE_ID_KEY))).get(5, TimeUnit.SECONDS);
        }
    }

    // --- Concurrent stress ---

    @Test
    void concurrentMdcOperations() throws Exception {
        int threadCount = 20;
        int opsPerThread = 100;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger errors = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    barrier.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        String key = "t" + threadId + "k" + i;
                        MDC.put(key, "v" + i);
                        assertEquals("v" + i, MDC.get(key));
                        MDC.remove(key);
                        assertNull(MDC.get(key));
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(0, errors.get());
    }

    @Test
    void concurrentPutGetDifferentKeys() throws Exception {
        int threadCount = 10;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger errors = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final String key = "key-" + t;
            final String value = "value-" + t;
            new Thread(() -> {
                try {
                    barrier.await();
                    MDC.put(key, value);
                    assertEquals(value, MDC.get(key));
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(0, errors.get());
    }

    // --- TraceId lifecycle ---

    @Test
    void traceIdFullLifecycle() {
        assertNull(MDC.get(MDC.TRACE_ID_KEY));

        MDC.put(MDC.TRACE_ID_KEY, "trace-001");
        assertEquals("trace-001", MDC.get(MDC.TRACE_ID_KEY));

        MDC.remove(MDC.TRACE_ID_KEY);
        assertNull(MDC.get(MDC.TRACE_ID_KEY));

        MDC.put(MDC.TRACE_ID_KEY, "trace-002");
        assertEquals("trace-002", MDC.get(MDC.TRACE_ID_KEY));

        MDC.clear();
        assertNull(MDC.get(MDC.TRACE_ID_KEY));
    }

    @Test
    void multipleContextKeysCoexistWithTraceId() {
        MDC.put(MDC.TRACE_ID_KEY, "trace-100");
        MDC.put("userId", "user-x");
        MDC.put("sessionId", "sess-y");

        Map<String, String> copy = MDC.getCopy();
        assertEquals(3, copy.size());
        assertEquals("trace-100", copy.get(MDC.TRACE_ID_KEY));
        assertEquals("user-x", copy.get("userId"));
        assertEquals("sess-y", copy.get("sessionId"));
    }
}
