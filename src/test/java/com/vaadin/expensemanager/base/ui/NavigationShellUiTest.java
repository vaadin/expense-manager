package com.vaadin.expensemanager.base.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.expensemanager.report.ui.MyReportsView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import com.vaadin.flow.component.sidenav.SideNav;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI unit test for the redesigned app shell (issue #146, ADR-0017), driven with
 * Vaadin's browserless tester — no browser, full Spring context.
 *
 * <p>Verifies the shell's contract from the outside for an authenticated user:
 * the landing dashboard renders inside {@link MainLayout}, the top bar carries
 * the three hand-authored {@link NavGroup} links filtered by the user's access,
 * the current group follows the <em>group</em> rather than the route, the view
 * chooses its own header state, and everything the drawer used to hold is
 * reachable from the avatar menu.
 *
 * <p>What it deliberately cannot check is appearance — the coral, the pill, the
 * 900px column and the 360px behaviour are all visual, and are verified in the
 * browser instead (see {@code docs/manual-verification.md}).
 *
 * <p>Boots on the same singleton {@link PostgreSQLContainer} pattern as
 * {@code AbstractIntegrationTest}; this class can't extend that base because
 * {@link SpringBrowserlessTest} already occupies the single inheritance slot.
 */
@UnifiedSpringBootTest
@ActiveProfiles("test")
class NavigationShellUiTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void landingViewRendersInsideMainLayout(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // TODO unified: Browserless Escape Hatch: navigate(DashboardView.class)
        var dashboard = browserless.navigate(DashboardView.class);

        assertThat(dashboard).isNotNull();
        // TODO unified: Browserless Escape Hatch: getCurrentView()
        assertThat(browserless.getCurrentView()).isInstanceOf(DashboardView.class);
        assertThat(dashboard.findAncestor(MainLayout.class)).isNotNull();
    }

    /**
     * The structural half of the redesign: the drawer shell is gone, not merely
     * restyled. Nothing in the app may still be rendering one.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void theDrawerShellIsGone(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        page.navigate(DashboardView.class);

        // TODO unified: Browserless Escape Hatch: $(AppLayout.class).all(), no Wrapper for AppLayout: write one (ADR 0003)
        assertThat(browserless.$(AppLayout.class).all()).isEmpty();
        // TODO unified: Browserless Escape Hatch: $(SideNav.class).all(), no Wrapper for SideNav: write one (ADR 0003)
        assertThat(browserless.$(SideNav.class).all()).isEmpty();
        // TODO unified: Browserless Escape Hatch: $(AppHeader.class).all(), no Wrapper for AppHeader: write one (ADR 0003)
        assertThat(browserless.$(AppHeader.class).all()).hasSize(1);
    }

    /** Eight views, three links — and the groups are in the design's order. */
    @Test
    @WithUserDetails("admin@vaadin.com")
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void adminSeesTheThreeDesignedGroups(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(navLabels(page))
                .containsExactly("My Expenses", "Admin Tasks", "Reference Tables");
    }

    /**
     * The Reference Tables pill is a plain link since #169, not a menu button:
     * its three routes are reached through {@code ReferenceTabs} inside the
     * content column, so the group has one destination and the shell already
     * rendered a one-entry group as a link. Admin Tasks still opens a menu, which
     * is why this asserts the pill's own type rather than counting ContextMenus.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void theReferenceTablesPillIsAPlainLinkWithNoMenuBehindIt(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        page.navigate(DashboardView.class);

        var pill = navPills(page).stream()
                .filter(p -> "Reference Tables"
                        .equals(p.getElement().getTextRecursively()))
                .findFirst()
                .orElseThrow();

        assertThat(pill).isInstanceOf(RouterLink.class);
        assertThat(((RouterLink) pill).getHref()).isEqualTo("vat-rates");
        // No ContextMenu is attached to it — the one left in the bar is Admin
        // Tasks', and a menu on this pill is exactly what #169 removed.
        // TODO unified: Browserless Escape Hatch: $(ContextMenu.class).all(), no Wrapper for ContextMenu: write one (ADR 0003)
        assertThat(browserless.$(ContextMenu.class).all())
                .allSatisfy(menu -> assertThat(menu.getTarget()).isNotSameAs(pill));
    }

    /**
     * A group whose every view is off-limits renders nothing at all — the plain
     * user is left with the one group they can use.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void plainUserSeesOnlyTheGroupTheyCanUse(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(navLabels(page)).containsExactly("My Expenses");
    }

    /**
     * The acceptance criterion the old per-route nav could not express: sitting
     * on a report's detail page keeps <em>My Expenses</em> current, even though
     * that route is not an entry of its own.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void currentGroupFollowsTheGroupNotTheRoute(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        page.navigate(MyReportsView.class);
        assertThat(currentNavLabel(page)).isEqualTo("My Expenses");

        // TODO unified: Browserless Escape Hatch:navigate("report", com.vaadin.expensemanager.report.ui.ReportDetailView.class)
        browserless.navigate("report", com.vaadin.expensemanager.report.ui.ReportDetailView.class);
        assertThat(currentNavLabel(page)).isEqualTo("My Expenses");
    }

    /** The dashboard has no nav item in the design, so no group is current. */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void theDashboardLeavesEveryGroupIdle(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(currentNavLabel(page)).isNull();
    }

    /**
     * Which header a view gets is the view's choice: the report list asks for
     * the tall greeting variant, everything else takes the plain bar.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void theViewChoosesItsHeaderVariant(UnifiedPage page) {
        page.navigate(MyReportsView.class);
        assertThat(headerClasses(page)).contains(HeaderState.HOME.className());

        page.navigate(DashboardView.class);
        assertThat(headerClasses(page))
                .contains(HeaderState.DEFAULT.className())
                .doesNotContain(HeaderState.HOME.className());
    }

    /**
     * Everything the drawer used to hold — the name, the colour-scheme switcher
     * and sign-out — is still reachable, now from behind the avatar (#145
     * decision 1). Dark mode is a requirement, so the switcher could not simply
     * be dropped with the navbar that held it.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void theAvatarMenuHoldsIdentityThemeAndSignOut(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(accountMenuLabels(page))
                .contains("Demo User", ThemeSwitcher.LABEL, "Sign out");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void unknownRouteResolvesToCustomNotFoundView(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // TODO unified: Browserless Escape Hatch: navigate("no-such-route-exists", NotFoundView.class)
        browserless.navigate("no-such-route-exists", NotFoundView.class);

        // TODO unified: Browserless Escape Hatch: getCurrentView()
        assertThat(browserless.getCurrentView()).isInstanceOf(NotFoundView.class);
    }

    // ---- helpers -----------------------------------------------------------

    /** The nav pills, in bar order, whatever element type each renders as. */
    private List<Component> navPills(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // TODO unified: Browserless Escape Hatch: $(AppHeader.class).first().getChildren().flatMap(Component::getChildren).filter(child -> child.getElement().getClassList().contains("app-nav")).flatMap(Component::getChildren).toList(), no Wrapper for AppHeader: write one (ADR 0003)
        return browserless.$(AppHeader.class).first().getChildren()
                .flatMap(Component::getChildren)
                .filter(child -> child.getElement().getClassList().contains("app-nav"))
                .flatMap(Component::getChildren)
                .toList();
    }

    private List<String> navLabels(UnifiedPage page) {
        return navPills(page).stream().map(pill -> pill.getElement().getTextRecursively()).toList();
    }

    /** The label of the group marked current, or {@code null} if none is. */
    private String currentNavLabel(UnifiedPage page) {
        return navPills(page).stream()
                .filter(pill -> "page"
                        .equals(pill.getElement().getAttribute("aria-current")))
                .map(pill -> pill.getElement().getTextRecursively())
                .findFirst()
                .orElse(null);
    }

    private List<String> headerClasses(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // TODO unified: Browserless Escape Hatch: $(AppHeader.class).first().getElement().getClassList(), no Wrapper for AppHeader: write one (ADR 0003)
        return List.copyOf(browserless.$(AppHeader.class).first().getElement().getClassList());
    }

    private List<String> accountMenuLabels(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // TODO unified: Browserless Escape Hatch: $(com.vaadin.flow.component.menubar.MenuBar.class).all().stream().flatMap(bar -> bar.getItems().stream()).flatMap(item -> item.getSubMenu().getItems().stream()).map(item -> item.getElement().getTextRecursively()).toList(), no Wrapper for com.vaadin.flow.component.menubar.MenuBar: write one (ADR 0003)
        return browserless.$(com.vaadin.flow.component.menubar.MenuBar.class).all().stream()
                .flatMap(bar -> bar.getItems().stream())
                .flatMap(item -> item.getSubMenu().getItems().stream())
                .map(item -> item.getElement().getTextRecursively())
                .toList();
    }
}
