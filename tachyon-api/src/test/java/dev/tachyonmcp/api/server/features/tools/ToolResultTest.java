/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.domain.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolResultTest {

    @Test
    void contentWithNoArgsIsEmptySuccess() {
        var r = ToolResult.content();
        assertThat(r).isInstanceOf(ToolResult.Success.class);
        assertThat(((ToolResult.Success) r).content()).isEmpty();
    }

    @Test
    void contentFactoryCarriesImageAndAudioBlocks() {
        var bytes = new byte[] {3, 2, 1};
        var image = ImageContent.of(bytes, "image/png");
        var audio = AudioContent.of(bytes, "audio/wav");
        var result = ToolResult.content(image, audio);

        assertThat(image).isEqualTo(ImageContent.of(bytes, "image/png"));
        assertThat(audio).isEqualTo(AudioContent.of(bytes, "audio/wav"));
        assertThat(((ToolResult.Success) result).content()).containsExactly(image, audio);
    }

    @Test
    void structuredWithoutTextHasNoContent() {
        var r = (ToolResult.Success) ToolResult.structured(42);
        assertThat(r.structured()).contains(42);
        assertThat(r.content()).isEmpty();
    }

    @Test
    void structuredWithTextCarriesContent() {
        var r = (ToolResult.Success) ToolResult.structured("data", "custom text");
        assertThat(r.structured()).contains("data");
        assertThat(((TextContent) r.content().getFirst()).text()).isEqualTo("custom text");
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
        var list = new java.util.ArrayList<ContentBlock>();
        list.add(TextContent.of("a"));
        var r = new ToolResult.Success(null, list);
        list.add(TextContent.of("b"));
        assertThat(r.content()).hasSize(1);
    }

    @Test
    void errorIsErrorResult() {
        ToolResult err = ToolResult.error("boom");

        assertThat(err).isInstanceOf(ToolResult.Error.class);
        var content = ((ToolResult.Error) err).content();
        assertThat(content).hasSize(1);
        assertThat(((TextContent) content.getFirst()).text()).isEqualTo("boom");
    }

    @Test
    void errorFactoryAcceptsContentBlocks() {
        var image = ImageContent.of(new byte[] {1, 2, 3}, "image/png");
        ToolResult err = ToolResult.error(TextContent.of("boom"), image);

        assertThat(err).isInstanceOf(ToolResult.Error.class);
        assertThat(((ToolResult.Error) err).content()).containsExactly(TextContent.of("boom"), image);
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
        var req = Map.<String, InputRequest>of();
        var r = ToolResult.inputRequired(req, null);
        assertThat(r).isInstanceOf(ToolResult.InputRequired.class);
        assertThat(((ToolResult.InputRequired) r).requestState()).isNull();
    }
}
