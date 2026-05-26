package io.github.emryschegn.jtool.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JulBootstrapTest {

    @Test
    void logDirConstant() {
        assertEquals("logs", JulBootstrap.LOG_DIR);
    }

    @Test
    void initIsIdempotent() {
        assertDoesNotThrow(() -> {
            JulBootstrap.init();
            JulBootstrap.init();
        });
    }
}
