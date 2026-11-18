package com.vaadin.expensemanager.reference.ui;

import java.math.BigDecimal;

import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.base.ui.NavGroup;
import com.vaadin.expensemanager.base.ui.ReferenceTabs;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.component.UnifiedTextField;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified view test (pyramid layer 3, ADR-0012) for {@link ExpenseTypeView}
 * — the ADMIN-only expense-type settings screen (issue #22, ADR-0008/0018).
 *
 * <p>Companion to {@link VatRateViewUiTest}; covers this screen's access control
 * and CRUD, plus the behaviours unique to expense types: an editor whose
 * default-rate {@code ComboBox} offers only <em>active</em> VAT rates (the
 * active-options query on the UI), a required default rate, and the
 * <em>optional</em> Lucide glyph a type's report rows draw (#172, ADR-0026).
 * Rows are found by their name, not their position, except where the position
 * is what a test asserts.
 */
class ExpenseTypeViewUiTest extends AbstractReferenceDataViewUiTest {

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesViewWithSeededTypesRendered(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);
        assertThat(grid.column("Name"))
                .hasCellTexts("Travel allowance", "Taxi/transport", "Accommodation",
                        "Restaurant/meals", "Parking/supplies/goods", "Publications",
                        "Kilometre allowance", "Meal allowance", "Other");
        // Each type renders its seeded default VAT rate.
        assertThat(grid.row(0)).hasCellText("Default VAT rate", "0 %");
        assertThat(grid.row(3)).hasCellText("Default VAT rate", "13.5 %");
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
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        assertThat(glyph(grid, "Travel allowance")).hasProperty("symbol", "plane");
        assertThat(glyph(grid, "Taxi/transport")).hasProperty("symbol", "car-taxi-front");
        assertThat(glyph(grid, "Accommodation")).hasProperty("symbol", "bed");
        assertThat(glyph(grid, "Restaurant/meals")).hasProperty("symbol", "utensils");
        for (int row = 0; row < grid.getRowCount(); row++) {
            assertThat(grid.row(row).cell("Icon").findComponent(SvgIcon.class)).exists();
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
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        addType(page, "Software licences", "25.5 %");

        assertThat(glyph(grid, "Software licences")).doesNotExist();
        assertThat(grid.row().withCellText("Name", "Software licences")).hasCellText("Icon", "—");

        rowAction(grid, "Name", "Software licences", "Edit expense type Software licences").click();
        UnifiedDialog editor = page.find(UnifiedDialog.class);
        editor.find(UnifiedComboBox.class).withLabel("Icon").selectItem("archive");
        editor.find(UnifiedButton.class).withText("Save").click();

        assertThat(glyph(grid, "Software licences")).hasProperty("symbol", "archive");
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

        assertThat(page.find(UnifiedTabs.class).withClassName("reference-tabs"))
                .hasSelectedTab("Expense Types");
        assertThat(navEntryLabels()).doesNotContain("Expense types");
        assertThat(NavGroup.of(ExpenseTypeView.class, "expense-types"))
                .contains(NavGroup.REFERENCE_TABLES);
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userSeesNoTabForARouteTheyCannotReach(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(new ReferenceTabs(ExpenseTypeView.class, authenticationContext)
                .getChildren()).isEmpty();
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachRouteByUrl(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);

        // Access control answers a denied route with the not-found page, never the grid.
        assertThat(page.findComponent(H2.class).withText("Page not found")).exists();
        assertThat(page.find(UnifiedGrid.class)).doesNotExist();
    }

    // ------------------------------------------------------------- CRUD: add

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addTypeThroughEditorAppendsActiveRow(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);
        assertThat(grid).hasRowCount(9);

        addType(page, "Software licences", "25.5 %");

        assertThat(grid).hasRowCount(10);
        assertThat(grid.row(9)).hasCellText("Name", "Software licences");
        assertThat(grid.row(9)).hasCellText("Default VAT rate", "25.5 %");
        assertThat(grid.row(9)).hasCellText("Status", "Active");
    }

    // ------------------------------------------------------------ CRUD: edit

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editTypeThroughEditorRenamesAndRepointsDefaultRate(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        rowAction(grid, "Name", "Restaurant/meals", "Edit expense type Restaurant/meals").click();
        UnifiedDialog editor = page.find(UnifiedDialog.class);
        editor.find(UnifiedTextField.class).withLabel("Name").setValue("Restaurant");
        editor.find(UnifiedComboBox.class).withLabel("Default VAT rate").selectItem("25.5 %");
        editor.find(UnifiedButton.class).withText("Save").click();

        assertThat(grid.row().withCellText("Name", "Restaurant/meals")).doesNotExist();
        // Row 3 kept its position; its default rate is now the repointed one.
        assertThat(grid.row(3)).hasCellText("Name", "Restaurant");
        assertThat(grid.row(3)).hasCellText("Default VAT rate", "25.5 %");
    }

    // --------------------------------------------------------- CRUD: reorder

    @Test
    @WithUserDetails("admin@vaadin.com")
    void reorderTypeSwapsRowsAndBoundaryButtonsAreDisabled(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        // Travel allowance is first; "Other" is last after the V10 catch-all
        // addition (issue #87).
        assertThat(rowAction(grid, "Name", "Travel allowance", "Move Travel allowance up"))
                .isDisabled();
        assertThat(rowAction(grid, "Name", "Other", "Move Other down")).isDisabled();

        // Move Taxi/transport (row 1) up; it swaps with Travel allowance.
        rowAction(grid, "Name", "Taxi/transport", "Move Taxi/transport up").click();

        assertThat(grid.row(0)).hasCellText("Name", "Taxi/transport");
        assertThat(grid.row(1)).hasCellText("Name", "Travel allowance");
    }

    // ------------------------------------------------------ CRUD: deactivate

    @Test
    @WithUserDetails("admin@vaadin.com")
    void deactivateRetainsRowAsInactiveAndExcludesFromActiveOptions(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        rowAction(grid, "Name", "Publications", "Deactivate Publications").click();

        // Retained in the admin grid at its position (the sixth row), flagged
        // Inactive — never deleted.
        assertThat(grid.row(5)).hasCellText("Name", "Publications");
        assertThat(grid.row(5)).hasCellText("Status", "Inactive");

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
        // Deactivate the 10 % rate, then open the add editor.
        var tenPercent = service.allVatRates().stream()
                .filter(r -> r.value().compareTo(new BigDecimal("10")) == 0)
                .findFirst().orElseThrow();
        service.setVatRateActive(tenPercent.id(), false);

        page.find(UnifiedButton.class).withText("New").click();

        var suggestions = page.find(UnifiedDialog.class).find(UnifiedComboBox.class)
                .withLabel("Default VAT rate").getItems();
        assertThat(suggestions)
                .contains("25.5 %", "13.5 %", "0 %")
                .doesNotContain("10 %");
    }

    // --------------------------------------------------------------- helpers

    private static void addType(UnifiedPage page, String name, String defaultRate) {
        page.step("Add expense type " + name, () -> {
            page.find(UnifiedButton.class).withText("New").click();
            UnifiedDialog editor = page.find(UnifiedDialog.class);
            editor.find(UnifiedTextField.class).withLabel("Name").setValue(name);
            editor.find(UnifiedComboBox.class).withLabel("Default VAT rate").selectItem(defaultRate);
            editor.find(UnifiedButton.class).withText("Save").click();
        });
    }

    /** The Lucide glyph in the named type's Icon cell; its {@code symbol} is the sprite id. */
    private static UnifiedGenericComponent glyph(UnifiedGrid grid, String typeName) {
        return grid.row().withCellText("Name", typeName).cell("Icon").findComponent(SvgIcon.class);
    }
}
