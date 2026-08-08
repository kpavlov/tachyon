/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2026_07_28.codecs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.config.ServerIdentity;
import dev.tachyonmcp.api.server.domain.Icon;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerInfoMapperTest {

    @Test
    void omitsIconsWhenServerIdentityHasNone() {
        var implementation = ServerInfoMapper.toImplementation(ServerIdentity.DEFAULT);

        assertThat(implementation.icons()).isNull();
    }

    @Test
    void carriesIconsAndOmitsEmptySizes() {
        var icon = Icon.of("https://example.test/icon.png", "image/png", List.of(), "light");
        var serverIdentity = ServerIdentity.builder().icons(icon).build();

        var implementation = ServerInfoMapper.toImplementation(serverIdentity);

        assertThat(implementation.icons()).hasSize(1);
        assertThat(implementation.icons().getFirst().src()).isEqualTo("https://example.test/icon.png");
        assertThat(implementation.icons().getFirst().sizes()).isNull();
    }
}
