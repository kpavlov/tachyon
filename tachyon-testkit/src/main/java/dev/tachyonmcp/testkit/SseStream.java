/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import me.kpavlov.finchly.queue.MessageAggregator;
import me.kpavlov.finchly.queue.QueueSubscriber;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;

/**
 * Raw socket GET against a Tachyon MCP server's Streamable HTTP endpoint — for reconnect /
 * {@code Last-Event-ID} scenarios {@link java.net.http.HttpClient}'s higher-level, {@code Stream}-
 * based body handling doesn't give fine enough control over: bounded polling reads, and an
 * explicit mid-stream close to simulate a dropped connection.
 *
 * <p>A {@link QueueSubscriber} for this transport: {@link #start()} opens the socket and a
 * background virtual thread parses arriving SSE frames, delivering each to the underlying {@link
 * MessageAggregator} so callers {@link #await} a specific frame the same way {@link
 * McpClient#awaitNotification} awaits a notification.
 */
public final class SseStream extends QueueSubscriber<SseFrame> implements AutoCloseable {

    private static final Pattern ID_LINE = Pattern.compile("id:\\s?(.*)");
    private static final Pattern EVENT_LINE = Pattern.compile("event:\\s?(.*)");
    private static final Pattern DATA_LINE = Pattern.compile("data:\\s?(.*)");

    private final int port;
    private final String sessionId;
    private final @Nullable String lastEventId;
    private final String protocolVersion;

    private @Nullable Socket socket;
    private final StringBuilder rawResponse = new StringBuilder();
    private volatile boolean stopped;

    /**
     * Opens against {@code /mcp} on {@code localhost:port}, with its own {@link MessageAggregator}.
     */
    SseStream(int port, String sessionId, @Nullable String lastEventId, String protocolVersion) {
        this(port, sessionId, lastEventId, protocolVersion, new MessageAggregator<>());
    }

    SseStream(
            int port,
            String sessionId,
            @Nullable String lastEventId,
            String protocolVersion,
            MessageAggregator<SseFrame> aggregator) {
        super(aggregator);
        this.port = port;
        this.sessionId = sessionId;
        this.lastEventId = lastEventId;
        this.protocolVersion = protocolVersion;
    }

    /**
     * Opens the socket, sends the GET request, and starts the background frame-parsing thread.
     */
    @Override
    public void start() {
        try {
            socket = new Socket("localhost", port);
            var req = new StringBuilder("GET /mcp HTTP/1.1\r\n")
                    .append("Host: localhost:")
                    .append(port)
                    .append("\r\n")
                    .append("MCP-Session-Id: ")
                    .append(sessionId)
                    .append("\r\n")
                    .append("MCP-Protocol-Version: ")
                    .append(protocolVersion)
                    .append("\r\n")
                    .append("Accept: text/event-stream\r\n");
            if (lastEventId != null) {
                req.append("Last-Event-ID: ").append(lastEventId).append("\r\n");
            }
            req.append("\r\n");
            socket.getOutputStream().write(req.toString().getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            socket.setSoTimeout(50);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open SSE stream to localhost:" + port, e);
        }
        Thread.ofVirtual().start(this::readLoop);
    }

    /**
     * Closes the socket, simulating a dropped connection, and stops the background reader.
     */
    @Override
    public void stop() {
        stopped = true;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best-effort close; nothing more to do with a socket we're discarding.
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Awaits an SSE frame matching {@code predicate}, or fails after {@code timeout}.
     */
    public SseFrame await(Predicate<SseFrame> predicate, Duration timeout) {
        return aggregator.awaitMessage(timeout, false, predicate);
    }

    /**
     * Awaits the first frame carrying an eventType id — the priming eventType a fresh GET stream sends.
     */
    public String awaitFirstEventId(Duration timeout) {
        return Objects.requireNonNull(await(f -> f.id() != null, timeout).id());
    }

    /**
     * Waits out {@code window} and asserts no frame matching {@code predicate} arrived in it.
     * Unlike {@link #await}, absence can't be confirmed faster than waiting the full window —
     * this isn't a lazy sleep standing in for a pollable condition, it <em>is</em> the condition.
     */
    public void assertNoneArrive(Predicate<SseFrame> predicate, Duration window) {
        try {
            Thread.sleep(window);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        var unexpected = aggregator.findAll(predicate);
        if (!unexpected.isEmpty()) {
            throw new AssertionError("Expected no matching SSE frame within " + window + ", but got: " + unexpected);
        }
    }

    /**
     * All frames delivered so far matching {@code predicate}, without waiting.
     */
    public List<SseFrame> received(Predicate<SseFrame> predicate) {
        return aggregator.findAll(predicate);
    }

    /**
     * The raw response bytes accumulated so far (status line, headers, and any SSE body), as text.
     */
    public synchronized String rawResponse() {
        return rawResponse.toString();
    }

    /**
     * Polls {@link #rawResponse()} until {@code predicate} holds, or fails after {@code timeout}.
     * For plain, non-SSE-framed responses (a 4xx status and body) that never deliver a frame, so
     * {@link #await} has nothing to wait on.
     */
    public String awaitRawResponse(Predicate<String> predicate, Duration timeout) {
        return Awaitility.await()
                .atMost(timeout)
                .pollDelay(Duration.ofMillis(20))
                .pollInterval(Duration.ofMillis(50))
                .until(this::rawResponse, predicate::test);
    }

    private void readLoop() {
        var socket = Objects.requireNonNull(this.socket, "start() must run before the reader thread");
        var buf = new byte[1024];
        var lineBuf = new StringBuilder();
        String pendingId = null;
        String pendingEvent = null;
        while (!stopped) {
            try {
                var n = socket.getInputStream().read(buf);
                if (n < 0) break;
                if (n == 0) continue;
                var chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                synchronized (this) {
                    rawResponse.append(chunk);
                }
                lineBuf.append(chunk);
                int newline;
                while ((newline = lineBuf.indexOf("\n")) >= 0) {
                    var line = lineBuf.substring(0, newline).stripTrailing();
                    lineBuf.delete(0, newline + 1);
                    var idMatcher = ID_LINE.matcher(line);
                    var eventMatcher = EVENT_LINE.matcher(line);
                    var dataMatcher = DATA_LINE.matcher(line);
                    if (idMatcher.matches()) {
                        pendingId = idMatcher.group(1);
                    } else if (eventMatcher.matches()) {
                        pendingEvent = eventMatcher.group(1);
                    } else if (dataMatcher.matches()) {
                        deliver(new SseFrame(pendingId, pendingEvent, dataMatcher.group(1)));
                        pendingId = null;
                        pendingEvent = null;
                    }
                }
            } catch (SocketTimeoutException e) {
                // No data this poll; keep reading until stopped, or the socket closes/errors.
            } catch (IOException e) {
                break;
            }
        }
    }
}
