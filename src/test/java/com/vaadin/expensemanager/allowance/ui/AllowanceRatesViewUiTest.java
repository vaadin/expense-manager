package com.vaadin.expensemanager.allowance.ui;

import java.math.BigDecimal;
import java.util.List;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.locator.Locators;
import com.vaadin.expensemanager.allowance.AllowanceRateService;
import com.vaadin.expensemanager.allowance.ForeignPerDiemDto;
import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.base.ui.ErrorSummary;
import com.vaadin.expensemanager.base.ui.ReferenceTabs;
import com.vaadin.expensemanager.base.ui.RowActionMenu;
import com.vaadin.expensemanager.reference.ui.ExpenseTypeView;
import com.vaadin.expensemanager.reference.ui.VatRateView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.menubar.MenuBarTester;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for
 * {@link AllowanceRatesView} — the ADMIN-only allowance-rate settings screen
 * (issue #48, redesigned to frame {@code 156:5396} in #169).
 *
 * <p>Drives the tab bar, the toolbar, the rate card's ⋮ menus, the searchable
 * foreign per-diem grid and both year-seeding editors as an admin would. Access
 * control mirrors the reference-data screens. The singleton Testcontainers
 * Postgres and {@code @Transactional} rollback follow
 * {@code AbstractReferenceDataViewUiTest} (F-008: {@link SpringBrowserlessTest}
 * occupies the inheritance slot).
 *
 * <p><strong>What is deliberately not here.</strong> Whether the dotted rules,
 * the 40px heading, the tab pill and the 512px grid actually <em>look</em> right
 * is not something a browserless tree can judge, and neither is a 3% Aura hover
 * overlay. This class asserts the structure and the behaviour; appearance goes
 * to visual verification against the frame.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AllowanceRatesViewUiTest extends SpringBrowserlessTest implements Locators {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    /** Resolves the signed-in user for the tab bar's access filter. */
    @Autowired
    protected AuthenticationContext authenticationContext;

    @Autowired
    private AllowanceRateService service;

    private static final int COUNTRY_COL = 0;
    private static final int AMOUNT_COL = 1;
    private static final int ACTIONS_COL = 2;

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesViewWithSeeded2026Rendered() {
        navigate(AllowanceRatesView.class);

        assertThat(findComboBox(Integer.class).getSelected()).isEqualTo(2026);

        var grid = findGrid(ForeignPerDiemDto.class);
        assertThat(grid.size()).isEqualTo(12);
        assertThat(grid.getCellText(0, COUNTRY_COL)).isEqualTo("Belgium");
        assertThat(grid.getCellText(9, COUNTRY_COL)).isEqualTo("Sweden");
        assertThat(grid.getCellText(9, AMOUNT_COL)).isEqualTo("€68.00");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachRouteByUrl() {
        assertThatThrownBy(() -> navigate(AllowanceRatesView.class))
                .isInstanceOf(Exception.class);
    }

    // ---------------------------------------------------------- the tab bar

    /**
     * The three reference routes as real links, with this route's tab selected.
     * The bar replaced the shell's Reference Tables menu, so it is the only way
     * into two of the three — hence links rather than a selection listener.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theTabBarLinksToAllThreeReferenceRoutes() {
        navigate(AllowanceRatesView.class);
        var tabs = $(ReferenceTabs.class).single();

        assertThat(tabLinks(tabs))
                .extracting(RouterLink::getText)
                .containsExactly("VAT Rates", "Expense Types", "Allowance Rates");
        assertThat(tabLinks(tabs))
                .extracting(RouterLink::getHref)
                .containsExactly("vat-rates", "expense-types", "allowance-rates");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void theCurrentRoutesTabIsSelectedAndItsLinkIsAriaCurrent() {
        navigate(AllowanceRatesView.class);
        var tabs = $(ReferenceTabs.class).single();

        assertThat(tabs.getSelectedTab().getElement().getTextRecursively())
                .isEqualTo("Allowance Rates");
        // aria-current is the half a screen reader reads; the selected pill is the
        // sighted half of the same fact, and it goes on the LINK, not the Tab.
        assertThat(tabLinks(tabs).stream()
                .filter(link -> "page".equals(
                        link.getElement().getAttribute("aria-current")))
                .map(RouterLink::getText))
                .containsExactly("Allowance Rates");
    }

    /**
     * A route the user cannot reach renders no tab at all, rather than a greyed
     * one (ADR-0008). All three views are ADMIN-only today, so the bar is
     * all-or-nothing — this is what keeps that true when one of them opens up.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void aUserWhoCannotReachARouteGetsNoTabForIt() {
        navigate(DashboardView.class);

        var tabs = new ReferenceTabs(VatRateView.class, authenticationContext);

        assertThat(tabs.getChildren()).isEmpty();
        assertThat(tabs.getSelectedIndex()).isEqualTo(-1);
    }

    /** The bar is shared: the other two reference views host the same component. */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theOtherTwoReferenceViewsHostTheSameBarWithTheirOwnTabSelected() {
        navigate(VatRateView.class);
        assertThat($(ReferenceTabs.class).single().getSelectedTab().getElement()
                .getTextRecursively()).isEqualTo("VAT Rates");

        navigate(ExpenseTypeView.class);
        assertThat($(ReferenceTabs.class).single().getSelectedTab().getElement()
                .getTextRecursively()).isEqualTo("Expense Types");
    }

    // ----------------------------------------------------------- the toolbar

    /**
     * Year selector left, both seeding actions right — and the selector carries
     * no visible label, so it must carry an accessible name instead.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theToolbarSelectorIsUnlabelledButNamed() {
        navigate(AllowanceRatesView.class);
        var selector = findComboBox(Integer.class).getComponent();

        assertThat(selector.getLabel()).isNull();
        assertThat(selector.getAriaLabel()).contains("Year");
        assertThat(selector.getWidth()).isEqualTo("180px");
    }

    /** Both are tertiary: with two side by side, neither is the page's loud action. */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void bothYearActionsAreTertiary() {
        navigate(AllowanceRatesView.class);

        assertThat(findButton().withText("Copy Year").component().getThemeNames())
                .contains(ButtonVariant.TERTIARY.getVariantName());
        assertThat(findButton().withText("Add Year").component().getThemeNames())
                .contains(ButtonVariant.TERTIARY.getVariantName());
    }

    // ------------------------------------------------------- the rate card

    /**
     * One card, three rows — not three cards. The domestic row keeps both of its
     * amounts, separated by the dot.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theThreeRatesRenderAsOneCardWithTheDomesticRowCarryingTwoValues() {
        navigate(AllowanceRatesView.class);

        var card = $(Div.class).withClassName("rate-list-card").single();
        var rows = card.getChildren().toList();
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> assertThat(
                row.getElement().getClassList()).contains("rate-row"));

        assertThat(rows).extracting(row -> titleOf(row))
                .containsExactly("Domestic per Diem", "Kilometre Allowance",
                        "Meal Allowance");

        var domestic = rows.get(0);
        assertThat(find(Span.class, domestic).withClassName("rate-row-value").all())
                .hasSize(2);
        assertThat(find(Span.class, domestic).withClassName("rate-row-dot").all())
                .hasSize(1);
        assertThat(domestic.getElement().getTextRecursively())
                .contains("Full day (over 10h)", "€54.00",
                        "Partial day (over 6h)", "€25.00");

        // The single-value rows carry one value and no dot.
        assertThat(find(Span.class, rows.get(1)).withClassName("rate-row-dot").exists())
                .isFalse();
        assertThat(rows.get(2).getElement().getTextRecursively()).contains("€13.50");
    }

    // ------------------------------------------------------ row action menus

    /**
     * Every row action is behind a ⋮ whose trigger names its row and whose items
     * are text-labelled — an icon-only trigger with no accessible name announces
     * as "button" and nothing else.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void everyRowActionSitsBehindANamedEllipsisMenu() {
        navigate(AllowanceRatesView.class);

        assertThat($(RowActionMenu.class).all())
                .extracting(AllowanceRatesViewUiTest::triggerName)
                .contains("Actions for Domestic per Diem",
                        "Actions for Kilometre Allowance",
                        "Actions for Meal Allowance");

        // Every item in every menu carries text, never an icon alone (ADR-0020).
        assertThat($(RowActionMenu.class).all()).allSatisfy(menu ->
                assertThat(menu.getItems().get(0).getSubMenu().getItems())
                        .isNotEmpty()
                        .allSatisfy(item -> assertThat(item.getText()).isNotBlank()));

        var grid = findGrid(ForeignPerDiemDto.class);
        var cell = grid.getCellComponent(9, ACTIONS_COL);
        assertThat(triggerName(find(RowActionMenu.class, cell).single()))
                .isEqualTo("Actions for Sweden");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editingARateThroughItsMenuPersists() {
        navigate(AllowanceRatesView.class);

        clickRowAction(rateRowMenu("Meal Allowance"), "Edit");
        findBigDecimalField().withLabel("Amount (€)").setValue(new BigDecimal("15"));
        findButton().withText("Save").click();

        assertThat(service.mealAllowance(2026).orElseThrow().amount())
                .isEqualByComparingTo("15.00");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editingAForeignPerDiemThroughItsGridMenuPersists() {
        navigate(AllowanceRatesView.class);
        var cell = findGrid(ForeignPerDiemDto.class)
                .getCellComponent(9, ACTIONS_COL);

        clickRowAction(find(RowActionMenu.class, cell).single(), "Edit");
        findBigDecimalField().withLabel("Amount (€)").setValue(new BigDecimal("70"));
        findButton().withText("Save").click();

        assertThat(service.foreignPerDiem(2026, "Sweden").orElseThrow().amount())
                .isEqualByComparingTo("70.00");
    }

    // --------------------------------------------------- the searchable grid

    @Test
    @WithUserDetails("admin@vaadin.com")
    void searchFiltersTheForeignPerDiemsByCountry() {
        navigate(AllowanceRatesView.class);
        var grid = findGrid(ForeignPerDiemDto.class);
        assertThat(grid.size()).isEqualTo(12);

        findTextField().withPlaceholder("Search").setValue("swe");

        assertThat(grid.size()).isEqualTo(1);
        assertThat(grid.getCellText(0, COUNTRY_COL)).isEqualTo("Sweden");

        // Clearing restores every row — the filter reads the field rather than
        // stacking a new one per keystroke.
        findTextField().withPlaceholder("Search").setValue("");
        assertThat(grid.size()).isEqualTo(12);
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void theGridIsStripedAndFixedHeightRatherThanShowingEveryRow() {
        navigate(AllowanceRatesView.class);
        var grid = findGrid(ForeignPerDiemDto.class).getComponent();

        // The theme-agnostic constant; the LUMO_ pair is inert under Aura.
        assertThat(grid.getThemeNames())
                .contains(GridVariant.ROW_STRIPES.getVariantName());
        assertThat(grid.getHeight()).isEqualTo("512px");
        assertThat(grid.isAllRowsVisible()).isFalse();
    }

    // ------------------------------------------------------ add / copy year

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addYearSeedsFromTheLatestYearAndSelectsIt() {
        navigate(AllowanceRatesView.class);

        findButton().withText("Add Year").click();
        findIntegerField().withLabel("Year").setValue(2027);
        findButton().withText("Save").click();

        // The new year is selected and its foreign per-diems were copied.
        assertThat(findComboBox(Integer.class).getSelected()).isEqualTo(2027);
        assertThat(findGrid(ForeignPerDiemDto.class).size()).isEqualTo(12);
        assertThat(service.availableYears()).containsExactly(2027, 2026);
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void copyYearSeedsANamedTargetFromANamedSourceAndSelectsIt() {
        navigate(AllowanceRatesView.class);

        findButton().withText("Copy Year").click();
        findComboBox(Integer.class).withLabel("Source year").selectItem("2026");
        findIntegerField().withLabel("Target year").setValue(2030);
        findButton().withText("Save").click();

        assertThat(findComboBox(Integer.class).getSelected()).isEqualTo(2030);
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
    void copyYearRefusesAnExistingTargetInTheDialogsErrorSummary() {
        navigate(AllowanceRatesView.class);

        findButton().withText("Copy Year").click();
        findComboBox(Integer.class).withLabel("Source year").selectItem("2026");
        findIntegerField().withLabel("Target year").setValue(2026);
        findButton().withText("Save").click();

        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("Please fix the following:")
                .contains("Year 2026 already has allowance rates");
        assertThat(find(ErrorSummary.class).all()).isNotEmpty();
        // Still open, and nothing was written.
        assertThat(findButton().withText("Save").exists()).isTrue();
        assertThat(service.availableYears()).containsExactly(2026);
    }

    // --------------------------------------------------------- add a country

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addCountryThroughEditorAppendsRow() {
        navigate(AllowanceRatesView.class);
        int before = findGrid(ForeignPerDiemDto.class).size();

        // "Add", not "Add country": the section heading beside it carries the noun.
        findButton().withText("Add").click();
        findTextField().withLabel("Country").setValue("Japan");
        findBigDecimalField().withLabel("Amount (€)").setValue(new BigDecimal("85"));
        findButton().withText("Save").click();

        assertThat(findGrid(ForeignPerDiemDto.class).size()).isEqualTo(before + 1);
        assertThat(service.foreignPerDiem(2026, "Japan")).isPresent();
    }

    // ---- helpers -----------------------------------------------------------

    /** The links inside the tab bar, in bar order. */
    private static List<RouterLink> tabLinks(ReferenceTabs tabs) {
        return tabs.getChildren()
                .map(Tab.class::cast)
                .flatMap(Component::getChildren)
                .map(RouterLink.class::cast)
                .toList();
    }

    /** The ⋮ trigger's accessible name, which must identify its row. */
    private static String triggerName(RowActionMenu menu) {
        return menu.getItems().get(0).getElement().getAttribute("aria-label");
    }

    private static String titleOf(Component row) {
        return row.getChildren()
                .filter(child -> child.getElement().getClassList()
                        .contains("rate-row-title"))
                .map(child -> child.getElement().getText())
                .findFirst()
                .orElseThrow();
    }

    private RowActionMenu rateRowMenu(String rowTitle) {
        return $(RowActionMenu.class)
                .withCondition(menu -> ("Actions for " + rowTitle)
                        .equals(triggerName(menu)))
                .single();
    }

    /** Opens a row's ⋮ and activates the named action. */
    private void clickRowAction(RowActionMenu menu, String action) {
        List<MenuItem> items = menu.getItems().get(0).getSubMenu().getItems();
        int index = -1;
        for (int i = 0; i < items.size(); i++) {
            if (action.equals(items.get(i).getText())) {
                index = i;
            }
        }
        assertThat(index).as("menu item '%s' is present", action).isNotNegative();
        // Index rather than caption path: the top-level trigger is icon-only, so
        // clickItem(String...) has no root caption to match against.
        // The tester, not use(menu): 25.3's MenuBarLocator re-finds the menu from
        // the UI, and a Grid cell component is not in that tree (F-082).
        new MenuBarTester<>(menu).clickItem(0, index);
    }
}
