package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless end-to-end test (pyramid layer 3, ADR-0012) for the shared error
 * routing (issue #86).
 *
 * <p>Two halves of the acceptance split:
 * <ul>
 *   <li><strong>Technical</strong> — the global {@link UiErrorHandler} is installed as
 *       the session's error handler (so any uncaught action failure reaches it in
 *       production), and when it handles an error it opens the generic
 *       {@link ErrorDialog} showing only the reassuring message, never the raw cause
 *       under the non-local {@code test} profile. (The browserless harness rethrows an
 *       uncaught listener exception to the test rather than routing it through the
 *       session handler, so the handler is exercised directly; the production route
 *       Vaadin → session handler is verified by hand — see docs/manual-verification.)</li>
 *   <li><strong>Domain rule</strong> — a {@link DomainRuleException} from a save is
 *       caught locally by the {@link EditorDialog} and lands in the form's summary,
 *       opening no dialog.</li>
 * </ul>
 */
@UnifiedSpringBootTest
@ActiveProfiles("test")
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class ErrorDialogFlowUiTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Autowired
    private UiErrorHandler errorHandler;

    @Test
    void theGlobalHandlerIsInstalledAsTheSessionErrorHandler(UnifiedPage page) {
        page.navigate(DashboardView.class);

        // The VaadinServiceInitListener wired it onto the session, so any uncaught
        // action failure is routed here in production (issue #86).
        assertThat(VaadinSession.getCurrent().getErrorHandler())
                .isInstanceOf(UiErrorHandler.class);
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void aTechnicalErrorOpensTheGenericDialogWithoutTheCause(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        page.navigate(DashboardView.class);

        errorHandler.error(new ErrorEvent(new IllegalStateException("stack trace guts")));

        // TODO unified: Browserless Escape Hatch: $(ErrorDialog.class).all(), no Wrapper for ErrorDialog: write one (ADR 0003)
        var dialogs = browserless.$(ErrorDialog.class).all();
        assertThat(dialogs).hasSize(1);
        String dialogText = textOf(dialogs.getFirst());
        assertThat(dialogText).contains(ErrorDialog.MESSAGE);
        assertThat(dialogText).doesNotContain("stack trace guts");
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void aDomainRuleLandsInTheSummaryAndOpensNoDialog(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        page.navigate(DashboardView.class);
        new EditorDialog<>("Editor", new TextField("Name"),
                new Binder<>(Object.class), new Object())
                .onSave(() -> {
                    throw new DomainRuleException("Name is required");
                })
                .open();

        clickSave(page);

        // No technical dialog; the user-actionable message shows in the form summary.
        // TODO unified: Browserless Escape Hatch: $(ErrorDialog.class).all(), no Wrapper for ErrorDialog: write one (ADR 0003)
        assertThat(browserless.$(ErrorDialog.class).all()).isEmpty();
        // TODO unified: Browserless Escape Hatch: $(ErrorSummary.class).all(), no Wrapper for ErrorSummary: write one (ADR 0003)
        var summaries = browserless.$(ErrorSummary.class).all();
        assertThat(summaries).anyMatch(summary ->
                summary.isVisible() && textOf(summary).contains("Name is required"));
    }

    private void clickSave(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // TODO unified: Browserless Escape Hatch: $(Button.class).all().stream().filter(button -> "Save".equals(button.getText())).findFirst().orElseThrow(() -> new AssertionError("No 'Save' button in the editor"))
        var save = browserless.$(Button.class).all().stream()
                .filter(button -> "Save".equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No 'Save' button in the editor"));
        // TODO unified: Component Reference: test(save).click(): replace it with a locator that finds the same component
        browserless.test(save).click();
    }

    /** Flattens every rendered text node in a component tree, for content assertions. */
    private static String textOf(Component component) {
        var text = new StringBuilder();
        if (component instanceof HasText hasText) {
            text.append(hasText.getText()).append(' ');
        }
        component.getChildren().forEach(child -> text.append(textOf(child)));
        return text.toString();
    }
}
