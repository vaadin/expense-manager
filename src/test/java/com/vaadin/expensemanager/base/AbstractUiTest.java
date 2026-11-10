package com.vaadin.expensemanager.base;

import java.nio.file.Files;
import java.nio.file.Path;

import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for Unified UI tests (pyramid layer 3, ADR-0027 proposed): written once,
 * run on Browserless or Playwright, chosen per run by {@code -Dvaadin.testing.tool}.
 *
 * <ul>
 *   <li>{@link UnifiedSpringBootTest} — the app on a random port, the same
 *       Spring context for every Testing Tool, {@code UnifiedPage} injected into
 *       test methods.</li>
 *   <li>The singleton Testcontainers Postgres, declared once here. The
 *       {@code SpringBrowserlessTest} bases had to re-declare it because that
 *       class took the single superclass slot (F-008); the Unified API needs no
 *       base class, so this one can hold it.</li>
 *   <li>{@link DatabaseSnapshotExtension} instead of {@code @Transactional}
 *       rollback, which does not reach a browser's server thread. Never put
 *       {@code @Transactional} on a subclass: it would pass on Browserless and
 *       mean something else on Playwright.</li>
 * </ul>
 *
 * <p>Log in with Spring Security's {@code @WithUserDetails} on the method or
 * class, as before: the adapter signs the browser in through the Unified API's
 * test-only endpoint.
 *
 * <p>Call {@link #screenshot(UnifiedPage, String)} at a test's main steps. It is
 * off unless the run asks for it with {@code -Dui.screenshots=true}, and a no-op
 * on Browserless, which has no browser to capture.
 */
@UnifiedSpringBootTest
@ActiveProfiles("test")
@ExtendWith(DatabaseSnapshotExtension.class)
public abstract class AbstractUiTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    /** Opt-in switch: {@code -Dui.screenshots=true}. */
    static final String SCREENSHOTS = "ui.screenshots";
    /** Where they go; override with {@code -Dui.screenshots.dir=...}. */
    static final String SCREENSHOTS_DIR = "ui.screenshots.dir";

    private Path screenshotDir;
    private int screenshotStep;

    @BeforeEach
    void prepareScreenshots(TestInfo info) {
        screenshotDir = Path.of(System.getProperty(SCREENSHOTS_DIR, "target/screenshots"),
                getClass().getSimpleName(),
                info.getTestMethod().map(m -> m.getName()).orElse("unknown"));
        screenshotStep = 0;
    }

    /**
     * Saves a full-page screenshot of this step as
     * {@code target/screenshots/<TestClass>/<testMethod>/NN-<step>.png}, when
     * screenshots are switched on and the run has a real browser; otherwise does
     * nothing, so the test stays the same on every Testing Tool.
     */
    protected void screenshot(UnifiedPage page, String step) {
        if (!Boolean.getBoolean(SCREENSHOTS) || !page.hasRealBrowser()) {
            return;
        }
        try {
            Files.createDirectories(screenshotDir);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Path file = screenshotDir.resolve("%02d-%s.png".formatted(++screenshotStep,
                step.replaceAll("[^A-Za-z0-9._-]+", "-")));
        // Full page through the Playwright escape hatch: the list views outgrow
        // the viewport. Any other browser tool gets the Unified viewport shot.
        page.tryUnwrap(Page.class).ifPresentOrElse(
                pw -> pw.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(true)),
                () -> page.screenshot(file));
    }
}
