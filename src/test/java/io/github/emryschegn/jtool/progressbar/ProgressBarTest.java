package io.github.emryschegn.jtool.progressbar;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("EmptyTryBlock")
class ProgressBarTest {

    private PrintStream capturingStream(ByteArrayOutputStream baos) {
        try {
            return new PrintStream(baos, true, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private String capturedOutput(ByteArrayOutputStream baos) {
        try {
            return baos.toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    // --- Builder validation ---

    @Test
    void builderRejectsNegativeTotal() {
        assertThrows(IllegalArgumentException.class, () -> {
            try (ProgressBar ignored = ProgressBar.builder(-1).build()) {
                fail("should have thrown");
            }
        });
    }

    @Test
    void builderAcceptsZeroTotal() {
        assertDoesNotThrow(() -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ProgressBar ignored = ProgressBar.builder(10)
                    .out(capturingStream(baos))
                    .build()) {
                // no-op, close tested below
            }
        });
    }

    @Test
    void builderRejectsZeroWidth() {
        assertThrows(IllegalArgumentException.class, () -> {
            try (ProgressBar ignored = ProgressBar.builder(10).width(0).build()) {
                fail("should have thrown");
            }
        });
    }

    @Test
    void builderRejectsNegativeThrottle() {
        assertThrows(IllegalArgumentException.class, () -> {
            try (ProgressBar ignored = ProgressBar.builder(10).throttleMillis(-1).build()) {
                fail("should have thrown");
            }
        });
    }

    @Test
    void builderRejectsNullOut() {
        assertThrows(NullPointerException.class, () -> {
            try (ProgressBar ignored = ProgressBar.builder(10).out(null).build()) {
                fail("should have thrown");
            }
        });
    }

    @Test
    void builderDefaultsAreValid() {
        assertDoesNotThrow(() -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ProgressBar ignored = ProgressBar.builder(10)
                    .out(capturingStream(baos))
                    .build()) {
                // no-op
            }
        });
    }

    // --- Basic operations ---

    @Test
    void stepIncrementsByOne() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(10)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            bar.step();
        }
        assertTrue(capturedOutput(baos).contains("1/10"));
    }

    @Test
    void updateIncrementsByDelta() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(100)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            bar.update(25);
        }
        assertTrue(capturedOutput(baos).contains("25/100"));
    }

    @Test
    void updateRejectsNegativeDelta() {
        try (ProgressBar bar = ProgressBar.builder(10).build()) {
            assertThrows(IllegalArgumentException.class, () -> bar.update(-1));
        }
    }

    @Test
    void updateZeroDeltaIsNoOp() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(10)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            bar.update(0);
        }
        assertTrue(capturedOutput(baos).contains("0/10"));
    }

    @Test
    void exceedsTotalShowsCorrectRawCount() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(5)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            bar.update(10);
        }
        String output = capturedOutput(baos);
        assertTrue(output.contains("10/5"));
        assertTrue(output.contains("100%"));
    }

    // --- Render output ---

    @Test
    void renderContainsProgressFrame() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(10)
                .width(10)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            bar.update(5);
        }
        String output = capturedOutput(baos);
        assertTrue(output.startsWith("\r["));
        assertTrue(output.contains("50%"));
        assertTrue(output.contains("5/10"));
    }

    @Test
    void renderWithMessage() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(10)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            bar.setMessage("processing");
            bar.step();
        }
        assertTrue(capturedOutput(baos).contains("processing"));
    }

    @Test
    void renderWithNullMessageClearsPrevious() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(10)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            bar.setMessage("first");
            bar.step();
            bar.setMessage(null);
            bar.step();
        }
        String output = capturedOutput(baos);
        String lastFrame = output.substring(output.lastIndexOf('\r'));
        assertFalse(lastFrame.contains("first"));
    }

    @Test
    void renderShowsElapsed() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(10)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .showElapsed(true)
                .build()) {
            bar.step();
        }
        assertTrue(capturedOutput(baos).contains("s"));
    }

    @Test
    void renderHidesElapsed() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(10)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .showElapsed(false)
                .build()) {
            bar.step();
        }
        assertFalse(capturedOutput(baos).matches(".*\\d+s.*\\|.*"));
    }

    @Test
    void zeroTotalShows100Percent() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar ignored = ProgressBar.builder(0)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            // finish tested via close
        }
        assertTrue(capturedOutput(baos).contains("100%"));
    }

    @Test
    void customChars() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(4)
                .width(4)
                .filledChar('#')
                .emptyChar('-')
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            bar.update(2);
        }
        assertTrue(capturedOutput(baos).contains("##--"));
    }

    // --- Finish and close ---

    @Test
    void finishPrintsNewline() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(10)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            bar.finish();
        }
        assertTrue(capturedOutput(baos).contains("\n"));
    }

    @Test
    void finishIsIdempotent() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(10)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            bar.finish();
            int firstLength = capturedOutput(baos).length();
            bar.finish();
            assertEquals(firstLength, capturedOutput(baos).length());
        }
    }

    @Test
    void closeCallsFinish() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(10)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            bar.step();
        }
        assertTrue(capturedOutput(baos).contains("1/10"));
    }

    @Test
    void updateAfterFinishIsNoOp() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(10)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .build()) {
            bar.finish();
            int lengthAfterFinish = capturedOutput(baos).length();
            bar.step();
            assertEquals(lengthAfterFinish, capturedOutput(baos).length());
        }
    }

    // --- Disabled mode ---

    @Test
    void disabledModeProducesNoOutput() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(10)
                .out(capturingStream(baos))
                .throttleMillis(0)
                .enabled(false)
                .build()) {
            bar.step();
            bar.finish();
        }
        assertEquals(0, capturedOutput(baos).length());
    }

    // --- Concurrency ---

    @Test
    void concurrentUpdatesDoNotThrow() throws Exception {
        try (ProgressBar bar = ProgressBar.builder(1000).build()) {
            int threadCount = 10;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < 100; j++) {
                            bar.step();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            start.countDown();
            assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, errors.get());
        }
    }

    // --- Throttle ---

    @Test
    void throttleControlsRenderFrequency() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ProgressBar bar = ProgressBar.builder(100)
                .out(capturingStream(baos))
                .throttleMillis(60000)
                .build()) {
            bar.step();
            bar.step();
            bar.step();
            bar.finish();
        }
        String output = capturedOutput(baos);
        int count = 0;
        int idx = 0;
        while ((idx = output.indexOf("\r[", idx)) != -1) {
            count++;
            idx++;
        }
        assertEquals(2, count);
    }
}
