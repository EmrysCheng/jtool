package io.github.emryschegn.jtool.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogFormatterTest {

    @Test
    void abbreviateSimpleClassName() {
        assertEquals("MyClass", LogFormatter.abbreviate("MyClass"));
    }

    @Test
    void abbreviateSinglePackage() {
        assertEquals("c.MyClass", LogFormatter.abbreviate("com.MyClass"));
    }

    @Test
    void abbreviateMultiPackage() {
        assertEquals("c.d.s.UserServiceImpl", LogFormatter.abbreviate("com.demo.service.UserServiceImpl"));
    }

    @Test
    void abbreviateDeepPackage() {
        assertEquals("i.g.e.j.l.LogFormatter", LogFormatter.abbreviate("io.github.emryschegn.jtool.log.LogFormatter"));
    }

    @Test
    void abbreviateSingleCharSegments() {
        assertEquals("a.b.c.X", LogFormatter.abbreviate("a.b.c.X"));
    }

    @Test
    void abbreviateEmptyString() {
        assertEquals("", LogFormatter.abbreviate(""));
    }

    @Test
    void abbreviateTrailingDot() {
        assertEquals("c.", LogFormatter.abbreviate("com."));
    }
}
