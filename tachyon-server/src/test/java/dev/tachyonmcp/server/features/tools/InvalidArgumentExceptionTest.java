/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidArgumentExceptionTest {

    @Test
    void shouldStoreArgName() {
        var e = new InvalidArgumentException("my-arg", "some message");
        assertThat(e.argName()).isEqualTo("my-arg");
    }

    @Test
    void shouldStoreMessage() {
        var e = new InvalidArgumentException("my-arg", "some message");
        assertThat(e.getMessage()).isEqualTo("some message");
    }

    @Test
    void isRuntimeException() {
        var e = new InvalidArgumentException("x", "y");
        assertThat(e).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldAcceptEmptyMessage() {
        var e = new InvalidArgumentException("arg", "");
        assertThat(e.getMessage()).isEmpty();
        assertThat(e.argName()).isEqualTo("arg");
    }
}
