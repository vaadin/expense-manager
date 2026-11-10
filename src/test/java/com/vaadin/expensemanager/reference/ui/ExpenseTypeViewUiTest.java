package com.vaadin.expensemanager.reference.ui;

import java.math.BigDecimal;

import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.base.ui.NavGroup;
import com.vaadin.expensemanager.base.ui.ReferenceTabs;
import com.vaadin.expensemanager.base.ui.unified.UnifiedTabs;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedTextField;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0027 proposed) for {@link ExpenseTypeView}
 * — the ADMIN-only expense-type settings screen (issue #22, ADR-0008/0018). Runs
 * unchanged on Browserless and Playwright.
 *
 * <p>Companion to {@link VatRateViewUiTest}; covers this screen's access control
 * and CRUD, plus the behaviours unique to expense types: an editor whose
 * default-rate {@code ComboBox} offers only <em>active</em> VAT rates (the
 * active-options query on the UI), a required default rate, and the
 * <em>optional</em> Lucide glyph a type's report rows draw (#172, ADR-0026). Grid
 * cells are read by column header; the glyph is the {@code symbol} property of the
 * Icon cell's {@code <vaadin-icon>}, which Flow sets server-side, so both Testing
 * Tools read the same value.
 */
class ExpenseTypeViewUiTest extends AbstractReferenceDataViewUiTest {

    /** The glyph column leads, as the glyph leads a report row (#172). */
    private static final String ICON = "Icon";
    private static final String NAME = "Name";
    private static final String RATE = "Default VAT rate";
    private static final String STATUS = "Status";

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesViewWithSeededTypesRendered(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);
        screenshot(page, "expense-types");

        assertThat(grid(page).column(NAME))
                .hasCellTexts("Travel allowance", "Taxi/transport", "Accommodation",
                        "Restaurant/meals", "Parking/supplies/goods", "Publications",
                        "Kilometre allowance", "Meal allowance", "Other");
        // Each type renders its seeded default VAT rate.
        assertThat(grid(page).row(0)).hasCellText(RATE, "0 %");
        assertThat(grid(page).row(3)).hasCellText(RATE, "13.5 %");
    }

    /**
     * Every seeded type carries the glyph its report rows draw (#172, V15). The four
     * the design draws on frame 116:4444 are asserted by name; the rest are the app's
     * initial choices and only have to be present.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void everySeededTypeRendersItsGlyph(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);

        assertThat(glyphOf(page, 0)).isEqualTo("plane");            // Travel allowance
        assertThat(glyphOf(page, 1)).isEqualTo("car-taxi-front");   // Taxi/transport
        assertThat(glyphOf(page, 2)).isEqualTo("bed");              // Accommodation
        assertThat(glyphOf(page, 3)).isEqualTo("utensils");         // Restaurant/meals
        for (int row = 0; row < grid(page).getRowCount(); row++) {
            assertThat(glyphOf(page, row))
                    .as("row %d carries a glyph", row).isNotNull();
        }
    }

    /**
     * The glyph is optional and admin-editable: a type saved with the picker
     * untouched renders no glyph and a dash in its place, and choosing one persists
     * it. Nothing rests on the glyph — the type name always renders beside it
     * (ADR-0020) — so "none" has to be a valid, visible state rather than a blank
     * cell that reads as a rendering failure.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theGlyphIsOptionalAndAdminChosen(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);
        int before = grid(page).getRowCount();

        addType(page, "Software licences", "25.5 %");

        assertThat(grid(page)).hasRowCount(before + 1);
        assertThat(grid(page).row(before)).hasCellText(ICON, "—");
        assertThat(glyphOf(page, before)).isNull();

        rowAction(page, before, "Edit expense type Software licences").click();
        page.find(UnifiedComboBox.class).withLabel("Icon").single().selectItem("archive");
        screenshot(page, "icon-chosen");
        button(page, "Save").click();
        screenshot(page, "saved");

        page.waitUntil(() -> "archive".equals(glyphOf(page, before)));
        assertThat(glyphOf(page, before)).isEqualTo("archive");
    }

    /**
     * Reached through the reference tab bar, not the shell — #169 made this route
     * a {@code covered()} entry of the Reference Tables group rather than a menu
     * item of its own, so it lights the shell's pill without appearing in it.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesThisRouteThroughTheTabBarRatherThanTheShellMenu(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);

        assertThat(page.find(UnifiedTabs.class).withClassName("reference-tabs").single()
                .getSelectedTabText()).isEqualTo("Expense Types");
        assertThat(navEntryLabels()).doesNotContain("Expense types");
        assertThat(NavGroup.of(ExpenseTypeView.class, "expense-types"))
                .contains(NavGroup.REFERENCE_TABLES);
    }

    /**
     * Builds the bar server-side for a plain user, as before: every reference route
     * is ADMIN-only, so no screen a plain user can open renders the bar, and the
     * filter is only observable on the component itself. Portable — the component
     * and {@code AuthenticationContext} are in the test JVM on every Testing Tool.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userSeesNoTabForARouteTheyCannotReach(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(new ReferenceTabs(ExpenseTypeView.class, authenticationContext)
                .getChildren()).isEmpty();
    }

    /**
     * Was {@code assertThatThrownBy(navigate(..))}: Browserless throws on a denied
     * route, a browser just renders the access-denied outcome. What both share, and
     * what the user meets, is that the expense-type grid never renders.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachRouteByUrl(UnifiedPage page) {
        page.navigate(DashboardView.class);
        page.navigate("expense-types");

        assertThat(page.find(UnifiedGrid.class).exists()).isFalse();
    }

    // ------------------------------------------------------------- CRUD: add

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addTypeThroughEditorAppendsActiveRow(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);
        int before = grid(page).getRowCount();

        addType(page, "Software licences", "25.5 %");

        assertThat(grid(page)).hasRowCount(before + 1);
        assertThat(grid(page).row(before))
                .hasCellText(NAME, "Software licences")
                .hasCellText(RATE, "25.5 %")
                .hasCellText(STATUS, "Active");
    }

    // ------------------------------------------------------------ CRUD: edit

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editTypeThroughEditorRenamesAndRepointsDefaultRate(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);

        // Restaurant/meals is the fourth seeded row (index 3).
        rowAction(page, 3, "Edit expense type Restaurant/meals").click();
        screenshot(page, "editor-open");
        nameField(page).setValue("Restaurant");
        rateCombo(page).selectItem("25.5 %");
        button(page, "Save").click();

        // Row 3 kept its position; its default rate is now the repointed one.
        assertThat(grid(page).row(3))
                .hasCellText(NAME, "Restaurant")
                .hasCellText(RATE, "25.5 %");
        assertThat(columnText(page, NAME))
                .contains("Restaurant").doesNotContain("Restaurant/meals");
    }

    // --------------------------------------------------------- CRUD: reorder

    @Test
    @WithUserDetails("admin@vaadin.com")
    void reorderTypeSwapsRowsAndBoundaryButtonsAreDisabled(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);

        // Travel allowance is first (row 0); "Other" is last (row 8) after the
        // V10 catch-all addition (issue #87).
        int last = grid(page).getRowCount() - 1;
        assertThat(rowAction(page, 0, "Move Travel allowance up")).isDisabled();
        assertThat(rowAction(page, last, "Move Other down")).isDisabled();

        // Move Taxi/transport (row 1) up; it swaps with Travel allowance.
        rowAction(page, 1, "Move Taxi/transport up").click();
        screenshot(page, "reordered");

        assertThat(grid(page).row(0)).hasCellText(NAME, "Taxi/transport");
        assertThat(grid(page).row(1)).hasCellText(NAME, "Travel allowance");
    }

    // ------------------------------------------------------ CRUD: deactivate

    @Test
    @WithUserDetails("admin@vaadin.com")
    void deactivateRetainsRowAsInactiveAndExcludesFromActiveOptions(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);

        // Publications is the sixth seeded row (index 5).
        rowAction(page, 5, "Deactivate Publications").click();
        screenshot(page, "deactivated");

        // Retained in the admin grid at its position, flagged Inactive — never deleted.
        assertThat(grid(page).row(5))
                .hasCellText(NAME, "Publications")
                .hasCellText(STATUS, "Inactive");

        assertThat(service.activeExpenseTypes())
                .noneMatch(t -> t.name().equals("Publications"));
        assertThat(service.allExpenseTypes())
                .anyMatch(t -> t.name().equals("Publications") && !t.active());
    }

    // ------------------------------------------ active-options in the editor

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editorOffersOnlyActiveVatRatesAsDefault(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);
        // Deactivate the 10 % rate, then open the add editor. Committed: the
        // snapshot extension restores it after the test.
        var tenPercent = service.allVatRates().stream()
                .filter(r -> r.value().compareTo(new BigDecimal("10")) == 0)
                .findFirst().orElseThrow();
        service.setVatRateActive(tenPercent.id(), false);

        button(page, "New").click();
        screenshot(page, "editor-open");

        assertThat(rateCombo(page).getItems())
                .contains("25.5 %", "13.5 %", "0 %")
                .doesNotContain("10 %");
    }

    // --------------------------------------------------------------- helpers

    /** Adds a type through the editor, leaving the icon picker untouched. */
    private void addType(UnifiedPage page, String name, String rate) {
        button(page, "New").click();
        screenshot(page, "editor-open");
        nameField(page).setValue(name);
        rateCombo(page).selectItem(rate);
        button(page, "Save").click();
        screenshot(page, "saved");
    }

    private static UnifiedTextField nameField(UnifiedPage page) {
        return page.find(UnifiedTextField.class).withLabel("Name").single();
    }

    private static UnifiedComboBox rateCombo(UnifiedPage page) {
        return page.find(UnifiedComboBox.class).withLabel("Default VAT rate").single();
    }

    /** The Lucide symbol id the row's Icon cell renders, or {@code null} if none. */
    private static String glyphOf(UnifiedPage page, int row) {
        return grid(page).row(row).cell(ICON).findComponent(SvgIcon.class).all().stream()
                .findFirst().map(icon -> icon.element().getProperty("symbol")).orElse(null);
    }
}
