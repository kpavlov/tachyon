/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.domain.Args;
import org.junit.jupiter.api.Test;

class PromptRequestTest {

    @Test
    void shouldNormalizeNullArgumentsToEmpty() {
        var request = new PromptRequest(null, null, null);

        assertThat(request.arguments()).isEqualTo(Args.empty());
    }
}
