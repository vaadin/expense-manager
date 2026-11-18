package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.html.H2;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedMenuBar;
import com.vaadin.testing.unified.component.UnifiedParagraph;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Server-side view test (pyramid layer 3, ADR-0012) for the role-aware dashboard
 * and shell header (UC-007, ADR-0017), on every Testing Tool.
 *
 * <p>Verifies the acceptance criteria: the dashboard greets the user by name and
 * renders role-appropriate content that differs for ADMIN vs USER, and
 * {@link MainLayout}'s header shows the current-user identity plus a working
 * logout action. Authenticates as the seeded admin and plain user via
 * {@link WithUserDetails} — the same records the form-stub logs in.
 */
class DashboardViewUiTest extends AbstractUiTest {

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesAdminGreetingAndContent(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(page.findComponent(H2.class).withTextContaining("Welcome"))
                .hasText("Welcome, Expense Admin");
        assertThat(roleNote(page, "administrator")).exists();
        assertThat(roleNote(page, "signed in as a user")).doesNotExist();
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userSeesUserGreetingAndContent(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(page.findComponent(H2.class).withTextContaining("Welcome"))
                .hasText("Welcome, Demo User");
        assertThat(roleNote(page, "signed in as a user")).exists();
        assertThat(roleNote(page, "administrator")).doesNotExist();
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

        // The avatar trigger has no text; its aria-label is its name.
        String trigger = "Account menu for Demo User";
        UnifiedMenuBar account = page.find(UnifiedMenuBar.class).withItem(trigger);

        assertThat(account.getItems(trigger)).contains("Demo User", "Sign out");
        assertThat(account.isItemEnabled(trigger, "Sign out")).isTrue();
    }

    /** The dashboard's role-specific paragraph, by a phrase only that role's note has. */
    private static UnifiedParagraph roleNote(UnifiedPage page, String phrase) {
        return page.find(UnifiedParagraph.class).withTextContaining(phrase);
    }
}
