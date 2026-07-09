/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import java.util.Objects;

/** A single Server-Sent Event with an ID, event type, and data payload. */
public record SseEvent(String id, String event, String data) {

    public SseEvent(String id, String event, String data) {
        this.id = Objects.requireNonNull(id, "id");
        this.event = Objects.requireNonNull(event, "event");
        this.data = Objects.requireNonNull(data, "data");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SseEvent(String id1, String event1, String data1))) return false;
        return id.equals(id1) && event.equals(event1) && data.equals(data1);
    }

    @Override
    public String toString() {
        return "SseEvent[id=" + id + ", event=" + event + "]";
    }
}
