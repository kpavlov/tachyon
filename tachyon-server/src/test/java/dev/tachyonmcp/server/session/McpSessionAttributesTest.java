/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

class McpSessionAttributesTest {

    @Test
    void enableAndCheckExtension() {
        var session = new McpSession("s1", new TestConnection());
        session.enableExtension("test/ext1");
        assertThat(session.isExtensionEnabled("test/ext1")).isTrue();
    }

    @Test
    void unenableExtensionReturnsFalse() {
        var session = new McpSession("s2", new TestConnection());
        assertThat(session.isExtensionEnabled("test/ext2")).isFalse();
    }

    @Test
    void extensionsAreIndependent() {
        var session = new McpSession("s3", new TestConnection());
        session.enableExtension("ext/a");
        session.enableExtension("ext/b");
        assertThat(session.isExtensionEnabled("ext/a")).isTrue();
        assertThat(session.isExtensionEnabled("ext/b")).isTrue();
        assertThat(session.isExtensionEnabled("ext/c")).isFalse();
    }

    private static class TestConnection implements SseConnection {

        @Override
        public boolean isWritable() {
            return true;
        }

        @Override
        public void send(@NonNull SseEvent event) {}
    }
}
