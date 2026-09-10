package com.vaadin.expensemanager.reference.ui;

import java.math.BigDecimal;

import com.vaadin.expensemanager.base.ui.ErrorSummary;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless test (pyramid layer 3, ADR-0012) for the shared reference-screen
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

    private static final int RATE_COL = 0;
    private static final int STATUS_COL = 1;
    private static final int ACTIONS_COL = 2;

    // ---------------------------------------------------------- grid renders

    @Test
    @WithUserDetails("admin@vaadin.com")
    void gridRendersRowsWithTextStatusAndActions() {
        navigate(VatRateView.class);
        var grid = findGrid(VatRateDto.class);

        // Data column + a text (never colour-only) status column.
        assertThat(columnText(grid, RATE_COL)).containsExactly("25.5 %", "13.5 %", "10 %", "0 %");
        assertThat(columnText(grid, STATUS_COL)).allMatch("Active"::equals);
        // Each row carries an accessible Edit action.
        assertThat(rowActionButton(grid, 0, ACTIONS_COL, "Edit rate 25.5 %")).isNotNull();
    }

    // ------------------------------------------------------ add/edit round-trip

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addThroughEditorAppendsActiveRow() {
        navigate(VatRateView.class);
        int before = findGrid(VatRateDto.class).size();

        findButton().withText("New").click();
        findBigDecimalField().setValue(new BigDecimal("8.5"));
        findButton().withText("Save").click();

        var grid = findGrid(VatRateDto.class);
        assertThat(grid.size()).isEqualTo(before + 1);
        assertThat(grid.getCellText(before, RATE_COL)).isEqualTo("8.5 %");
        assertThat(grid.getCellText(before, STATUS_COL)).isEqualTo("Active");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editThroughEditorChangesTheRow() {
        navigate(VatRateView.class);

        test(rowActionButton(findGrid(VatRateDto.class), 1, ACTIONS_COL,
                "Edit rate 13.5 %")).click();
        findBigDecimalField().setValue(new BigDecimal("12"));
        findButton().withText("Save").click();

        assertThat(columnText(findGrid(VatRateDto.class), RATE_COL))
                .contains("12 %").doesNotContain("13.5 %");
    }

    // ------------------------------------------------------ error summary

    @Test
    @WithUserDetails("admin@vaadin.com")
    void invalidSaveShowsErrorSummaryAndPersistsNothing() {
        navigate(VatRateView.class);
        int before = findGrid(VatRateDto.class).size();

        // Always-enabled Save: submitting the empty required field surfaces the
        // top-of-form summary and writes nothing (ADR-0020).
        findButton().withText("New").click();
        findButton().withText("Save").click();

        // The summary heading + the failed field's message appear in the dialog
        // overlay (UI-rooted, since the dialog renders outside the view tree).
        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("Please fix the following:")
                .contains("Rate is required");
        // The field-level error is a focusable entry that navigates to its field
        // (the accessible error-summary pattern), not plain text.
        assertThat(findButton().withText("Rate is required").exists()).isTrue();
        // The shared ErrorSummary carries the accessibility contract once, for
        // every form: a labelled, focusable group whose aria-labelledby names its
        // own heading. It is tabindex=0 (not the -1 the GOV.UK pattern would use)
        // so it joins a Dialog's focus trap and Tab-after-submit reaches the fields
        // rather than the dialog frame — see ErrorSummary and web-components#3486.
        var summary = find(ErrorSummary.class).single();
        assertThat(summary.getElement().getAttribute("role")).isEqualTo("group");
        assertThat(summary.getElement().getAttribute("tabindex")).isEqualTo("0");
        assertThat(summary.getElement().getAttribute("aria-labelledby"))
                .isNotBlank();
        // The dialog stays open (Save still present) and no row was added.
        assertThat(findButton().withText("Save").exists()).isTrue();
        assertThat(findGrid(VatRateDto.class).size()).isEqualTo(before);
    }

    // ----------------------------------------------------------- reorder

    @Test
    @WithUserDetails("admin@vaadin.com")
    void reorderSwapsRowsAndBoundaryButtonsAreDisabled() {
        navigate(VatRateView.class);
        var grid = findGrid(VatRateDto.class);
        assertThat(columnText(grid, RATE_COL))
                .containsExactly("25.5 %", "13.5 %", "10 %", "0 %");

        assertThat(rowActionButton(grid, 0, ACTIONS_COL, "Move rate 25.5 % up")
                .isEnabled()).isFalse();
        assertThat(rowActionButton(grid, 3, ACTIONS_COL, "Move rate 0 % down")
                .isEnabled()).isFalse();

        test(rowActionButton(grid, 1, ACTIONS_COL, "Move rate 13.5 % up")).click();

        assertThat(columnText(findGrid(VatRateDto.class), RATE_COL))
                .containsExactly("13.5 %", "25.5 %", "10 %", "0 %");
    }

    // -------------------------------------------------------- active-toggle

    @Test
    @WithUserDetails("admin@vaadin.com")
    void deactivateRetainsRowAsInactive() {
        navigate(VatRateView.class);

        test(rowActionButton(findGrid(VatRateDto.class), 0, ACTIONS_COL,
                "Deactivate rate 25.5 %")).click();

        var grid = findGrid(VatRateDto.class);
        assertThat(grid.getCellText(0, RATE_COL)).isEqualTo("25.5 %");
        assertThat(grid.getCellText(0, STATUS_COL)).isEqualTo("Inactive");
        // The toggle now offers to re-activate the retained row.
        assertThat(rowActionButton(grid, 0, ACTIONS_COL, "Activate rate 25.5 %")).isNotNull();
    }
}
