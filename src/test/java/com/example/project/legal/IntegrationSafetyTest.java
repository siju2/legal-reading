package com.example.project.legal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class IntegrationSafetyTest {
    @Test void aiIsDisabledWithoutConfiguration() {
        assertThat(new IntegrationSettings(new MockEnvironment()).aiReady()).isFalse();
        assertThat(new IntegrationSettings(new MockEnvironment().withProperty("legal.ai.enabled","true")).aiReady()).isFalse();
    }
}
