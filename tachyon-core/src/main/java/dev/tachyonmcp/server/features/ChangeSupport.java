/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features;

import dev.tachyonmcp.annotations.InternalApi;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Change-notification for registries: a list of listeners run when contents change. Composed by
 * registries that don't share the {@link AbstractRegistry} base, so the listener boilerplate lives once.
 *
 * @author Konstantin Pavlov
 */
@InternalApi
public final class ChangeSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeSupport.class);

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** Creates a new change support with an empty listener list. */
    public ChangeSupport() {}

    /**
     * Registers a callback invoked when the registry contents change.
     *
     * @param callback the callback to invoke on change
     */
    public void onChange(Runnable callback) {
        listeners.add(callback);
    }

    /**
     * Runs every registered listener.
     */
    public void fireOnChange() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                // log and continue so other listeners still receive the notification
                LOGGER.debug("Can't notify listener: {}", listener, e);
            }
        }
    }
}
