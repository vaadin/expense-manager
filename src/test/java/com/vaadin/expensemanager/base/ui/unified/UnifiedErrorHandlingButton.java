package com.vaadin.expensemanager.base.ui.unified;

import com.vaadin.browserless.internal.MockVaadin;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.ToolImplementation;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.Query;
import com.vaadin.testing.unified.query.TextFilter;

/**
 * A {@link Button} clicked the way a browser's click reaches the server: an exception
 * its listener does not catch goes to the session's {@code ErrorHandler}, not to the
 * test (ADR-0027, proposed). Generic; for testing an app's error handling.
 *
 * <p>In a browser that is simply what Flow does with a failed server round trip, so
 * the default is a plain click. Browserless calls the listener on the test thread and
 * rethrows its exception into the test instead — the session's handler never runs
 * (Browserless's {@code MockVaadin.runUIQueue} documents this as deliberate). The
 * override catches the exception and hands it to {@code
 * VaadinSession.getCurrent().getErrorHandler()}, as Flow's request handling would,
 * then runs the round trip that would have followed. Whatever handler the app
 * installed decides what happens next, so a test still fails if none was installed.
 *
 * <p>Only a usable button is routed: a hidden or disabled one is clicked plainly, so
 * the tool's own "not usable" failure reaches the test unchanged.
 */
@UnifiedComponent(value = Button.class, overrides = @ToolImplementation(
        tool = TestingTool.BROWSERLESS, value = UnifiedErrorHandlingButton.Browserless.class))
public interface UnifiedErrorHandlingButton extends UnifiedWrapper,
        Findable<UnifiedErrorHandlingButton.ErrorHandlingButtonQuery> {

    interface ErrorHandlingButtonQuery extends Query<UnifiedErrorHandlingButton,
            ErrorHandlingButtonQuery>, TextFilter<ErrorHandlingButtonQuery> {}

    default void click() {
        element().click();
    }

    /** The Browserless implementation: the session's error handler, as in a browser. */
    interface Browserless extends UnifiedErrorHandlingButton {
        @Override
        default void click() {
            if (!isVisible() || !isEnabled()) {
                element().click();
                return;
            }
            try {
                element().click();
            } catch (RuntimeException uncaught) {
                VaadinSession.getCurrent().getErrorHandler().error(new ErrorEvent(uncaught));
                MockVaadin.clientRoundtrip();
            }
        }
    }
}
