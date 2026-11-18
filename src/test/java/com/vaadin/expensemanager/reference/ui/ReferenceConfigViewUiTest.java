package com.vaadin.expensemanager.reference.ui;

import java.math.BigDecimal;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedBigDecimalField;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedGrid;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified test (pyramid layer 3, ADR-0012) for the shared reference-screen
 * machinery — the {@link ReferenceConfigView} base and the
 * {@link com.vaadin.expensemanager.base.ui.EditorDialog} — exercised
 * <strong>once</strong> here, driven through {@link VatRateView} as a
 * representative kind. The per-kind view tests ({@link VatRateViewUiTest},
 * {@link ExpenseTypeViewUiTest}) then assert only their kind-specific columns and
 * fields/validators.
 *
 * <p>Covers the shared contract: the grid renders its rows (data + text status +
 * actions), the add/edit dialog round-trips, an invalid Save shows the
 * top-of-form error summary and persists nothing (ADR-0020 always-enabled Save),
 * reorder swaps neighbours with boundary buttons disabled, and the active-toggle
 * retains the row as inactive (ADR-0018, never deletes).
 */
class ReferenceConfigViewUiTest extends AbstractReferenceDataViewUiTest {

    // ---------------------------------------------------------- grid renders

    @Test
    @WithUserDetails("admin@vaadin.com")
    void gridRendersRowsWithTextStatusAndActions(UnifiedPage page) {
        page.navigate(VatRateView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        // Data column + a text (never colour-only) status column.
        assertThat(grid.column("Rate")).hasCellTexts("25.5 %", "13.5 %", "10 %", "0 %");
        assertThat(grid.column("Status")).hasCellTexts("Active", "Active", "Active", "Active");
        // Each row carries an accessible Edit action.
        assertThat(rowAction(grid, "Rate", "25.5 %", "Edit rate 25.5 %")).exists();
    }

    // ------------------------------------------------------ add/edit round-trip

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addThroughEditorAppendsActiveRow(UnifiedPage page) {
        page.navigate(VatRateView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);
        assertThat(grid).hasRowCount(4);

        page.find(UnifiedButton.class).withText("New").click();
        UnifiedDialog editor = page.find(UnifiedDialog.class);
        editor.find(UnifiedBigDecimalField.class).setValue(new BigDecimal("8.5"));
        editor.find(UnifiedButton.class).withText("Save").click();

        assertThat(grid).hasRowCount(5);
        assertThat(grid.row(4)).hasCellText("Rate", "8.5 %");
        assertThat(grid.row(4)).hasCellText("Status", "Active");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editThroughEditorChangesTheRow(UnifiedPage page) {
        page.navigate(VatRateView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        rowAction(grid, "Rate", "13.5 %", "Edit rate 13.5 %").click();
        UnifiedDialog editor = page.find(UnifiedDialog.class);
        editor.find(UnifiedBigDecimalField.class).setValue(new BigDecimal("12"));
        editor.find(UnifiedButton.class).withText("Save").click();

        assertThat(grid.row().withCellText("Rate", "12 %")).exists();
        assertThat(grid.row().withCellText("Rate", "13.5 %")).doesNotExist();
    }

    // ------------------------------------------------------ error summary

    @Test
    @WithUserDetails("admin@vaadin.com")
    void invalidSaveShowsErrorSummaryAndPersistsNothing(UnifiedPage page) {
        page.navigate(VatRateView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);
        assertThat(grid).hasRowCount(4);

        // Always-enabled Save: submitting the empty required field surfaces the
        // top-of-form summary and writes nothing (ADR-0020).
        page.find(UnifiedButton.class).withText("New").click();
        UnifiedDialog editor = page.find(UnifiedDialog.class);
        editor.find(UnifiedButton.class).withText("Save").click();

        // The summary heading + the failed field's message appear in the dialog.
        UnifiedDiv summary = editor.find(UnifiedDiv.class).withClassName("error-summary");
        assertThat(summary).hasTextContaining("Please fix the following:");
        assertThat(summary).hasTextContaining("Rate is required");
        // The field-level error is a focusable entry that navigates to its field
        // (the accessible error-summary pattern), not plain text.
        assertThat(summary.find(UnifiedButton.class).withText("Rate is required")).exists();
        // The shared ErrorSummary carries the accessibility contract once, for
        // every form: a labelled, focusable group whose aria-labelledby names its
        // own heading. It is tabindex=0 (not the -1 the GOV.UK pattern would use)
        // so it joins a Dialog's focus trap and Tab-after-submit reaches the fields
        // rather than the dialog frame — see ErrorSummary and web-components#3486.
        assertThat(summary.element().getAttribute("role")).isEqualTo("group");
        assertThat(summary.element().getAttribute("tabindex")).isEqualTo("0");
        assertThat(summary.element().getAttribute("aria-labelledby")).isNotBlank();
        // The dialog stays open and no row was added.
        assertThat(editor).isOpen();
        assertThat(grid).hasRowCount(4);
    }

    // ----------------------------------------------------------- reorder

    @Test
    @WithUserDetails("admin@vaadin.com")
    void reorderSwapsRowsAndBoundaryButtonsAreDisabled(UnifiedPage page) {
        page.navigate(VatRateView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);
        assertThat(grid.column("Rate")).hasCellTexts("25.5 %", "13.5 %", "10 %", "0 %");

        assertThat(rowAction(grid, "Rate", "25.5 %", "Move rate 25.5 % up")).isDisabled();
        assertThat(rowAction(grid, "Rate", "0 %", "Move rate 0 % down")).isDisabled();

        rowAction(grid, "Rate", "13.5 %", "Move rate 13.5 % up").click();

        assertThat(grid.column("Rate")).hasCellTexts("13.5 %", "25.5 %", "10 %", "0 %");
    }

    // -------------------------------------------------------- active-toggle

    @Test
    @WithUserDetails("admin@vaadin.com")
    void deactivateRetainsRowAsInactive(UnifiedPage page) {
        page.navigate(VatRateView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        rowAction(grid, "Rate", "25.5 %", "Deactivate rate 25.5 %").click();

        assertThat(grid.row(0)).hasCellText("Rate", "25.5 %");
        assertThat(grid.row(0)).hasCellText("Status", "Inactive");
        // The toggle now offers to re-activate the retained row.
        assertThat(rowAction(grid, "Rate", "25.5 %", "Activate rate 25.5 %")).exists();
    }
}
