/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.tachyonmcp.transport.netty.NettyIoEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Guards the {@code netty-native-*} Maven profiles: if they stop putting native transport jars
 * on the runtime classpath, detection silently falls back to NIO and these tests fail.
 *
 * @author Konstantin Pavlov
 */
class NativeTransportDetectionTest {

    @Test
    @EnabledOnOs(OS.MAC)
    void kqueueDetectedOnMac() {
        assumeNativeEnabled();

        assertThat(NettyIoEngine.detect()).isEqualTo(NettyIoEngine.KQUEUE);
        assertThat(NettyIoEngine.KQUEUE.channel().getSimpleName()).isEqualTo("KQueueServerSocketChannel");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void nativeTransportDetectedOnLinux() {
        assumeNativeEnabled();

        assertThat(NettyIoEngine.detect()).isIn(NettyIoEngine.IO_URING, NettyIoEngine.EPOLL);
    }

    private static void assumeNativeEnabled() {
        assumeFalse("false".equals(System.getProperty("netty.native.enabled")));
    }
}
