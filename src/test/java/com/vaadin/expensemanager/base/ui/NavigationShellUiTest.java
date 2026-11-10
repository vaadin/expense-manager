package com.vaadin.expensemanager.base.ui;

import java.util.List;

import com.vaadin.expensemanager.base.AbstractUiTest;
import com.vaadin.expensemanager.base.ui.unified.UnifiedDiv;
import com.vaadin.expensemanager.base.ui.unified.UnifiedMenuBar;
import com.vaadin.expensemanager.base.ui.unified.UnifiedNav;
import com.vaadin.expensemanager.base.ui.unified.UnifiedVerticalLayout;
import com.vaadin.expensemanager.reference.ui.VatRateView;
import com.vaadin.expensemanager.report.ui.MyReportsView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0027 proposed) for the redesigned app shell
 * (issue #146, ADR-0017). Runs unchanged on Browserless and Playwright.
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
 * <p>Everything is read from what renders: the shell by its role class names, the
 * pills inside the {@code <nav aria-label="Main">} landmark, the current group by
 * {@code aria-current}, the header state by its class. Two things the browserless
 * version read from the server-side component tree are now read the way a user
 * meets them — see {@link #theReferenceTablesPillIsAPlainLinkWithNoMenuBehindIt} and
 * {@link #unknownRouteResolvesToCustomNotFoundView}.
 */
class NavigationShellUiTest extends AbstractUiTest {

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void landingViewRendersInsideMainLayout(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(page).isAt(DashboardView.class);
        // The dashboard's greeting is found inside the shell, not merely on the page.
        var shell = page.find(UnifiedDiv.class).withClassName("app-shell").single();
        assertThat(shell.findComponent(H2.class).withText("Welcome, Demo User").exists())
                .isTrue();
        screenshot(page, "dashboard-in-shell");
    }

    /**
     * The structural half of the redesign: the drawer shell is gone, not merely
     * restyled. Nothing in the app may still be rendering one.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theDrawerShellIsGone(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(page.findComponent(AppLayout.class).exists()).isFalse();
        assertThat(page.findComponent(SideNav.class).exists()).isFalse();
        assertThat(appHeaders(page).all()).hasSize(1);
    }

    /** Eight views, three links — and the groups are in the design's order. */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesTheThreeDesignedGroups(UnifiedPage page) {
        page.navigate(DashboardView.class);
        screenshot(page, "admin-nav");

        assertThat(navLabels(page))
                .containsExactly("My Expenses", "Admin Tasks", "Reference Tables");
    }

    /**
     * The Reference Tables pill is a plain link since #169, not a menu button:
     * its three routes are reached through {@code ReferenceTabs} inside the
     * content column, so the group has one destination and the shell already
     * rendered a one-entry group as a link. Admin Tasks still opens a menu.
     *
     * <p>The browserless version checked that no server-side {@code ContextMenu}
     * targets the pill. A browser has no such tree, so the same fact is asserted
     * the way a user meets it: the pill is an {@code <a>} to {@code vat-rates}, and
     * clicking it goes there with no menu opening on top. (On Browserless no menu
     * would open on click either way — a {@code ContextMenu}'s open-on-click is
     * client-side — so there the tag and the {@code href} carry the assertion.)
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theReferenceTablesPillIsAPlainLinkWithNoMenuBehindIt(UnifiedPage page) {
        page.navigate(DashboardView.class);

        var pill = navPills(page).stream()
                .filter(p -> "Reference Tables".equals(p.element().getText()))
                .findFirst()
                .orElseThrow();

        assertThat(pill.element().getTag()).isEqualTo("a");
        assertThat(pill.element().getAttribute("href")).isEqualTo("vat-rates");

        pill.element().click();
        // Wait on the destination's content before the URL: a router link
        // navigates client-side after the click's Auto-Wait has returned, and
        // on Playwright isAt's polling alone never sees the URL change (it reads
        // a cached page.url() that only a browser call refreshes).
        page.waitUntil(() -> page.find(UnifiedVerticalLayout.class)
                .withClassName("page-header").exists());

        assertThat(page).isAt(VatRateView.class);
        // The only ContextMenu left in the bar is Admin Tasks', and it stays shut.
        assertThat(page.findComponent(ContextMenu.class)
                .where(menu -> Boolean.TRUE.equals(menu.getPropertyBoolean("opened")))
                .exists()).isFalse();
        screenshot(page, "after-reference-tables-click");
    }

    /**
     * A group whose every view is off-limits renders nothing at all — the plain
     * user is left with the one group they can use.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
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
    void currentGroupFollowsTheGroupNotTheRoute(UnifiedPage page) {
        page.navigate(MyReportsView.class);
        assertThat(currentNavLabel(page)).isEqualTo("My Expenses");

        page.navigate("report");
        assertThat(page).isAt(com.vaadin.expensemanager.report.ui.ReportDetailView.class);
        screenshot(page, "report-detail-nav");
        assertThat(currentNavLabel(page)).isEqualTo("My Expenses");
    }

    /** The dashboard has no nav item in the design, so no group is current. */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
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
    void theViewChoosesItsHeaderVariant(UnifiedPage page) {
        page.navigate(MyReportsView.class);
        screenshot(page, "home-header");
        assertThat(headerClasses(page)).contains(HeaderState.HOME.className());

        page.navigate(DashboardView.class);
        screenshot(page, "default-header");
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
    void theAvatarMenuHoldsIdentityThemeAndSignOut(UnifiedPage page) {
        page.navigate(DashboardView.class);

        var labels = page.find(UnifiedMenuBar.class).single().openSubMenu(0).stream()
                .map(UnifiedMenuBar.Item::text).toList();
        screenshot(page, "avatar-menu-open");

        assertThat(labels).contains("Demo User", ThemeSwitcher.LABEL, "Sign out");
    }

    /**
     * {@code NotFoundView} has no route of its own to compare a browser's URL with,
     * so "the router resolved to it" is asserted by what only it renders — its
     * empty state, inside the shell — at the URL the user typed.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void unknownRouteResolvesToCustomNotFoundView(UnifiedPage page) {
        page.navigate("no-such-route-exists");
        screenshot(page, "not-found");

        assertThat(page).hasPath("no-such-route-exists");
        var shell = page.find(UnifiedDiv.class).withClassName("app-shell").single();
        assertThat(shell.findComponent(H2.class).withText("Page not found").exists()).isTrue();
    }

    // ---- helpers -----------------------------------------------------------

    /** The nav pills, in bar order, whatever element type each renders as. */
    private static List<UnifiedContainer> navPills(UnifiedPage page) {
        return page.find(UnifiedNav.class).withAccessibleName("Main").single()
                .find(UnifiedContainer.class).withClassName("app-nav__item").all();
    }

    private static List<String> navLabels(UnifiedPage page) {
        return navPills(page).stream().map(pill -> pill.element().getText()).toList();
    }

    /** The label of the group marked current, or {@code null} if none is. */
    private static String currentNavLabel(UnifiedPage page) {
        return navPills(page).stream()
                .filter(pill -> "page".equals(pill.element().getAttribute("aria-current")))
                .map(pill -> pill.element().getText())
                .findFirst()
                .orElse(null);
    }

    /** The shell's header bar: a {@code <header>}, narrowed by its role class. */
    private static com.vaadin.testing.unified.query.ComponentQuery appHeaders(UnifiedPage page) {
        return page.findComponent(AppHeader.class).withClassName("app-header");
    }

    private static List<String> headerClasses(UnifiedPage page) {
        UnifiedGenericComponent header = appHeaders(page).single();
        return List.of(header.element().getAttribute("class").trim().split("\\s+"));
    }
}
