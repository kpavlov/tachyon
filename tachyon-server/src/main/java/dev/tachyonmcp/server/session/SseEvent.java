/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import java.util.Objects;

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
