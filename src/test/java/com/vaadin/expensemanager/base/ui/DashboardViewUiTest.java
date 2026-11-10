package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.base.AbstractUiTest;
import com.vaadin.expensemanager.base.ui.unified.UnifiedMenuBar;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.html.Main;
import com.vaadin.testing.unified.UnifiedPage;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0027 proposed) for the role-aware dashboard
 * and shell header (UC-007, ADR-0017). Runs unchanged on Browserless and Playwright.
 *
 * <p>Verifies the acceptance criteria: the dashboard greets the user by name and
 * renders role-appropriate content that differs for ADMIN vs USER, and
 * {@link MainLayout}'s header shows the current-user identity plus a working
 * logout action. Authenticates as the seeded admin and plain user via
 * {@link WithUserDetails} — the same records the form-stub logs in.
 *
 * <p>The dashboard's text is read from the shell's {@code <main>} content column,
 * which holds nothing but the routed view: {@code DashboardView} is a bare
 * {@code VerticalLayout}, a tag every block on a page shares (F-088). The avatar
 * menu is read through {@link UnifiedMenuBar}, which opens it in a browser — its
 * items exist only while open — and reads the server model on Browserless.
 */
class DashboardViewUiTest extends AbstractUiTest {

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesAdminGreetingAndContent(UnifiedPage page) {
        page.navigate(DashboardView.class);
        assertThat(page).isAt(DashboardView.class);
        screenshot(page, "admin-dashboard");

        var text = dashboardText(page);
        assertThat(text).contains("Welcome, Expense Admin");
        assertThat(text).contains("administrator");
        assertThat(text).doesNotContain("signed in as a user");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userSeesUserGreetingAndContent(UnifiedPage page) {
        page.navigate(DashboardView.class);
        assertThat(page).isAt(DashboardView.class);
        screenshot(page, "user-dashboard");

        var text = dashboardText(page);
        assertThat(text).contains("Welcome, Demo User");
        assertThat(text).contains("signed in as a user");
        assertThat(text).doesNotContain("administrator");
    }

    /**
     * Identity and logout survived the shell redesign (#146) — both moved from
     * the {@code AppLayout} navbar to behind the avatar, because the designed
     * bar has no room beside the three nav links (#145 decision 1).
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void headerShowsIdentityAndLogout(UnifiedPage page) {
        page.navigate(DashboardView.class);

        var accountMenu = page.find(UnifiedMenuBar.class).single().openSubMenu(0);
        screenshot(page, "account-menu-open");

        assertThat(accountMenu).extracting(UnifiedMenuBar.Item::text)
                .contains("Demo User", "Sign out");

        var signOut = accountMenu.stream()
                .filter(item -> "Sign out".equals(item.text()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No 'Sign out' item in the shell"));
        assertThat(signOut.enabled()).isTrue();
    }

    private static String dashboardText(UnifiedPage page) {
        return page.findComponent(Main.class).single().element().getText();
    }
}
