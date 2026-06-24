/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.builder;

import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.McpServerHandle;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.extensions.McpExtension;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptHandler;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
import dev.tachyonmcp.server.features.tasks.TaskDescriptor;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tasks.TaskState;
import dev.tachyonmcp.server.features.tools.AsyncToolHandler;
import dev.tachyonmcp.server.features.tools.SyncToolHandler;
import dev.tachyonmcp.server.session.SessionLogRouter;
import dev.tachyonmcp.server.session.SessionStore;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;

/**
 * Step: configure capabilities (tools, resources, prompts enabled flags).
 * Call identity methods to go back, or feature/session/networks methods to transition onward.
 */
public final class CapabilitiesStep {

    final BuilderState state;

    CapabilitiesStep(BuilderState state) {
        this.state = state;
    }

    // === Capabilities ===

    public CapabilitiesStep toolsEnabled(boolean listChanged) {
        state.toolsListChanged = listChanged;
        return this;
    }

    public CapabilitiesStep resourcesEnabled(boolean subscribe, boolean listChanged) {
        state.resourcesSubscribe = subscribe;
        state.resourcesListChanged = listChanged;
        return this;
    }

    public CapabilitiesStep promptsEnabled(boolean listChanged) {
        state.promptsListChanged = listChanged;
        return this;
    }

    // === Features (transition) ===

    public ExtensionsStep extension(McpExtension extension) {
        state.extensions.add(extension);
        return new ExtensionsStep(state);
    }

    public FeatureStep tool(SyncToolHandler<?, ?> handler) {
        state.tools.add(handler);
        return new FeatureStep(state);
    }

    public FeatureStep tool(AsyncToolHandler<?, ?> handler) {
        state.tools.add(handler);
        return new FeatureStep(state);
    }

    public FeatureStep resource(ResourceDescriptor descriptor) {
        state.resources.add(new BuilderState.ResourceRegistration(descriptor, null));
        return new FeatureStep(state);
    }

    public FeatureStep resource(ResourceDescriptor descriptor, ResourceHandler handler) {
        state.resources.add(new BuilderState.ResourceRegistration(descriptor, handler));
        return new FeatureStep(state);
    }

    public FeatureStep prompt(PromptDescriptor descriptor, List<PromptMessage> messages) {
        state.prompts.add(new BuilderState.PromptRegistration(descriptor, args -> messages));
        return new FeatureStep(state);
    }

    public FeatureStep prompt(PromptDescriptor descriptor, PromptHandler handler) {
        state.prompts.add(new BuilderState.PromptRegistration(descriptor, handler));
        return new FeatureStep(state);
    }

    public FeatureStep task(TaskDescriptor descriptor) {
        var id = java.util.UUID.randomUUID().toString();
        state.tasks.add(new TaskEntry(descriptor, id, TaskState.WORKING, 0.0));
        return new FeatureStep(state);
    }

    public FeatureStep task(TaskEntry entry) {
        state.tasks.add(entry);
        return new FeatureStep(state);
    }

    // === Session (transition) ===

    public SessionStep stateless(boolean stateless) {
        state.stateless = stateless;
        return new SessionStep(state);
    }

    public SessionStep sessionLogRouter(SessionLogRouter router) {
        state.sessionLogRouter = router;
        return new SessionStep(state);
    }

    public SessionStep sessionTtl(Duration sessionTtl) {
        state.sessionTtl = sessionTtl;
        return new SessionStep(state);
    }

    public SessionStep sessionStore(SessionStore sessionStore) {
        state.sessionStore = sessionStore;
        return new SessionStep(state);
    }

    // === Networks (transition) ===

    public NetworksStep endpointPath(String endpointPath) {
        state.endpointPath = endpointPath;
        return new NetworksStep(state);
    }

    public NetworksStep readerIdleTimeout(Duration timeout) {
        state.readerIdleTimeout = timeout;
        return new NetworksStep(state);
    }

    public NetworksStep writerIdleTimeout(Duration timeout) {
        state.writerIdleTimeout = timeout;
        return new NetworksStep(state);
    }

    public NetworksStep host(String host) {
        if (state.addressExplicitlySet) {
            throw new IllegalStateException("Cannot combine host() with address()");
        }
        state.host = host;
        state.hostPortExplicitlySet = true;
        return new NetworksStep(state);
    }

    public NetworksStep port(int port) {
        if (state.addressExplicitlySet) {
            throw new IllegalStateException("Cannot combine port() with address()");
        }
        state.port = port;
        state.hostPortExplicitlySet = true;
        return new NetworksStep(state);
    }

    public NetworksStep address(SocketAddress addr) {
        if (state.hostPortExplicitlySet) {
            throw new IllegalStateException("Cannot combine address() with host()/port()");
        }
        if (addr instanceof InetSocketAddress inet) {
            state.host = inet.getHostString();
            state.port = inet.getPort();
        }
        state.addressExplicitlySet = true;
        return new NetworksStep(state);
    }

    // === Terminal ===

    public McpServer build() {
        return state.build();
    }

    public McpServerHandle bind() {
        return state.bind();
    }
}
