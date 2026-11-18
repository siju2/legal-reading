package com.example.project.legal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import java.net.*;
import java.io.IOException;

class LocalAiLifecycleTest {
    @Test void reusesBoundPortWithoutStartingOrStoppingAnotherProcess() throws Exception {
        try(var existing=new java.net.ServerSocket()) {
            existing.bind(new InetSocketAddress("127.0.0.1",0));
            var settings=new IntegrationSettings(new MockEnvironment()
                .withProperty("legal.ai.enabled","true").withProperty("legal.ai.provider","local")
                .withProperty("legal.ai.runtime-path","missing-runtime"));
            var lifecycle=new LocalAiLifecycle(settings,existing.getLocalPort());
            lifecycle.run(null);
            lifecycle.stop();
            assertThat(existing.isClosed()).isFalse();
        }
    }
    @Test void missingRuntimeFailsBeforeCreatingAProcess() throws Exception {
        int unusedPort;
        try(var probe=new java.net.ServerSocket(0)) {unusedPort=probe.getLocalPort();}
        var settings=new IntegrationSettings(new MockEnvironment()
            .withProperty("legal.ai.enabled","true").withProperty("legal.ai.provider","local")
            .withProperty("legal.ai.runtime-path","missing-runtime"));
        var lifecycle=new LocalAiLifecycle(settings,unusedPort);
        assertThatThrownBy(()->lifecycle.run(null)).isInstanceOf(IOException.class).hasMessageContaining("missing");
        lifecycle.stop();
    }
}
