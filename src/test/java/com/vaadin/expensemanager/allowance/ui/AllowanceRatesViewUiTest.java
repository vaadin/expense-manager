package com.vaadin.expensemanager.allowance.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.expensemanager.allowance.AllowanceRateService;
import com.vaadin.expensemanager.base.AbstractUiTest;
import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.base.ui.ReferenceTabs;
import com.vaadin.expensemanager.base.ui.unified.UnifiedElements;
import com.vaadin.expensemanager.base.ui.unified.UnifiedMenuBar;
import com.vaadin.expensemanager.reference.ui.ExpenseTypeView;
import com.vaadin.expensemanager.reference.ui.VatRateView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.component.UnifiedBigDecimalField;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedIntegerField;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.component.UnifiedTextField;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0027 proposed) for
 * {@link AllowanceRatesView} — the ADMIN-only allowance-rate settings screen
 * (issue #48, redesigned to frame {@code 156:5396} in #169). Runs unchanged on
 * Browserless and Playwright.
 *
 * <p>Drives the tab bar, the toolbar, the rate card's ⋮ menus, the searchable
 * foreign per-diem grid and both year-seeding editors as an admin would. Access
 * control mirrors the reference-data screens. Saved state is checked through
 * {@link AllowanceRateService}, which is in the test JVM on every tool, and the
 * {@code DatabaseSnapshotExtension} puts every committed write back afterwards.
 *
 * <p><strong>Tool differences this class is written around.</strong>
 * <ul>
 *   <li>A component in a Grid cell is reached only through
 *       {@code row.cell("Actions")} (F-085); a page-wide lookup sees it in a
 *       browser only. So the "every item is text-labelled" check covers the rate
 *       card's menus plus one grid row's, the same set on both tools.</li>
 *   <li>The ⋮ menus go through {@link UnifiedMenuBar}: in a browser it opens the
 *       sub-menu to read and click it, on Browserless it uses the tester.</li>
 *   <li>The year selector is found by its {@code accessibleName} property, not
 *       {@code withAccessibleName("Year")}, which matches in a browser only: Flow's
 *       {@code setAriaLabel} on a field sets that property, and Browserless looks
 *       for an {@code aria-label} attribute on the host.</li>
 *   <li>Sizes are read from the inline {@code style} attribute, which Flow writes
 *       as {@code width:180px} and the browser serialises as
 *       {@code width: 180px;} — compared with whitespace and {@code ;} stripped.</li>
 * </ul>
 *
 * <p><strong>What is deliberately not here.</strong> Whether the dotted rules,
 * the 40px heading, the tab pill and the 512px grid actually <em>look</em> right,
 * or a 3% Aura hover overlay. This class asserts the structure and the behaviour;
 * appearance goes to visual verification against the frame (screenshots with
 * {@code -Dui.screenshots=true} on Playwright help with that).
 */
class AllowanceRatesViewUiTest extends AbstractUiTest {

    private static final String ADMIN_EMAIL = "admin@vaadin.com";

    /** Resolves the signed-in user for the tab bar's access filter. */
    @Autowired
    private AuthenticationContext authenticationContext;

    @Autowired
    private AllowanceRateService service;

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void adminReachesViewWithSeeded2026Rendered(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        screenshot(page, "seeded-2026");

        assertThat(yearSelector(page).getSelectedItem()).isEqualTo("2026");

        var grid = grid(page);
        assertThat(grid).hasRowCount(12);
        assertThat(grid.row(0).getCellText("Country")).isEqualTo("Belgium");
        assertThat(grid.row(9).getCellText("Country")).isEqualTo("Sweden");
        assertThat(grid.row(9).getCellText("Per-diem")).isEqualTo("€68.00");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachRouteByUrl(UnifiedPage page) {
        page.navigate(DashboardView.class);
        page.navigate("allowance-rates");

        // Denied: neither the year selector nor the per-diem grid renders.
        assertThat(page.find(UnifiedComboBox.class).exists()).isFalse();
        assertThat(page.find(UnifiedGrid.class).exists()).isFalse();
    }

    // ---------------------------------------------------------- the tab bar

    /**
     * The three reference routes as real links, with this route's tab selected.
     * The bar replaced the shell's Reference Tables menu, so it is the only way
     * into two of the three — hence links rather than a selection listener.
     */
    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void theTabBarLinksToAllThreeReferenceRoutes(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        screenshot(page, "tab-bar");

        var links = tabLinks(page);
        assertThat(links).extracting(link -> link.element().getText())
                .containsExactly("VAT Rates", "Expense Types", "Allowance Rates");
        assertThat(links).extracting(link -> link.element().getAttribute("href"))
                .containsExactly("vat-rates", "expense-types", "allowance-rates");
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void theCurrentRoutesTabIsSelectedAndItsLinkIsAriaCurrent(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        assertThat(UnifiedElements.selectedTabText(tabs(page))).isEqualTo("Allowance Rates");
        // aria-current is the half a screen reader reads; the selected pill is the
        // sighted half of the same fact, and it goes on the LINK, not the Tab.
        assertThat(tabLinks(page).stream()
                .filter(link -> "page".equals(link.element().getAttribute("aria-current")))
                .map(link -> link.element().getText()))
                .containsExactly("Allowance Rates");
    }

    /**
     * A route the user cannot reach renders no tab at all, rather than a greyed
     * one (ADR-0008). All three views are ADMIN-only today, so the bar is
     * all-or-nothing — this is what keeps that true when one of them opens up.
     *
     * <p>Builds the bar directly, as before: no view a plain user can reach hosts
     * it. Portable as it stands — the component is built in the test JVM, and the
     * access filter reads the test thread's {@code @WithUserDetails} user; it
     * builds no {@code RouterLink} (which would need a running UI) because the
     * user can reach none of the routes.
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
    @WithUserDetails(ADMIN_EMAIL)
    void theOtherTwoReferenceViewsHostTheSameBarWithTheirOwnTabSelected(UnifiedPage page) {
        page.navigate(VatRateView.class);
        assertThat(UnifiedElements.selectedTabText(tabs(page))).isEqualTo("VAT Rates");

        page.navigate(ExpenseTypeView.class);
        assertThat(UnifiedElements.selectedTabText(tabs(page))).isEqualTo("Expense Types");
    }

    // ----------------------------------------------------------- the toolbar

    /**
     * Year selector left, both seeding actions right — and the selector carries
     * no visible label, so it must carry an accessible name instead.
     */
    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void theToolbarSelectorIsUnlabelledButNamed(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        // Found by its accessible name: that is the "named" half of the contract.
        var selector = yearSelector(page);

        assertThat(selector.getLabel()).isNull();
        assertThat(inlineStyle(selector)).contains("width:180px");
    }

    /** Both are tertiary: with two side by side, neither is the page's loud action. */
    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void bothYearActionsAreTertiary(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        assertThat(themeNames(button(page, "Copy Year")))
                .contains(ButtonVariant.TERTIARY.getVariantName());
        assertThat(themeNames(button(page, "Add Year")))
                .contains(ButtonVariant.TERTIARY.getVariantName());
    }

    // ------------------------------------------------------- the rate card

    /**
     * One card, three rows — not three cards. The domestic row keeps both of its
     * amounts, separated by the dot.
     */
    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void theThreeRatesRenderAsOneCardWithTheDomesticRowCarryingTwoValues(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        screenshot(page, "rate-card");

        var rows = rateRows(page);
        assertThat(rows).hasSize(3);

        assertThat(rows).extracting(AllowanceRatesViewUiTest::titleOf)
                .containsExactly("Domestic per Diem", "Kilometre Allowance",
                        "Meal Allowance");

        var domestic = rows.get(0);
        assertThat(spans(domestic, "rate-row-value")).hasSize(2);
        assertThat(spans(domestic, "rate-row-dot")).hasSize(1);
        assertThat(domestic.getText())
                .contains("Full day (over 10h)", "€54.00",
                        "Partial day (over 6h)", "€25.00");

        // The single-value rows carry one value and no dot.
        assertThat(spans(rows.get(1), "rate-row-dot")).isEmpty();
        assertThat(rows.get(2).getText()).contains("€13.50");
    }

    // ------------------------------------------------------ row action menus

    /**
     * Every row action is behind a ⋮ whose trigger names its row and whose items
     * are text-labelled — an icon-only trigger with no accessible name announces
     * as "button" and nothing else.
     */
    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void everyRowActionSitsBehindANamedEllipsisMenu(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        var cardMenus = rateCard(page).find(UnifiedMenuBar.class).all();
        assertThat(cardMenus)
                .extracting(AllowanceRatesViewUiTest::triggerName)
                .containsExactly("Actions for Domestic per Diem",
                        "Actions for Kilometre Allowance",
                        "Actions for Meal Allowance");

        var swedenMenu = gridRowMenu(page, "Sweden");
        assertThat(triggerName(swedenMenu)).isEqualTo("Actions for Sweden");

        // Every item in every menu carries text, never an icon alone (ADR-0020).
        var menus = new ArrayList<>(cardMenus);
        menus.add(swedenMenu);
        assertThat(menus).allSatisfy(menu ->
                assertThat(menu.getSubMenuItemTexts(0))
                        .isNotEmpty()
                        .allSatisfy(text -> assertThat(text).isNotBlank()));
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void editingARateThroughItsMenuPersists(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        rateRowMenu(page, "Meal Allowance").clickSubMenuItem(0, "Edit");
        screenshot(page, "meal-editor");
        amountField(page).setValue(new BigDecimal("15"));
        button(page, "Save").click();

        assertThat(service.mealAllowance(2026).orElseThrow().amount())
                .isEqualByComparingTo("15.00");
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void editingAForeignPerDiemThroughItsGridMenuPersists(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        gridRowMenu(page, "Sweden").clickSubMenuItem(0, "Edit");
        screenshot(page, "foreign-editor");
        amountField(page).setValue(new BigDecimal("70"));
        button(page, "Save").click();

        assertThat(grid(page).rows().withCellText("Country", "Sweden").single())
                .hasCellText("Per-diem", "€70.00");
        assertThat(service.foreignPerDiem(2026, "Sweden").orElseThrow().amount())
                .isEqualByComparingTo("70.00");
    }

    // --------------------------------------------------- the searchable grid

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void searchFiltersTheForeignPerDiemsByCountry(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        assertThat(grid(page)).hasRowCount(12);

        search(page).setValue("swe");
        screenshot(page, "search-swe");

        assertThat(grid(page)).hasRowCount(1);
        assertThat(grid(page).row(0).getCellText("Country")).isEqualTo("Sweden");

        // Clearing restores every row — the filter reads the field rather than
        // stacking a new one per keystroke.
        search(page).setValue("");
        assertThat(grid(page)).hasRowCount(12);
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void theGridIsStripedAndFixedHeightRatherThanShowingEveryRow(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        var grid = grid(page);

        // The theme-agnostic constant; the LUMO_ pair is inert under Aura.
        assertThat(themeNames(grid)).contains(GridVariant.ROW_STRIPES.getVariantName());
        assertThat(inlineStyle(grid)).contains("height:512px");
        assertThat(grid.element().getPropertyBoolean("allRowsVisible")).isFalse();
    }

    // ------------------------------------------------------ add / copy year

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void addYearSeedsFromTheLatestYearAndSelectsIt(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        button(page, "Add Year").click();
        screenshot(page, "add-year-dialog");
        integerField(page, "Year").setValue(2027);
        button(page, "Save").click();
        screenshot(page, "year-2027");

        // The new year is selected and its foreign per-diems were copied.
        assertThat(yearSelector(page).getSelectedItem()).isEqualTo("2027");
        assertThat(grid(page)).hasRowCount(12);
        assertThat(service.availableYears()).containsExactly(2027, 2026);
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void copyYearSeedsANamedTargetFromANamedSourceAndSelectsIt(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        button(page, "Copy Year").click();
        screenshot(page, "copy-year-dialog");
        page.find(UnifiedComboBox.class).withLabel("Source year").single().selectItem("2026");
        integerField(page, "Target year").setValue(2030);
        button(page, "Save").click();

        assertThat(yearSelector(page).getSelectedItem()).isEqualTo("2030");
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
    @WithUserDetails(ADMIN_EMAIL)
    void copyYearRefusesAnExistingTargetInTheDialogsErrorSummary(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        button(page, "Copy Year").click();
        page.find(UnifiedComboBox.class).withLabel("Source year").single().selectItem("2026");
        integerField(page, "Target year").setValue(2026);
        button(page, "Save").click();
        screenshot(page, "existing-target-refused");

        var summary = page.find(UnifiedDiv.class).withClassName("error-summary").single();
        assertThat(summary).isVisible();
        assertThat(summary.getText())
                .contains("Please fix the following:")
                .contains("Year 2026 already has allowance rates");
        // Still open, and nothing was written.
        assertThat(button(page, "Save")).isVisible();
        assertThat(service.availableYears()).containsExactly(2026);
    }

    // --------------------------------------------------------- add a country

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void addCountryThroughEditorAppendsRow(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        int before = grid(page).getRowCount();

        // "Add", not "Add country": the section heading beside it carries the noun.
        button(page, "Add").click();
        screenshot(page, "add-country-dialog");
        page.find(UnifiedTextField.class).withLabel("Country").single().setValue("Japan");
        amountField(page).setValue(new BigDecimal("85"));
        button(page, "Save").click();
        screenshot(page, "japan-added");

        assertThat(grid(page)).hasRowCount(before + 1);
        assertThat(service.foreignPerDiem(2026, "Japan")).isPresent();
    }

    // ---- helpers -----------------------------------------------------------

    /**
     * The toolbar's unlabelled year selector, by its accessible name. Not
     * {@code withAccessibleName("Year")}: a Flow field's {@code setAriaLabel} sets
     * the {@code accessibleName} property, which the web component forwards to its
     * inner {@code <input>}. A browser computes the name from there; Browserless
     * reads only an {@code aria-label} attribute on the host and finds nothing.
     * The property itself is synchronised, so both tools read it.
     */
    private static UnifiedComboBox yearSelector(UnifiedPage page) {
        return page.find(UnifiedComboBox.class)
                .where(field -> "Year".equals(field.getProperty("accessibleName")))
                .single();
    }

    private static UnifiedGrid grid(UnifiedPage page) {
        return page.find(UnifiedGrid.class).single();
    }

    private static UnifiedButton button(UnifiedPage page, String text) {
        return page.find(UnifiedButton.class).withText(text).single();
    }

    private static UnifiedTextField search(UnifiedPage page) {
        return page.find(UnifiedTextField.class).withPlaceholder("Search").single();
    }

    private static UnifiedIntegerField integerField(UnifiedPage page, String label) {
        return page.find(UnifiedIntegerField.class).withLabel(label).single();
    }

    private static UnifiedBigDecimalField amountField(UnifiedPage page) {
        return page.find(UnifiedBigDecimalField.class).withLabel("Amount (€)").single();
    }

    private static UnifiedTabs tabs(UnifiedPage page) {
        return page.find(UnifiedTabs.class).withClassName("reference-tabs").single();
    }

    /**
     * The links inside the tab bar, in bar order. The Core Set's UnifiedTabs is not a
     * Scope, so the bar is found as a generic component, which is.
     */
    private static List<UnifiedGenericComponent> tabLinks(UnifiedPage page) {
        return page.findComponent(Tabs.class).withClassName("reference-tabs").single()
                .findComponent(RouterLink.class).all();
    }

    private static UnifiedDiv rateCard(UnifiedPage page) {
        return page.find(UnifiedDiv.class).withClassName("rate-list-card").single();
    }

    private static List<UnifiedDiv> rateRows(UnifiedPage page) {
        return rateCard(page).find(UnifiedDiv.class).withClassName("rate-row").all();
    }

    private static List<UnifiedGenericComponent> spans(Scope scope, String className) {
        return scope.findComponent(Span.class).withClassName(className).all();
    }

    private static String titleOf(UnifiedDiv row) {
        return spans(row, "rate-row-title").getFirst().element().getText();
    }

    /** The ⋮ trigger's accessible name, which must identify its row. */
    private static String triggerName(UnifiedMenuBar menu) {
        return menu.getRootItemAccessibleName(0);
    }

    private static UnifiedMenuBar rateRowMenu(UnifiedPage page, String rowTitle) {
        return rateCard(page).find(UnifiedMenuBar.class).all().stream()
                .filter(menu -> ("Actions for " + rowTitle).equals(triggerName(menu)))
                .findFirst()
                .orElseThrow();
    }

    /** A grid row's ⋮ — through the row's cell, the only route on Browserless (F-085). */
    private static UnifiedMenuBar gridRowMenu(UnifiedPage page, String country) {
        return grid(page).rows().withCellText("Country", country).single()
                .cell("Actions").find(UnifiedMenuBar.class).single();
    }

    private static List<String> themeNames(UnifiedWrapper wrapper) {
        String theme = wrapper.element().getAttribute("theme");
        return theme == null ? List.of() : List.of(theme.trim().split("\\s+"));
    }

    /** The inline style, whitespace and {@code ;} stripped: Flow and the browser format it differently. */
    private static String inlineStyle(UnifiedWrapper wrapper) {
        String style = wrapper.element().getAttribute("style");
        return style == null ? "" : style.replaceAll("[\\s;]", "");
    }
}
