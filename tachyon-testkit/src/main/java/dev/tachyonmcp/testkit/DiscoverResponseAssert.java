/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import java.net.http.HttpResponse;
import org.assertj.core.api.AbstractAssert;
import org.intellij.lang.annotations.Language;

/** Staged assertions for an MCP 2026-07-28 {@code server/discover} response. */
public final class DiscoverResponseAssert extends AbstractAssert<DiscoverResponseAssert, HttpResponse<String>> {

    DiscoverResponseAssert(HttpResponse<String> response) {
        super(response, DiscoverResponseAssert.class);
    }

    /**
     * Verifies HTTP and JSON-RPC success, then exposes discover result assertions.
     *
     * @return success-only assertions
     */
    public DiscoverSuccessAssert isSuccess() {
        isNotNull();
        if (actual.statusCode() != 200) {
            failWithMessage("Expected discover HTTP status <200> but was <%s>: %s", actual.statusCode(), actual.body());
        }
        JsonRpcResponseAssert.assertThat(actual).isSuccess().hasId(1);
        return new DiscoverSuccessAssert(actual.body());
    }

    /** Assertions available after the discover success branch is verified. */
    public static final class DiscoverSuccessAssert extends AbstractAssert<DiscoverSuccessAssert, String> {

        private DiscoverSuccessAssert(String body) {
            super(body, DiscoverSuccessAssert.class);
        }

        /**
         * Verifies the discover capabilities object.
         *
         * @param capabilities expected capabilities JSON
         * @return {@code this}
         */
        public DiscoverSuccessAssert hasCapabilities(@Language("json") String capabilities) {
            assertThatJson(actual).inPath("$.result.capabilities").isEqualTo(capabilities);
            return this;
        }
    }
}
