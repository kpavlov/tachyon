/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.util.List;
import org.junit.jupiter.api.Test;

class NettyIoEngineTest {

    @Test
    void detectReturnsAvailableEngine() {
        var engine = NettyIoEngine.detect();

        assertThat(engine).isNotNull();
        assertThat(engine.ioHandler()).isNotNull();
        assertThat(engine.channel()).isNotNull();
    }

    @Test
    void nioIsAlwaysAvailable() {
        assertThat(NettyIoEngine.NIO.ioHandler()).isNotNull();
        assertThat(NettyIoEngine.NIO.channel()).isEqualTo(NioServerSocketChannel.class);
    }

    @Test
    void detectIsCached() {
        var a = NettyIoEngine.detect();
        var b = NettyIoEngine.detect();

        assertThat(a).isSameAs(b);
    }

    @Test
    void autoDelegatesToDetect() {
        var autoEngine = NettyIoEngine.detect();

        assertThat(NettyIoEngine.AUTO.ioHandler()).isEqualTo(autoEngine.ioHandler());
        assertThat(NettyIoEngine.AUTO.channel()).isEqualTo(autoEngine.channel());
    }

    @Test
    void detectFallsBackToNioWithoutTransportJars() {
        assertThat(NettyIoEngine.detect()).isEqualTo(NettyIoEngine.NIO);
    }

    @Test
    void nativeEnginesThrowWithReasonWithoutTransportJars() {
        for (var engine : List.of(NettyIoEngine.IO_URING, NettyIoEngine.EPOLL, NettyIoEngine.KQUEUE)) {
            assertThatThrownBy(engine::ioHandler)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining(engine.name())
                    .hasMessageContaining("not available");
            assertThatThrownBy(engine::channel).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
