package com.vaadin.expensemanager.reference.ui;

import java.math.BigDecimal;

import com.vaadin.expensemanager.base.ui.ErrorSummary;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedBigDecimalField;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0027 proposed) for the shared
 * reference-screen machinery — the {@link ReferenceConfigView} base and the
 * {@link com.vaadin.expensemanager.base.ui.EditorDialog} — exercised
 * <strong>once</strong> here, driven through {@link VatRateView} as a
 * representative kind. Runs unchanged on Browserless and Playwright. The per-kind
 * view tests ({@link VatRateViewUiTest}, {@link ExpenseTypeViewUiTest}) then assert
 * only their kind-specific columns and fields/validators.
 *
 * <p>Covers the shared contract: the grid renders its rows (data + text status +
 * actions), the add/edit dialog round-trips, an invalid Save shows the
 * top-of-form error summary and persists nothing (ADR-0020 always-enabled Save),
 * reorder swaps neighbours with boundary buttons disabled, and the active-toggle
 * retains the row as inactive (ADR-0018, never deletes).
 *
 * <p>The rate field is a {@code BigDecimalField}, driven through the Core Set's
 * {@link UnifiedBigDecimalField} (since 0.1.0-alpha2).
 */
class ReferenceConfigViewUiTest extends AbstractReferenceDataViewUiTest {

    private static final String RATE = "Rate";
    private static final String STATUS = "Status";

    // ---------------------------------------------------------- grid renders

    @Test
    @WithUserDetails("admin@vaadin.com")
    void gridRendersRowsWithTextStatusAndActions(UnifiedPage page) {
        page.navigate(VatRateView.class);
        screenshot(page, "grid");

        // Data column + a text (never colour-only) status column.
        assertThat(grid(page).column(RATE)).hasCellTexts("25.5 %", "13.5 %", "10 %", "0 %");
        assertThat(columnText(page, STATUS)).allMatch("Active"::equals);
        // Each row carries an accessible Edit action.
        assertThat(rowAction(page, 0, "Edit rate 25.5 %")).isVisible();
    }

    // ------------------------------------------------------ add/edit round-trip

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addThroughEditorAppendsActiveRow(UnifiedPage page) {
        page.navigate(VatRateView.class);
        int before = grid(page).getRowCount();

        button(page, "New").click();
        screenshot(page, "editor-open");
        rateField(page).setValue(new BigDecimal("8.5"));
        button(page, "Save").click();
        screenshot(page, "saved");

        assertThat(grid(page)).hasRowCount(before + 1);
        assertThat(grid(page).row(before))
                .hasCellText(RATE, "8.5 %")
                .hasCellText(STATUS, "Active");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editThroughEditorChangesTheRow(UnifiedPage page) {
        page.navigate(VatRateView.class);

        rowAction(page, 1, "Edit rate 13.5 %").click();
        screenshot(page, "editor-open");
        rateField(page).setValue(new BigDecimal("12"));
        button(page, "Save").click();

        assertThat(grid(page).row(1)).hasCellText(RATE, "12 %");
        assertThat(columnText(page, RATE)).contains("12 %").doesNotContain("13.5 %");
    }

    // ------------------------------------------------------ error summary

    @Test
    @WithUserDetails("admin@vaadin.com")
    void invalidSaveShowsErrorSummaryAndPersistsNothing(UnifiedPage page) {
        page.navigate(VatRateView.class);
        int before = grid(page).getRowCount();

        // Always-enabled Save: submitting the empty required field surfaces the
        // top-of-form summary and writes nothing (ADR-0020).
        button(page, "New").click();
        button(page, "Save").click();
        screenshot(page, "error-summary");

        // The summary heading + the failed field's message appear in the dialog.
        // The open one: in a browser in dev mode the page also holds closed
        // <vaadin-dialog>s of Vaadin's own dev tooling, which Browserless never sees.
        var dialog = page.find(UnifiedDialog.class)
                .where(d -> Boolean.TRUE.equals(d.getPropertyBoolean("opened"))).single();
        // ErrorSummary is a Div: by tag alone that is every <div> in the dialog,
        // so its role class narrows it (scoped to the dialog, so cheap — F-087).
        var summary = dialog.findComponent(ErrorSummary.class)
                .withClassName("error-summary").single();
        assertThat(summary).isVisible();
        assertThat(summary.element().getText())
                .contains("Please fix the following:")
                .contains("Rate is required");
        // The field-level error is a focusable entry that navigates to its field
        // (the accessible error-summary pattern), not plain text.
        assertThat(dialog.find(UnifiedButton.class).withText("Rate is required").single())
                .isVisible();
        // The shared ErrorSummary carries the accessibility contract once, for
        // every form: a labelled, focusable group whose aria-labelledby names its
        // own heading. It is tabindex=0 (not the -1 the GOV.UK pattern would use)
        // so it joins a Dialog's focus trap and Tab-after-submit reaches the fields
        // rather than the dialog frame — see ErrorSummary and web-components#3486.
        assertThat(summary.element().getAttribute("role")).isEqualTo("group");
        assertThat(summary.element().getAttribute("tabindex")).isEqualTo("0");
        assertThat(summary.element().getAttribute("aria-labelledby")).isNotBlank();
        // The dialog stays open (Save still present) and no row was added.
        assertThat(button(page, "Save")).isVisible();
        assertThat(grid(page)).hasRowCount(before);
    }

    // ----------------------------------------------------------- reorder

    @Test
    @WithUserDetails("admin@vaadin.com")
    void reorderSwapsRowsAndBoundaryButtonsAreDisabled(UnifiedPage page) {
        page.navigate(VatRateView.class);
        assertThat(grid(page).column(RATE)).hasCellTexts("25.5 %", "13.5 %", "10 %", "0 %");

        assertThat(rowAction(page, 0, "Move rate 25.5 % up")).isDisabled();
        assertThat(rowAction(page, 3, "Move rate 0 % down")).isDisabled();

        rowAction(page, 1, "Move rate 13.5 % up").click();
        screenshot(page, "reordered");

        assertThat(grid(page).column(RATE)).hasCellTexts("13.5 %", "25.5 %", "10 %", "0 %");
    }

    // -------------------------------------------------------- active-toggle

    @Test
    @WithUserDetails("admin@vaadin.com")
    void deactivateRetainsRowAsInactive(UnifiedPage page) {
        page.navigate(VatRateView.class);

        rowAction(page, 0, "Deactivate rate 25.5 %").click();
        screenshot(page, "deactivated");

        assertThat(grid(page).row(0))
                .hasCellText(RATE, "25.5 %")
                .hasCellText(STATUS, "Inactive");
        // The toggle now offers to re-activate the retained row.
        assertThat(rowAction(page, 0, "Activate rate 25.5 %")).isVisible();
    }

    private static UnifiedBigDecimalField rateField(UnifiedPage page) {
        return page.find(UnifiedBigDecimalField.class).withLabel("Rate (%)").single();
    }
}
