package support;

import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeTest {

    @Test
    void surefirePicksUpTests() {
        assertThat(true).isTrue();
    }

    @Test
    void headlessModeIsEnabled() {
        assertThat(GraphicsEnvironment.isHeadless())
                .as("java.awt.headless must be true — Surefire argLine or -Djava.awt.headless=true is missing")
                .isTrue();
    }

    @Test
    void assertJIsOnClasspath() {
        assertThat("AssertJ").isNotBlank();
    }
}
