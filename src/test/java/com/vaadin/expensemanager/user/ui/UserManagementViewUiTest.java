package com.vaadin.expensemanager.user.ui;

import com.vaadin.expensemanager.base.ui.AbstractUiTest;
import java.util.List;
import java.util.Set;

import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.base.ui.NavGroup;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.expensemanager.user.Role;
import com.vaadin.expensemanager.user.User;
import com.vaadin.expensemanager.user.UserRepository;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedCheckbox;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedRadioButtonGroup;
import com.vaadin.testing.unified.component.UnifiedTextField;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0012) for
 * {@link UserManagementView} — the ADMIN-only Users list (issue #64, ADR-0008).
 *
 * <p>Mirrors {@code ExpenseTypeViewUiTest}: an admin reaches the view with the
 * grid rendered and a menu entry; a plain USER is denied the route and does not
 * see the menu item; and the search box plus role/status filters narrow the rows
 * (and combine). Tests commit what they write; {@link #restoreSeededUsers()}
 * puts the two seeded accounts back and removes the revoked admin one test adds,
 * so the class runs on every Testing Tool (a browser's request threads never see
 * a test transaction).
 */
class UserManagementViewUiTest extends AbstractUiTest {

    private static final String ADMIN_EMAIL = "admin@vaadin.com";
    private static final String REVOKED_EMAIL = "revoked.admin@vaadin.com";

    /** Resolves the signed-in user for {@code navEntryLabels()}. */
    @Autowired
    protected AuthenticationContext authenticationContext;

    @Autowired
    private UserRepository userRepository;

    /** Undoes what the tests commit: the seeded revoked admin, a promotion, a revoke. */
    @AfterEach
    void restoreSeededUsers() {
        userRepository.findByEmail(REVOKED_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(LocalUserSeeder.PLAIN_USER_EMAIL).ifPresent(user -> {
            user.setRoles(Set.of(Role.USER));
            user.setEnabled(true);
            userRepository.save(user);
        });
    }

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void adminReachesViewWithSeededUsersRendered(UnifiedPage page) {
        page.navigate(UserManagementView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        // Both seeded accounts appear with their effective role and text status.
        assertThat(userRow(grid, ADMIN_EMAIL)).hasCellText("Role", "ADMIN");
        assertThat(userRow(grid, ADMIN_EMAIL)).hasCellText("Status", "Enabled");
        assertThat(userRow(grid, LocalUserSeeder.PLAIN_USER_EMAIL)).hasCellText("Role", "USER");
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
        page.navigate(UserManagementView.class);

        // Access control answers a denied route with the not-found page, never the grid.
        assertThat(page.findComponent(H2.class).withText("Page not found")).exists();
        assertThat(page.find(UnifiedGrid.class)).doesNotExist();
    }

    // ------------------------------------------------------- search & filter

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void searchNarrowsByNameOrEmail(UnifiedPage page) {
        page.navigate(UserManagementView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);
        UnifiedTextField search = page.find(UnifiedTextField.class).withLabel("Search");

        // The plain user's email is user@vaadin.com; searching it excludes admin.
        search.setValue("user@");
        assertThat(grid).hasRowCount(1);
        assertThat(userRow(grid, LocalUserSeeder.PLAIN_USER_EMAIL)).exists();

        // Clearing restores the full list.
        search.setValue("");
        assertThat(userRow(grid, ADMIN_EMAIL)).exists();
        assertThat(userRow(grid, LocalUserSeeder.PLAIN_USER_EMAIL)).exists();
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void roleFilterNarrowsRows(UnifiedPage page) {
        page.navigate(UserManagementView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);
        UnifiedComboBox role = page.find(UnifiedComboBox.class).withLabel("Role");

        role.selectItem("ADMIN");
        assertThat(grid.row().withCellText("Role", "ADMIN")).exists();
        assertThat(grid.row().withCellText("Role", "USER")).doesNotExist();

        role.selectItem("USER");
        assertThat(grid.row().withCellText("Role", "USER")).exists();
        assertThat(grid.row().withCellText("Role", "ADMIN")).doesNotExist();
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void statusFilterNarrowsRowsAndCombinesWithRole(UnifiedPage page) {
        // Seed a revoked admin so the revoked filter has something to show.
        var revoked = new User(REVOKED_EMAIL, "Revoked Admin", Set.of(Role.ADMIN));
        revoked.setEnabled(false);
        userRepository.saveAndFlush(revoked);

        page.navigate(UserManagementView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        page.find(UnifiedComboBox.class).withLabel("Status").selectItem("Revoked");
        assertThat(grid).hasRowCount(1);
        assertThat(userRow(grid, REVOKED_EMAIL)).hasCellText("Status", "Revoked");

        // Combine with a role filter that excludes the (ADMIN) revoked row.
        page.find(UnifiedComboBox.class).withLabel("Role").selectItem("USER");
        assertThat(grid).isEmpty();
    }

    // ------------------------------------------------------- editor write path

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void adminPromotesUserToAdminAndGridReflectsIt(UnifiedPage page) {
        page.navigate(UserManagementView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        UnifiedDialog editor = openEditorFor(page, grid, LocalUserSeeder.PLAIN_USER_EMAIL);
        editor.find(UnifiedRadioButtonGroup.class).withLabel("Role").selectItem("ADMIN");
        editor.find(UnifiedButton.class).withText("Save").click();

        assertThat(userRow(grid, LocalUserSeeder.PLAIN_USER_EMAIL)).hasCellText("Role", "ADMIN");
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void adminRevokesUserAndGridReflectsIt(UnifiedPage page) {
        page.navigate(UserManagementView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        UnifiedDialog editor = openEditorFor(page, grid, LocalUserSeeder.PLAIN_USER_EMAIL);
        // The seeded user starts enabled; clearing the checkbox revokes access.
        editor.find(UnifiedCheckbox.class).withLabel("Account enabled").uncheck();
        editor.find(UnifiedButton.class).withText("Save").click();

        assertThat(userRow(grid, LocalUserSeeder.PLAIN_USER_EMAIL)).hasCellText("Status", "Revoked");
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void guardViolationShowsErrorSummaryAndLeavesStateUnchanged(UnifiedPage page) {
        page.navigate(UserManagementView.class);
        UnifiedGrid grid = page.find(UnifiedGrid.class);

        // The bootstrap admin is the only admin, so disabling itself is rejected.
        UnifiedDialog editor = openEditorFor(page, grid, ADMIN_EMAIL);
        editor.find(UnifiedCheckbox.class).withLabel("Account enabled").uncheck();
        editor.find(UnifiedButton.class).withText("Save").click();

        // The error summary (in the dialog) carries the guard message.
        assertThat(editor.findComponent(ListItem.class).withTextContaining("last administrator"))
                .exists();
        // Dialog stays open and the grid is unchanged.
        assertThat(editor).isOpen();
        assertThat(userRow(grid, ADMIN_EMAIL)).hasCellText("Status", "Enabled");
    }

    // --------------------------------------------------------------- helpers

    /** Opens a user's editor from its row's pencil button; returns the dialog. */
    private static UnifiedDialog openEditorFor(UnifiedPage page, UnifiedGrid grid, String email) {
        userRow(grid, email).cell("").find(UnifiedButton.class)
                .withAccessibleName("Edit user " + email).click();
        return page.find(UnifiedDialog.class);
    }

    private static UnifiedGrid.UnifiedGridRow userRow(UnifiedGrid grid, String email) {
        return grid.row().withCellText("Email", email);
    }

    /**
     * The navigation entries the signed-in user can reach, by label (#146).
     *
     * <p>Was the {@code @Menu}-generated side-nav paths. The nav is now
     * hand-authored in {@code NavGroup} and two of its three groups render as
     * menus, whose items the browserless tester cannot see (F-071) — so this
     * asks the model the shell renders from, and the rendered menu is left to
     * visual verification.
     */
    private List<String> navEntryLabels() {
        return NavGroup.allVisibleTo(authenticationContext).stream()
                .map(NavGroup.NavItem::label)
                .toList();
    }
}
