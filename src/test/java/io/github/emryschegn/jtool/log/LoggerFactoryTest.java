package io.github.emryschegn.jtool.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoggerFactoryTest {

    @Test
    void getLoggerReturnsNonNull() {
        Log log = LoggerFactory.getLogger(LoggerFactoryTest.class);
        assertNotNull(log);
    }

    @Test
    void getLoggerCachesInstances() {
        Log a = LoggerFactory.getLogger(LoggerFactoryTest.class);
        Log b = LoggerFactory.getLogger(LoggerFactoryTest.class);
        assertSame(a, b);
    }

    @Test
    void getLoggerReturnsDifferentForDifferentClass() {
        Log a = LoggerFactory.getLogger(LoggerFactoryTest.class);
        Log b = LoggerFactory.getLogger(String.class);
        assertNotSame(a, b);
    }
}
