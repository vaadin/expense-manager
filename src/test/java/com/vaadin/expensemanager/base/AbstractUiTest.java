package com.vaadin.expensemanager.base;

import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
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
}
