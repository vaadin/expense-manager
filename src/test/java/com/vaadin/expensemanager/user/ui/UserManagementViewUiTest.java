package com.vaadin.expensemanager.user.ui;

import java.util.List;
import java.util.Set;

import com.vaadin.expensemanager.base.AbstractUiTest;
import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.base.ui.NavGroup;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.expensemanager.user.Role;
import com.vaadin.expensemanager.user.User;
import com.vaadin.expensemanager.user.UserRepository;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedCheckbox;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedRadioButtonGroup;
import com.vaadin.testing.unified.component.UnifiedTextField;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0027 proposed) for
 * {@link UserManagementView} — the ADMIN-only Users list (issue #64, ADR-0008).
 * Runs unchanged on Browserless and Playwright.
 *
 * <p>An admin reaches the view with the grid rendered and a menu entry; a plain
 * USER is denied the route and does not see the menu item; the search box and the
 * role/status filters narrow the rows (and combine); the editor promotes, revokes
 * and refuses to revoke the last admin. The revoked user seeded for the
 * status-filter case is committed and removed again by the
 * {@code DatabaseSnapshotExtension}.
 */
class UserManagementViewUiTest extends AbstractUiTest {

    private static final String ADMIN_EMAIL = "admin@vaadin.com";

    /** Resolves the signed-in user for {@code navEntryLabels()}. */
    @Autowired
    private AuthenticationContext authenticationContext;

    @Autowired
    private UserRepository userRepository;

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void adminReachesViewWithSeededUsersRendered(UnifiedPage page) {
        page.navigate(UserManagementView.class);
        var grid = grid(page);

        // Both seeded accounts appear with their effective role and text status.
        assertThat(grid.column("Email").getCellTexts())
                .contains(ADMIN_EMAIL, LocalUserSeeder.PLAIN_USER_EMAIL);
        assertThat(row(grid, ADMIN_EMAIL))
                .hasCellText("Role", "ADMIN")
                .hasCellText("Status", "Enabled");
        assertThat(row(grid, LocalUserSeeder.PLAIN_USER_EMAIL)).hasCellText("Role", "USER");
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void adminSeesMenuEntry(UnifiedPage page) {
        page.navigate(DashboardView.class);
        assertThat(navEntryLabels()).contains("Users");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userSeesNoMenuEntry(UnifiedPage page) {
        page.navigate(DashboardView.class);
        assertThat(navEntryLabels()).doesNotContain("Users");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachRouteByUrl(UnifiedPage page) {
        page.navigate(DashboardView.class);
        page.navigate("users");

        // Denied: the Users grid and its search box never render for a plain user.
        assertThat(page.find(UnifiedTextField.class).withLabel("Search").exists()).isFalse();
        assertThat(page.find(UnifiedGrid.class).exists()).isFalse();
    }

    // ------------------------------------------------------- search & filter

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void searchNarrowsByNameOrEmail(UnifiedPage page) {
        page.navigate(UserManagementView.class);

        // The plain user's email is user@vaadin.com; searching it excludes admin.
        search(page).setValue("user@");
        assertThat(grid(page).column("Email"))
                .hasCellTexts(LocalUserSeeder.PLAIN_USER_EMAIL);

        // Clearing restores the full list.
        search(page).clear();
        assertThat(grid(page)).hasRowCount(2);
        assertThat(grid(page).column("Email").getCellTexts())
                .contains(ADMIN_EMAIL, LocalUserSeeder.PLAIN_USER_EMAIL);
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void roleFilterNarrowsRows(UnifiedPage page) {
        page.navigate(UserManagementView.class);

        comboBox(page, "Role").selectItem("ADMIN");
        assertThat(grid(page).column("Role")).hasCellTexts("ADMIN");

        comboBox(page, "Role").selectItem("USER");
        assertThat(grid(page).column("Role")).hasCellTexts("USER");
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void statusFilterNarrowsRowsAndCombinesWithRole(UnifiedPage page) {
        // Seed a revoked admin so the revoked filter has something to show.
        var revoked = new User("revoked.admin@vaadin.com", "Revoked Admin",
                Set.of(Role.ADMIN));
        revoked.setEnabled(false);
        userRepository.saveAndFlush(revoked);

        page.navigate(UserManagementView.class);

        comboBox(page, "Status").selectItem("Revoked");
        assertThat(grid(page).column("Email")).hasCellTexts("revoked.admin@vaadin.com");
        assertThat(row(grid(page), "revoked.admin@vaadin.com"))
                .hasCellText("Status", "Revoked");

        // Combine with a role filter that excludes the (ADMIN) revoked row.
        comboBox(page, "Role").selectItem("USER");
        assertThat(grid(page)).isEmpty();
    }

    // ------------------------------------------------------- editor write path

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void adminPromotesUserToAdminAndGridReflectsIt(UnifiedPage page) {
        page.navigate(UserManagementView.class);

        openEditorFor(page, LocalUserSeeder.PLAIN_USER_EMAIL);
        page.find(UnifiedRadioButtonGroup.class).withLabel("Role").single()
                .selectItem("ADMIN");
        page.find(UnifiedButton.class).withText("Save").single().click();

        assertThat(row(grid(page), LocalUserSeeder.PLAIN_USER_EMAIL))
                .hasCellText("Role", "ADMIN");
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void adminRevokesUserAndGridReflectsIt(UnifiedPage page) {
        page.navigate(UserManagementView.class);

        openEditorFor(page, LocalUserSeeder.PLAIN_USER_EMAIL);
        // The seeded user starts enabled; clearing the checkbox revokes access.
        page.find(UnifiedCheckbox.class).withLabel("Account enabled").single().uncheck();
        page.find(UnifiedButton.class).withText("Save").single().click();

        assertThat(row(grid(page), LocalUserSeeder.PLAIN_USER_EMAIL))
                .hasCellText("Status", "Revoked");
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void guardViolationShowsErrorSummaryAndLeavesStateUnchanged(UnifiedPage page) {
        page.navigate(UserManagementView.class);

        // The bootstrap admin is the only admin, so disabling itself is rejected.
        openEditorFor(page, ADMIN_EMAIL);
        page.find(UnifiedCheckbox.class).withLabel("Account enabled").single().uncheck();
        page.find(UnifiedButton.class).withText("Save").single().click();

        // The error summary (in the dialog) carries the guard message.
        var summary = page.find(UnifiedContainer.class).withClassName("error-summary").single();
        assertThat(summary).isVisible();
        assertThat(summary.element().getText()).contains("last administrator");
        // Dialog stays open (Save still present) and the grid is unchanged.
        assertThat(page.find(UnifiedButton.class).withText("Save").single()).isVisible();
        assertThat(row(grid(page), ADMIN_EMAIL)).hasCellText("Status", "Enabled");
    }

    // --------------------------------------------------------------- helpers

    private static UnifiedGrid grid(UnifiedPage page) {
        return page.find(UnifiedGrid.class).single();
    }

    private static UnifiedGrid.UnifiedGridRow row(UnifiedGrid grid, String email) {
        return grid.rows().withCellText("Email", email).single();
    }

    private static UnifiedTextField search(UnifiedPage page) {
        return page.find(UnifiedTextField.class).withLabel("Search").single();
    }

    private static UnifiedComboBox comboBox(UnifiedPage page, String label) {
        return page.find(UnifiedComboBox.class).withLabel(label).single();
    }

    private static void openEditorFor(UnifiedPage page, String email) {
        // Scoped to the row's action cell (the column has an empty header).
        row(grid(page), email).cell("").find(UnifiedButton.class)
                .withAccessibleName("Edit user " + email).single().click();
    }

    /**
     * The navigation entries the signed-in user can reach, by label (#146).
     *
     * <p>Asks the model the shell renders from, as the browserless version did:
     * two of the three nav groups render as menus whose items exist only while
     * open (F-071). Portable as it stands — the server is in the test JVM on every
     * Testing Tool, and {@code @WithUserDetails} sets the test thread's user.
     */
    private List<String> navEntryLabels() {
        return NavGroup.allVisibleTo(authenticationContext).stream()
                .map(NavGroup.NavItem::label)
                .toList();
    }
}
