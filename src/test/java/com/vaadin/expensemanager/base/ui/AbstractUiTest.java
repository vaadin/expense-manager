package com.vaadin.expensemanager.base.ui;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedMain;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The one base of every UI test (layer 3, ADR-0027): the test configuration and the
 * singleton Testcontainers Postgres, declared <em>once</em>.
 *
 * <p>Why a shared base rather than the same three lines in each class: Spring caches a
 * test context per distinct configuration, and a {@code @ServiceConnection} field is
 * part of that configuration. Each class declaring its own container field made 9
 * contexts, and {@code @UnifiedSpringBootTest} starts an embedded server for each one,
 * even on Browserless (F-086). Inheriting this field gives every UI test the same key,
 * so one context serves the whole suite.
 *
 * <p>Test data isolation stays with each family's base ({@code TableSnapshot}); this
 * class holds nothing per test. Don't add {@code @MockitoBean}, {@code @Import} or
 * properties to a subclass without reason: any of them splits the context again.
 */
@UnifiedSpringBootTest
@ActiveProfiles("test")
public abstract class AbstractUiTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    /** The routed view's column inside {@code MainLayout}, for what the page says. */
    protected static UnifiedMain view(UnifiedPage page) {
        return page.find(UnifiedMain.class).withClassName("app-shell__content");
    }
}
