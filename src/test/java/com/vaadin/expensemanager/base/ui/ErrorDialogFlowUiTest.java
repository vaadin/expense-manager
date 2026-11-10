package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.base.AbstractUiTest;
import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.expensemanager.base.ui.unified.UnifiedErrorHandlingButton;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0027 proposed) for the shared error routing
 * (issue #86). Runs unchanged on Browserless and Playwright.
 *
 * <p>Two halves of the acceptance split, both driven from the test-only
 * {@link ErrorFlowTestView} so a real browser can reach them too:
 * <ul>
 *   <li><strong>Technical</strong> — a listener failure nothing catches reaches the
 *       global {@link UiErrorHandler} through the session (the
 *       {@code VaadinServiceInitListener} installs it there), which opens the generic
 *       {@link ErrorDialog} showing only the reassuring message, never the raw cause
 *       under the non-local {@code test} profile.</li>
 *   <li><strong>Domain rule</strong> — a {@link DomainRuleException} from a save is
 *       caught locally by the {@link EditorDialog} and lands in the form's summary,
 *       opening no dialog.</li>
 * </ul>
 *
 * <p><strong>Tool divergence worked around.</strong> In a browser the failing click
 * takes the production route — Flow hands the exception to the session's error
 * handler. Browserless rethrows a listener's exception into the test instead, which is
 * why the browserless version of this class called the handler directly and only
 * inspected {@code VaadinSession.getErrorHandler()} for the wiring.
 * {@link UnifiedErrorHandlingButton} closes that gap on Browserless by handing the
 * exception to whatever handler the session has, as Flow would; so on both tools the
 * dialog only appears if {@link UiErrorHandler} really is the session's handler.
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class ErrorDialogFlowUiTest extends AbstractUiTest {

    /**
     * The wiring, observed rather than inspected: an uncaught action failure opens
     * {@link UiErrorHandler}'s dialog, which only happens if the init listener put
     * the handler on the session — otherwise Vaadin's default handler just logs it.
     */
    @Test
    void anUncaughtActionFailureReachesTheGlobalHandler(UnifiedPage page) {
        page.navigate(ErrorFlowTestView.class);
        assertThat(errorDialogs(page).exists()).isFalse();

        failUnexpectedly(page);

        assertThat(errorDialogs(page).single()).isOpen();
        screenshot(page, "technical-error-dialog");
    }

    @Test
    void aTechnicalErrorOpensTheGenericDialogWithoutTheCause(UnifiedPage page) {
        page.navigate(ErrorFlowTestView.class);

        failUnexpectedly(page);

        var dialogs = errorDialogs(page).all();
        assertThat(dialogs).hasSize(1);
        String dialogText = dialogs.getFirst().element().getText();
        assertThat(dialogText).contains(ErrorDialog.MESSAGE);
        assertThat(dialogText).doesNotContain(ErrorFlowTestView.CAUSE);
    }

    @Test
    void aDomainRuleLandsInTheSummaryAndOpensNoDialog(UnifiedPage page) {
        page.navigate(ErrorFlowTestView.class);
        page.find(UnifiedButton.class).withText(ErrorFlowTestView.OPEN_EDITOR).single().click();
        var editor = page.find(UnifiedDialog.class).withHeaderTitle("Editor").single();
        assertThat(editor).isOpen();

        editor.find(UnifiedButton.class).withText("Save").single().click();
        screenshot(page, "domain-rule-in-summary");

        // No technical dialog; the user-actionable message shows in the form summary.
        assertThat(errorDialogs(page).exists()).isFalse();
        var summaries = editor.findComponent(ErrorSummary.class).withClassName("error-summary")
                .all();
        assertThat(summaries).anySatisfy(summary -> {
            assertThat(summary.isVisible()).isTrue();
            assertThat(summary.element().getText()).contains(ErrorFlowTestView.DOMAIN_RULE);
        });
    }

    private static void failUnexpectedly(UnifiedPage page) {
        page.find(UnifiedErrorHandlingButton.class).withText(ErrorFlowTestView.FAIL).single()
                .click();
    }

    private static UnifiedDialog.DialogQuery errorDialogs(UnifiedPage page) {
        return page.find(UnifiedDialog.class).withHeaderTitle(ErrorDialog.TITLE);
    }
}
