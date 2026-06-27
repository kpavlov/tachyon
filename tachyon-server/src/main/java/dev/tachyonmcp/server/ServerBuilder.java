/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.config.*;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.extensions.McpExtension;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptHandler;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
import dev.tachyonmcp.server.features.tools.AsyncToolHandler;
import dev.tachyonmcp.server.features.tools.SyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.session.InMemorySessionLogRouter;
import dev.tachyonmcp.server.session.InMemorySessionStore;
import dev.tachyonmcp.transport.netty.NettyServer;
import dev.tachyonmcp.transport.netty.NettyServerConfig;
import io.netty.channel.ChannelPipeline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class ServerBuilder {

    private final ServerIdentityBuilder identityBuilder =
            ServerIdentity.builder().from(ServerIdentity.DEFAULT);
    private final CapabilitiesConfig.Builder capabilitiesConfig = CapabilitiesConfig.builder();
    private final FeaturesConfig featuresConfig = new FeaturesConfig();
    private final SessionConfig.Builder sessionBuilder = SessionConfig.builder();
    private final NetworkConfig.Builder networkBuilder = NetworkConfig.builder();

    @Nullable
    Consumer<ChannelPipeline> pipelineCustomizer;

    ServerBuilder() {}

    // === Configuration groups ===

    public ServerBuilder info(Consumer<ServerIdentityBuilder> configurer) {
        configurer.accept(identityBuilder);
        return this;
    }

    public ServerBuilder capabilities(Consumer<CapabilitiesConfig.Builder> configurer) {
        configurer.accept(capabilitiesConfig);
        return this;
    }

    public ServerBuilder session(Consumer<SessionConfig.Builder> configurer) {
        configurer.accept(sessionBuilder);
        return this;
    }

    public ServerBuilder network(Consumer<NetworkConfig.Builder> configurer) {
        configurer.accept(networkBuilder);
        return this;
    }

    public ServerBuilder name(String name) {
        identityBuilder.name(name);
        return this;
    }

    public ServerBuilder port(int port) {
        networkBuilder.port(port);
        return this;
    }

    // === Feature registration ===

    public ServerBuilder tool(ToolHandler handler) {
        featuresConfig.tools.add(handler);
        return this;
    }

    public ServerBuilder tool(SyncToolHandler handler) {
        featuresConfig.tools.add(handler);
        return this;
    }

    public ServerBuilder tool(AsyncToolHandler handler) {
        featuresConfig.tools.add(handler);
        return this;
    }

    public ServerBuilder resource(ResourceDescriptor descriptor) {
        featuresConfig.resources.add(new FeaturesConfig.ResourceRegistration(descriptor, null));
        return this;
    }

    public ServerBuilder resource(ResourceDescriptor descriptor, ResourceHandler handler) {
        featuresConfig.resources.add(new FeaturesConfig.ResourceRegistration(descriptor, handler));
        return this;
    }

    public ServerBuilder prompt(PromptDescriptor descriptor, List<PromptMessage> messages) {
        featuresConfig.prompts.add(new FeaturesConfig.PromptRegistration(descriptor, args -> messages));
        return this;
    }

    public ServerBuilder prompt(PromptDescriptor descriptor, PromptHandler handler) {
        featuresConfig.prompts.add(new FeaturesConfig.PromptRegistration(descriptor, handler));
        return this;
    }

    public ServerBuilder extension(McpExtension extension) {
        featuresConfig.extensions.add(extension);
        return this;
    }

    public ServerBuilder jsonSchemaValidator(JsonSchemaValidator validator) {
        featuresConfig.jsonSchemaValidator = validator;
        return this;
    }

    // === Transport escape hatch ===

    public ServerBuilder pipelineCustomizer(@Nullable Consumer<ChannelPipeline> customizer) {
        this.pipelineCustomizer = customizer;
        return this;
    }

    // === Terminal methods ===

    public McpServer build() {
        var sessionConfig = sessionBuilder.build();
        var router = sessionConfig.sessionLogRouter() != null
                ? sessionConfig.sessionLogRouter()
                : new InMemorySessionLogRouter();
        var store = sessionConfig.sessionStore() != null ? sessionConfig.sessionStore() : new InMemorySessionStore();
        var allExtensions = Collections.unmodifiableList(featuresConfig.extensions);
        var serverConfig = buildConfig();
        var server = new McpServer(router, store, serverConfig, featuresConfig.jsonSchemaValidator, allExtensions);
        featuresConfig.tools.forEach(server::registerTool);
        featuresConfig.resources.forEach(r -> {
            var d = r.descriptor();
            server.resources()
                    .add(
                            d,
                            r.handler() != null
                                    ? r.handler()
                                    : (ctx, req) -> TextResourceContents.of(d.uri(), d.mimeType(), "", null));
        });
        featuresConfig.prompts.forEach(p -> server.prompts().add(p.descriptor(), p.handler()));
        return server;
    }

    public McpServerHandle bind() {
        var networkConfig = networkBuilder.build();
        if (networkConfig.port() < 0) {
            throw new IllegalStateException("Port must be set before bind()");
        }
        var server = build();
        var nettyConfig = new NettyServerConfig(
                networkConfig.host(),
                networkConfig.port(),
                networkConfig.endpointPath(),
                networkConfig.readerIdleTimeout(),
                networkConfig.writerIdleTimeout(),
                networkConfig.maxContentLength(),
                NettyServerConfig.buildCorsConfig(
                        networkConfig.allowedOrigins(),
                        networkConfig.allowNullOrigin(),
                        networkConfig.allowPrivateNetworks(),
                        networkConfig.allowedHeaders()),
                pipelineCustomizer);
        var netty = new NettyServer(server, nettyConfig);
        return new McpServerHandle(server, netty.port(), netty);
    }

    public ServerConfig buildConfig() {
        return new ServerConfig(
                identityBuilder.build(), capabilitiesConfig.build(), sessionBuilder.build(), networkBuilder.build());
    }

    static final class FeaturesConfig {

        final List<ToolHandler> tools = new ArrayList<>();
        final List<ResourceRegistration> resources = new ArrayList<>();
        final List<PromptRegistration> prompts = new ArrayList<>();
        final List<McpExtension> extensions = new ArrayList<>();

        JsonSchemaValidator jsonSchemaValidator = new NetworkntJsonSchemaValidator();

        record PromptRegistration(PromptDescriptor descriptor, PromptHandler handler) {}

        record ResourceRegistration(
                ResourceDescriptor descriptor, @Nullable ResourceHandler handler) {}
    }
}
