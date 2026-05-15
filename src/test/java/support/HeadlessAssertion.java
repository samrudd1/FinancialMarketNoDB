package support;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.awt.GraphicsEnvironment;

/**
 * JUnit 5 extension: aborts any test class that runs outside headless mode,
 * preventing Swing windows from opening mid-suite and blocking CI.
 *
 * Usage: @ExtendWith(HeadlessAssertion.class)
 */
public class HeadlessAssertion implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException(
                    "Test suite requires -Djava.awt.headless=true. " +
                    "Check Surefire <argLine> in pom.xml.");
        }
    }
}
