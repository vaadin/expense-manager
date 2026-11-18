package com.vaadin.expensemanager.allowance.ui;

import com.vaadin.expensemanager.base.ui.AbstractUiTest;
import java.math.BigDecimal;
import java.util.List;

import com.vaadin.expensemanager.allowance.AllowanceRateService;
import com.vaadin.expensemanager.base.TableSnapshot;
import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.base.ui.ReferenceTabs;
import com.vaadin.expensemanager.reference.ui.ExpenseTypeView;
import com.vaadin.expensemanager.reference.ui.VatRateView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedBigDecimalField;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedIntegerField;
import com.vaadin.testing.unified.component.UnifiedMenuBar;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.component.UnifiedTextField;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified view test (pyramid layer 3, ADR-0012) for
 * {@link AllowanceRatesView} — the ADMIN-only allowance-rate settings screen
 * (issue #48, redesigned to frame {@code 156:5396} in #169).
 *
 * <p>Drives the tab bar, the toolbar, the rate card's ⋮ menus, the searchable
 * foreign per-diem grid and both year-seeding editors as an admin would. Access
 * control mirrors the reference-data screens. The singleton Testcontainers
 * Postgres follows {@code AbstractReferenceDataViewUiTest} (F-008), and so does
 * the clean slate: a {@link TableSnapshot} of the four rate tables, restored after
 * each test, since a browser's request threads never see a test transaction.
 *
 * <p><strong>What is deliberately not here.</strong> Whether the dotted rules,
 * the 40px heading, the tab pill and the 512px grid actually <em>look</em> right
 * is not something a component tree can judge, and neither is a 3% Aura hover
 * overlay. This class asserts the structure and the behaviour; appearance goes
 * to visual verification against the frame.
 */
class AllowanceRatesViewUiTest extends AbstractUiTest {

    /** Resolves the signed-in user for the tab bar's access filter. */
    @Autowired
    protected AuthenticationContext authenticationContext;

    @Autowired
    private AllowanceRateService service;

    @Autowired
    private JdbcTemplate jdbc;

    private TableSnapshot rates;

    @BeforeEach
    void snapshotRates() {
        rates = TableSnapshot.take(jdbc, "domestic_per_diem_rate", "kilometre_rate",
                "meal_allowance_rate", "foreign_per_diem_rate");
    }

    @AfterEach
    void restoreRates() {
        rates.restore();
    }

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesViewWithSeeded2026Rendered(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        assertThat(yearSelector(page)).hasSelectedItem("2026");

        UnifiedGrid grid = page.find(UnifiedGrid.class);
        assertThat(grid).hasRowCount(12);
        assertThat(grid.row(0)).hasCellText("Country", "Belgium");
        assertThat(grid.row(9)).hasCellText("Country", "Sweden");
        assertThat(grid.row(9)).hasCellText("Per-diem", "€68.00");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachRouteByUrl(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        // Access control answers a denied route with the not-found page, never the grid.
        assertThat(page.findComponent(H2.class).withText("Page not found")).exists();
        assertThat(page.find(UnifiedGrid.class)).doesNotExist();
    }

    // ---------------------------------------------------------- the tab bar

    /**
     * The three reference routes as real links, with this route's tab selected.
     * The bar replaced the shell's Reference Tables menu, so it is the only way
     * into two of the three — hence links rather than a selection listener.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theTabBarLinksToAllThreeReferenceRoutes(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        assertThat(tabLinks(page)).extracting(link -> link.getText().strip())
                .containsExactly("VAT Rates", "Expense Types", "Allowance Rates");
        assertThat(tabLinks(page)).extracting(link -> link.getAttribute("href"))
                .containsExactly("vat-rates", "expense-types", "allowance-rates");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void theCurrentRoutesTabIsSelectedAndItsLinkIsAriaCurrent(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        assertThat(tabs(page)).hasSelectedTab("Allowance Rates");
        // aria-current is the half a screen reader reads; the selected pill is the
        // sighted half of the same fact, and it goes on the LINK, not the Tab.
        assertThat(tabLinks(page).stream()
                .filter(link -> "page".equals(link.getAttribute("aria-current")))
                .map(link -> link.getText().strip()))
                .containsExactly("Allowance Rates");
    }

    /**
     * A route the user cannot reach renders no tab at all, rather than a greyed
     * one (ADR-0008). All three views are ADMIN-only today, so the bar is
     * all-or-nothing — this is what keeps that true when one of them opens up.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void aUserWhoCannotReachARouteGetsNoTabForIt(UnifiedPage page) {
        page.navigate(DashboardView.class);

        var tabs = new ReferenceTabs(VatRateView.class, authenticationContext);

        assertThat(tabs.getChildren()).isEmpty();
        assertThat(tabs.getSelectedIndex()).isEqualTo(-1);
    }

    /** The bar is shared: the other two reference views host the same component. */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theOtherTwoReferenceViewsHostTheSameBarWithTheirOwnTabSelected(UnifiedPage page) {
        page.navigate(VatRateView.class);
        assertThat(tabs(page)).hasSelectedTab("VAT Rates");

        page.navigate(ExpenseTypeView.class);
        assertThat(tabs(page)).hasSelectedTab("Expense Types");
    }

    // ----------------------------------------------------------- the toolbar

    /**
     * Year selector left, both seeding actions right — and the selector carries
     * no visible label, so it must carry an accessible name instead.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theToolbarSelectorIsUnlabelledButNamed(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        UnifiedComboBox selector = page.find(UnifiedComboBox.class).withAccessibleName("Year");

        assertThat(selector).exists();
        String label = selector.element().getProperty("label");
        assertThat(label == null || label.isBlank()).as("no visible label").isTrue();
        assertThat(selector.element().getInlineStyle("width")).isEqualTo("180px");
    }

    /** Both are tertiary: with two side by side, neither is the page's loud action. */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void bothYearActionsAreTertiary(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        String tertiary = ButtonVariant.TERTIARY.getVariantName();

        assertThat(page.find(UnifiedButton.class).withText("Copy Year").withTheme(tertiary)).exists();
        assertThat(page.find(UnifiedButton.class).withText("Add Year").withTheme(tertiary)).exists();
    }

    // ------------------------------------------------------- the rate card

    /**
     * One card, three rows — not three cards. The domestic row keeps both of its
     * amounts, separated by the dot.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theThreeRatesRenderAsOneCardWithTheDomesticRowCarryingTwoValues(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        UnifiedDiv card = page.find(UnifiedDiv.class).withClassName("rate-list-card");
        assertThat(card).hasCount(1);
        List<UnifiedElement> children = card.element().children();
        assertThat(children).hasSize(3);
        assertThat(children).allSatisfy(row ->
                assertThat(row.getAttribute("class")).contains("rate-row"));

        assertThat(card.find(UnifiedSpan.class).withClassName("rate-row-title").all())
                .extracting(title -> title.getText().strip())
                .containsExactly("Domestic per Diem", "Kilometre Allowance",
                        "Meal Allowance");

        UnifiedDiv domestic = rateRow(card, "Domestic per Diem");
        assertThat(domestic.find(UnifiedSpan.class).withClassName("rate-row-value")).hasCount(2);
        assertThat(domestic.find(UnifiedSpan.class).withClassName("rate-row-dot")).hasCount(1);
        for (String text : new String[] { "Full day (over 10h)", "€54.00",
                "Partial day (over 6h)", "€25.00" }) {
            assertThat(domestic).hasTextContaining(text);
        }

        // The single-value rows carry one value and no dot.
        assertThat(rateRow(card, "Kilometre Allowance").find(UnifiedSpan.class)
                .withClassName("rate-row-dot")).doesNotExist();
        assertThat(rateRow(card, "Meal Allowance")).hasTextContaining("€13.50");
    }

    // ------------------------------------------------------ row action menus

    /**
     * Every row action is behind a ⋮ whose trigger names its row and whose items
     * are text-labelled — an icon-only trigger with no accessible name announces
     * as "button" and nothing else.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void everyRowActionSitsBehindANamedEllipsisMenu(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        List<UnifiedMenuBar> menus = page.find(UnifiedMenuBar.class)
                .withClassName("row-action-menu").all();
        assertThat(menus).extracting(AllowanceRatesViewUiTest::triggerName)
                .contains("Actions for Domestic per Diem",
                        "Actions for Kilometre Allowance",
                        "Actions for Meal Allowance");

        // Every item in every menu carries text, never an icon alone (ADR-0020).
        assertThat(menus).allSatisfy(menu ->
                assertThat(menu.getItems(triggerName(menu)))
                        .isNotEmpty()
                        .allSatisfy(item -> assertThat(item).isNotBlank()));

        UnifiedGrid grid = page.find(UnifiedGrid.class);
        assertThat(triggerName(gridRowMenu(grid, "Sweden"))).isEqualTo("Actions for Sweden");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editingARateThroughItsMenuPersists(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        rateRowMenu(page, "Meal Allowance").clickItem("Actions for Meal Allowance", "Edit");
        UnifiedDialog editor = page.find(UnifiedDialog.class);
        editor.find(UnifiedBigDecimalField.class).withLabel("Amount (€)").setValue(new BigDecimal("15"));
        editor.find(UnifiedButton.class).withText("Save").click();

        assertThat(rateRow(page.find(UnifiedDiv.class).withClassName("rate-list-card"),
                "Meal Allowance")).hasTextContaining("€15.00");
        assertThat(service.mealAllowance(2026).orElseThrow().amount())
                .isEqualByComparingTo("15.00");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editingAForeignPerDiemThroughItsGridMenuPersists(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        gridRowMenu(grid, "Sweden").clickItem("Actions for Sweden", "Edit");
        UnifiedDialog editor = page.find(UnifiedDialog.class);
        editor.find(UnifiedBigDecimalField.class).withLabel("Amount (€)").setValue(new BigDecimal("70"));
        editor.find(UnifiedButton.class).withText("Save").click();

        assertThat(grid.row().withCellText("Country", "Sweden")).hasCellText("Per-diem", "€70.00");
        assertThat(service.foreignPerDiem(2026, "Sweden").orElseThrow().amount())
                .isEqualByComparingTo("70.00");
    }

    // --------------------------------------------------- the searchable grid

    @Test
    @WithUserDetails("admin@vaadin.com")
    void searchFiltersTheForeignPerDiemsByCountry(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);
        UnifiedTextField search = page.find(UnifiedTextField.class).withPlaceholder("Search");
        assertThat(grid).hasRowCount(12);

        search.setValue("swe");

        assertThat(grid).hasRowCount(1);
        assertThat(grid.row(0)).hasCellText("Country", "Sweden");

        // Clearing restores every row — the filter reads the field rather than
        // stacking a new one per keystroke.
        search.setValue("");
        assertThat(grid).hasRowCount(12);
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void theGridIsStripedAndFixedHeightRatherThanShowingEveryRow(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        // The theme-agnostic constant; the LUMO_ pair is inert under Aura.
        UnifiedGrid grid = page.find(UnifiedGrid.class)
                .withTheme(GridVariant.ROW_STRIPES.getVariantName());

        assertThat(grid).exists();
        assertThat(grid.element().getInlineStyle("height")).isEqualTo("512px");
        assertThat(grid.element().getPropertyBoolean("allRowsVisible")).isNotEqualTo(Boolean.TRUE);
    }

    // ------------------------------------------------------ add / copy year

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addYearSeedsFromTheLatestYearAndSelectsIt(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        page.find(UnifiedButton.class).withText("Add Year").click();
        UnifiedDialog editor = page.find(UnifiedDialog.class);
        editor.find(UnifiedIntegerField.class).withLabel("Year").setValue(2027);
        editor.find(UnifiedButton.class).withText("Save").click();

        // The new year is selected and its foreign per-diems were copied.
        assertThat(yearSelector(page)).hasSelectedItem("2027");
        assertThat(page.find(UnifiedGrid.class)).hasRowCount(12);
        assertThat(service.availableYears()).containsExactly(2027, 2026);
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void copyYearSeedsANamedTargetFromANamedSourceAndSelectsIt(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        copyYear(page, "2026", 2030);

        assertThat(yearSelector(page)).hasSelectedItem("2030");
        assertThat(service.availableYears()).containsExactly(2030, 2026);
        assertThat(service.mealAllowance(2030)).isPresent();
        assertThat(service.foreignPerDiems(2030)).hasSize(12);
    }

    /**
     * The target must not already exist, and the refusal lands in the dialog's
     * own error summary with the dialog still open — never the generic error
     * dialog (ADR-0020).
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void copyYearRefusesAnExistingTargetInTheDialogsErrorSummary(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        copyYear(page, "2026", 2026);

        UnifiedDialog editor = page.find(UnifiedDialog.class);
        UnifiedDiv summary = editor.find(UnifiedDiv.class).withClassName("error-summary");
        assertThat(summary).hasTextContaining("Please fix the following:");
        assertThat(summary).hasTextContaining("Year 2026 already has allowance rates");
        // Still open, and nothing was written.
        assertThat(editor).isOpen();
        assertThat(service.availableYears()).containsExactly(2026);
    }

    // --------------------------------------------------------- add a country

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addCountryThroughEditorAppendsRow(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);
        assertThat(grid).hasRowCount(12);

        // "Add", not "Add country": the section heading beside it carries the noun.
        page.find(UnifiedButton.class).withText("Add").click();
        UnifiedDialog editor = page.find(UnifiedDialog.class);
        editor.find(UnifiedTextField.class).withLabel("Country").setValue("Japan");
        editor.find(UnifiedBigDecimalField.class).withLabel("Amount (€)").setValue(new BigDecimal("85"));
        editor.find(UnifiedButton.class).withText("Save").click();

        assertThat(grid).hasRowCount(13);
        assertThat(service.foreignPerDiem(2026, "Japan")).isPresent();
    }

    // ---- helpers -----------------------------------------------------------

    private static UnifiedComboBox yearSelector(UnifiedPage page) {
        return page.find(UnifiedComboBox.class).withAccessibleName("Year");
    }

    private static UnifiedTabs tabs(UnifiedPage page) {
        return page.find(UnifiedTabs.class).withClassName("reference-tabs");
    }

    /** The links inside the tab bar, in bar order. */
    private static List<UnifiedElement> tabLinks(UnifiedPage page) {
        return tabs(page).element().findDescendants("a");
    }

    /** The rate card's row with the given title. */
    private static UnifiedDiv rateRow(UnifiedDiv card, String title) {
        return card.find(UnifiedDiv.class).withClassName("rate-row")
                .where(row -> row.getText().contains(title));
    }

    /** The ⋮ trigger's accessible name (its only item, an icon), which must identify its row. */
    private static String triggerName(UnifiedMenuBar menu) {
        return menu.getItems().getFirst();
    }

    private static UnifiedMenuBar rateRowMenu(UnifiedPage page, String rowTitle) {
        return page.find(UnifiedMenuBar.class).withClassName("row-action-menu")
                .withItem("Actions for " + rowTitle);
    }

    private static UnifiedMenuBar gridRowMenu(UnifiedGrid grid, String country) {
        return grid.row().withCellText("Country", country).cell("Actions").find(UnifiedMenuBar.class);
    }

    private static void copyYear(UnifiedPage page, String source, int target) {
        page.step("Copy year " + source + " to " + target, () -> {
            page.find(UnifiedButton.class).withText("Copy Year").click();
            UnifiedDialog editor = page.find(UnifiedDialog.class);
            editor.find(UnifiedComboBox.class).withLabel("Source year").selectItem(source);
            editor.find(UnifiedIntegerField.class).withLabel("Target year").setValue(target);
            editor.find(UnifiedButton.class).withText("Save").click();
        });
    }
}
