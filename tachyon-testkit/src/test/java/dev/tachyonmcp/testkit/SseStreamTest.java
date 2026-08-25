/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Verifies {@link SseStream}'s wire-frame parsing against a raw loopback socket. */
class SseStreamTest {

    @Test
    void joinsMultiLineDataIntoOneFrameDispatchedOnBlankLine() throws Exception {
        try (var server = new ServerSocket(0)) {
            var endpoint = URI.create("http://localhost:" + server.getLocalPort() + "/mcp");
            try (var stream = new SseStream(endpoint, "session-1", null, "2025-11-25")) {
                stream.start();
                try (var accepted = server.accept()) {
                    consumeRequestHeaders(accepted);
                    var out = accepted.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n" + "id: 1\n" + "event: mess")
                            .getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Thread.sleep(20); // force the "event:" line to arrive split across two socket reads
                    out.write(("age\n" + "data: line one\n" + "data: line two\n" + "\n")
                            .getBytes(StandardCharsets.UTF_8));
                    out.flush();

                    var frame = stream.await(f -> true, Duration.ofSeconds(2));
                    assertThat(frame.id()).isEqualTo("1");
                    assertThat(frame.eventType()).isEqualTo("message");
                    assertThat(frame.data()).isEqualTo("line one\nline two");
                    assertThat(stream.received(f -> true))
                            .as("multi-line data must dispatch as exactly one frame")
                            .hasSize(1);
                }
            }
        }
    }

    private static void consumeRequestHeaders(Socket socket) throws Exception {
        var in = socket.getInputStream();
        var tail = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            tail.append((char) b);
            if (tail.length() > 4) tail.deleteCharAt(0);
            if ("\r\n\r\n".contentEquals(tail)) return;
        }
        throw new AssertionError("connection closed before request headers completed");
    }
}
