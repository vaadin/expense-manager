package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedParagraph;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified end-to-end test (pyramid layer 3, ADR-0012) for the shared error
 * routing (issue #86).
 *
 * <p>Two halves of the acceptance split:
 * <ul>
 *   <li><strong>Technical</strong> — the global {@link UiErrorHandler} is installed as
 *       the session's error handler (so any uncaught action failure reaches it in
 *       production), and when it handles an error it opens the generic
 *       {@link ErrorDialog} showing only the reassuring message, never the raw cause
 *       under the non-local {@code test} profile. Both are observed through
 *       {@link ErrorFlowTestView}, whose failing button hands its exception to the
 *       session's handler as Vaadin does in a browser (Browserless Test would
 *       rethrow it into the test instead).</li>
 *   <li><strong>Domain rule</strong> — a {@link DomainRuleException} from a save is
 *       caught locally by the {@link EditorDialog} and lands in the form's summary,
 *       opening no dialog.</li>
 * </ul>
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class ErrorDialogFlowUiTest extends AbstractUiTest {

    /**
     * The wiring, observed rather than inspected: a failure handed to the session's
     * handler opens {@link UiErrorHandler}'s dialog only if the init listener put
     * that handler on the session — Vaadin's default handler just logs it.
     */
    @Test
    void theGlobalHandlerIsInstalledAsTheSessionErrorHandler(UnifiedPage page) {
        page.navigate(ErrorFlowTestView.class);
        assertThat(errorDialog(page)).doesNotExist();

        failUnexpectedly(page);

        assertThat(errorDialog(page)).isOpen();
    }

    @Test
    void aTechnicalErrorOpensTheGenericDialogWithoutTheCause(UnifiedPage page) {
        page.navigate(ErrorFlowTestView.class);

        failUnexpectedly(page);

        assertThat(errorDialog(page)).hasCount(1);
        assertThat(errorDialog(page)).hasTextContaining(ErrorDialog.MESSAGE);
        assertThat(errorDialog(page).find(UnifiedParagraph.class)).hasText(ErrorDialog.MESSAGE);
        // The cause would render as a <pre> (local profile only), never under `test`.
        assertThat(errorDialog(page).findComponent(Pre.class)).doesNotExist();
        assertThat(errorDialog(page).element().getText()).doesNotContain(ErrorFlowTestView.CAUSE);
    }

    @Test
    void aDomainRuleLandsInTheSummaryAndOpensNoDialog(UnifiedPage page) {
        page.navigate(ErrorFlowTestView.class);
        page.find(UnifiedButton.class).withText(ErrorFlowTestView.OPEN_EDITOR).click();
        UnifiedDialog editor = page.find(UnifiedDialog.class).withHeaderTitle("Editor");

        editor.find(UnifiedButton.class).withText("Save").click();

        // No technical dialog; the user-actionable message shows in the form summary.
        assertThat(errorDialog(page)).doesNotExist();
        assertThat(editor.find(UnifiedDiv.class).withClassName("error-summary").visible())
                .hasTextContaining(ErrorFlowTestView.DOMAIN_RULE);
    }

    private static void failUnexpectedly(UnifiedPage page) {
        page.find(UnifiedButton.class).withText(ErrorFlowTestView.FAIL).click();
    }

    private static UnifiedDialog errorDialog(UnifiedPage page) {
        return page.find(UnifiedDialog.class).withHeaderTitle(ErrorDialog.TITLE);
    }
}
