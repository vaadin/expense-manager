package com.vaadin.expensemanager.base.ui;

import java.util.List;

import com.vaadin.expensemanager.reference.ui.VatRateView;
import com.vaadin.expensemanager.report.ui.MyReportsView;
import com.vaadin.expensemanager.report.ui.ReportDetailView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedHeader;
import com.vaadin.testing.unified.component.UnifiedMenuBar;
import com.vaadin.testing.unified.component.UnifiedNav;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test for the redesigned app shell (issue #146, ADR-0017): full Spring
 * context, on every Testing Tool.
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
 * <p>Runs on the shared {@link AbstractUiTest} context. The shell is read through the Core Set
 * Wrappers of what {@code AppHeader} renders: a {@code <header>} with the
 * {@code app-header} class, its {@code <nav>}, and the account {@code MenuBar}.
 */
class NavigationShellUiTest extends AbstractUiTest {

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void landingViewRendersInsideMainLayout(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(page).isAt(DashboardView.class);
        // MainLayout is what draws the app header around the view.
        assertThat(header(page)).exists();
        assertThat(page.findComponent(H2.class).withTextContaining("Welcome")).exists();
    }

    /**
     * The structural half of the redesign: the drawer shell is gone, not merely
     * restyled. Nothing in the app may still be rendering one.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theDrawerShellIsGone(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(page.findComponent(AppLayout.class)).doesNotExist();
        assertThat(page.findComponent(SideNav.class)).doesNotExist();
        assertThat(header(page)).hasCount(1);
    }

    /** Eight views, three links — and the groups are in the design's order. */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesTheThreeDesignedGroups(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(navLabels(page))
                .containsExactly("My Expenses", "Admin Tasks", "Reference Tables");
    }

    /**
     * The Reference Tables pill is a plain link since #169, not a menu button:
     * its three routes are reached through {@code ReferenceTabs} inside the
     * content column, so the group has one destination and the shell already
     * rendered a one-entry group as a link. Admin Tasks still opens a menu.
     * Observed from outside: the pill is an {@code <a>} to {@code vat-rates}, and
     * activating it goes there rather than opening anything.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theReferenceTablesPillIsAPlainLinkWithNoMenuBehindIt(UnifiedPage page) {
        page.navigate(DashboardView.class);

        UnifiedElement pill = navPills(page).stream()
                .filter(p -> "Reference Tables".equals(p.getText().strip()))
                .findFirst()
                .orElseThrow();

        assertThat(pill.getTag()).isEqualTo("a");
        assertThat(pill.getAttribute("href")).isEqualTo("vat-rates");

        pill.click();
        assertThat(page).isAt(VatRateView.class);
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
        assertThat(page).isAt(ReportDetailView.class);
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
        assertThat(header(page).withClassName(HeaderState.HOME.className())).exists();

        page.navigate(DashboardView.class);
        assertThat(header(page).withClassName(HeaderState.DEFAULT.className())).exists();
        assertThat(header(page).withClassName(HeaderState.HOME.className())).doesNotExist();
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

        // The avatar trigger has no text; its aria-label is its name.
        String trigger = "Account menu for Demo User";
        assertThat(page.find(UnifiedMenuBar.class).withItem(trigger).getItems(trigger))
                .contains("Demo User", ThemeSwitcher.LABEL, "Sign out");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void unknownRouteResolvesToCustomNotFoundView(UnifiedPage page) {
        page.navigate("no-such-route-exists");

        // NotFoundView's empty state, inside the shell (it keeps the navigation).
        assertThat(page.findComponent(H2.class).withText("Page not found")).exists();
        assertThat(header(page)).exists();
    }

    // ---- helpers -----------------------------------------------------------

    private static UnifiedHeader header(UnifiedPage page) {
        return page.find(UnifiedHeader.class).withClassName("app-header");
    }

    /** The nav pills, in bar order, whatever element type each renders as. */
    private static List<UnifiedElement> navPills(UnifiedPage page) {
        return header(page).find(UnifiedNav.class).withClassName("app-nav").element().children();
    }

    private static List<String> navLabels(UnifiedPage page) {
        return navPills(page).stream().map(pill -> pill.getText().strip()).toList();
    }

    /** The label of the group marked current, or {@code null} if none is. */
    private static String currentNavLabel(UnifiedPage page) {
        return navPills(page).stream()
                .filter(pill -> "page".equals(pill.getAttribute("aria-current")))
                .map(pill -> pill.getText().strip())
                .findFirst()
                .orElse(null);
    }
}
