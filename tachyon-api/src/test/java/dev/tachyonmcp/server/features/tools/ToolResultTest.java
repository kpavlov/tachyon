/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.domain.UrlInputRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolResultTest {

    @Test
    void blocksWithNoArgsIsEmptySuccess() {
        var r = ToolResult.blocks();
        assertThat(r).isInstanceOf(ToolResult.Success.class);
        assertThat(((ToolResult.Success) r).content()).isEmpty();
    }

    @Test
    void successAllowsNullStructuredAndEmptyContent() {
        var r = new ToolResult.Success(null, List.of());
        assertThat(r.structured()).isEmpty();
        assertThat(r.content()).isEmpty();
    }

    @Test
    void successWithStructuredAndNoContentIsAllowed() {
        var r = new ToolResult.Success("data", List.of());
        assertThat(r.structured()).contains("data");
        assertThat(r.content()).isEmpty();
    }

    @Test
    void textFactoryProducesTextContentBlock() {
        var r = ToolResult.text("hello");
        assertThat(r).isInstanceOf(ToolResult.Success.class);
        var s = (ToolResult.Success) r;
        assertThat(s.content()).hasSize(1);
        assertThat(((TextContent) s.content().getFirst()).text()).isEqualTo("hello");
        assertThat(s.structured()).isEmpty();
    }

    @Test
    void withMetaMergesNotNests() {
        var base = ToolResult.text("x").withMeta("a", 1);
        var merged = base.withMeta("b", 2);

        assertThat(merged).isInstanceOf(ToolResult.WithMeta.class);
        var wm = (ToolResult.WithMeta) merged;
        assertThat(wm.inner()).isNotInstanceOf(ToolResult.WithMeta.class);
        assertThat(wm.meta()).containsKey("a").containsKey("b");
    }

    @Test
    void withMetaKeyOverridesMerges() {
        var base = ToolResult.text("x").withMeta("k", 1);
        var updated = base.withMeta("k", 99);

        var wm = (ToolResult.WithMeta) updated;
        assertThat(wm.meta().get("k")).isEqualTo(99);
    }

    @Test
    void withMetaEmptyMapReturnsThis() {
        var r = ToolResult.text("x");
        assertThat(r.withMeta(Map.of())).isSameAs(r);
    }

    @Test
    void withMetaImmutability() {
        var source = new HashMap<String, Object>();
        source.put("k", 1);
        var r = ToolResult.text("x").withMeta(source);
        source.put("injected", 99);
        var wm = (ToolResult.WithMeta) r;
        assertThat(wm.meta()).doesNotContainKey("injected");
    }

    @Test
    void successContentIsDefensiveCopy() {
        var list = new java.util.ArrayList<dev.tachyonmcp.server.domain.ContentBlock>();
        list.add(TextContent.of("a"));
        var r = new ToolResult.Success(null, list);
        list.add(TextContent.of("b"));
        assertThat(r.content()).hasSize(1);
    }

    @Test
    void errorIsErrorResult() {
        ToolResult err = ToolResult.error("boom");

        assertThat(err).isInstanceOf(ToolResult.Error.class).hasFieldOrPropertyWithValue("message", "boom");
    }

    @Test
    void emptyIsSuccessWithoutContent() {
        ToolResult empty = ToolResult.empty();

        assertThat(empty).isInstanceOf(ToolResult.Success.class);
        var success = (ToolResult.Success) empty;
        assertThat(success.structuredValue()).isNull();
        assertThat(success.content()).isEmpty();
    }

    @Test
    void failureCanCarryMeta() {
        ToolResult err = ToolResult.error("oops").withMeta("trace", "id-1");
        assertThat(err).isInstanceOf(ToolResult.WithMeta.class);
        var wm = (ToolResult.WithMeta) err;
        assertThat(wm.inner()).isInstanceOf(ToolResult.Error.class);
        assertThat(wm.meta().get("trace")).isEqualTo("id-1");
    }

    @Test
    void inputRequiredFactory() {
        var req = new LinkedHashMap<String, InputRequest>();
        req.put("field", UrlInputRequest.of("authenticate", "elic-1", "https://example.com/auth"));
        var r = ToolResult.inputRequired(req, "state-1");
        assertThat(r).isInstanceOf(ToolResult.InputRequired.class);
        var ir = (ToolResult.InputRequired) r;
        assertThat(ir.inputRequests()).containsOnlyKeys("field");
        assertThat(ir.requestState()).isEqualTo("state-1");
    }

    @Test
    void inputRequiredWithNullState() {
        var req = Map.<String, dev.tachyonmcp.server.domain.InputRequest>of();
        var r = ToolResult.inputRequired(req, null);
        assertThat(r).isInstanceOf(ToolResult.InputRequired.class);
        assertThat(((ToolResult.InputRequired) r).requestState()).isNull();
    }

    @Test
    void ofFactoryWithPayload() {
        ToolResult r = ToolResult.of(42);
        assertThat(r).isInstanceOf(ToolResult.Success.class);
        var s = (ToolResult.Success) r;
        assertThat(s.structured()).contains(42);
        assertThat(s.content()).isEmpty();
    }

    @Test
    void ofFactoryWithPayloadAndText() {
        ToolResult r = ToolResult.of("data", "custom text");
        assertThat(r).isInstanceOf(ToolResult.Success.class);
        var s = (ToolResult.Success) r;
        assertThat(s.structured()).contains("data");
        assertThat(((dev.tachyonmcp.server.domain.TextContent) s.content().getFirst()).text())
                .isEqualTo("custom text");
    }
}
