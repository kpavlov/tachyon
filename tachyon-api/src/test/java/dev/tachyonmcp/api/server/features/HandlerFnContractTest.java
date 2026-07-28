/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.features.completions.AsyncCompletionFn;
import dev.tachyonmcp.api.server.features.completions.CompletionFn;
import dev.tachyonmcp.api.server.features.prompts.AsyncPromptFn;
import dev.tachyonmcp.api.server.features.prompts.PromptFn;
import dev.tachyonmcp.api.server.features.resources.AsyncResourceFn;
import dev.tachyonmcp.api.server.features.resources.ResourceFn;
import org.junit.jupiter.api.Test;

class HandlerFnContractTest {

    @Test
    void shouldKeepSyncAndAsyncFunctionsIndependent() {
        assertThat(ResourceFn.class.isAssignableFrom(AsyncResourceFn.class)).isFalse();
        assertThat(PromptFn.class.isAssignableFrom(AsyncPromptFn.class)).isFalse();
        assertThat(CompletionFn.class.isAssignableFrom(AsyncCompletionFn.class)).isFalse();
    }
}
