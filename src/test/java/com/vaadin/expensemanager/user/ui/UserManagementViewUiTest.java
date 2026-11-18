package com.vaadin.expensemanager.user.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.vaadin.browserless.locator.Locators;
import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.expensemanager.user.Role;
import com.vaadin.expensemanager.user.User;
import com.vaadin.expensemanager.user.UserRepository;
import com.vaadin.expensemanager.user.UserSummaryDto;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.GridLocator;
import com.vaadin.expensemanager.base.ui.NavGroup;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for
 * {@link UserManagementView} — the ADMIN-only Users list (issue #64, ADR-0008).
 *
 * <p>Mirrors {@code ExpenseTypeViewUiTest}: an admin reaches the view with the
 * grid rendered and a menu entry; a plain USER is denied the route and does not
 * see the menu item; and the search box plus role/status filters narrow the rows
 * (and combine). {@code @Transactional} rolls back the extra revoked user seeded
 * for the status-filter case.
 */
// TODO unified: Browserless-only pattern: @Transactional rolls back what the views write only on Browserless; commit test data and clean up to run everywhere (spec 02)
@UnifiedSpringBootTest
@ActiveProfiles("test")
@Transactional
@RequiresTestingTool(TestingTool.BROWSERLESS)
class UserManagementViewUiTest implements Locators {

    private static final int EMAIL_COL = 0;
    private static final int NAME_COL = 1;
    private static final int ROLE_COL = 2;
    private static final int STATUS_COL = 3;
    private static final int ACTION_COL = 4;

    private static final String ADMIN_EMAIL = "admin@vaadin.com";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    /** Resolves the signed-in user for {@code navEntryLabels()}. */
    @Autowired
    protected AuthenticationContext authenticationContext;

    @Autowired
    private UserRepository userRepository;

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void adminReachesViewWithSeededUsersRendered(UnifiedPage page) {
        page.navigate(UserManagementView.class);
        var grid = findGrid(UserSummaryDto.class);

        // Both seeded accounts appear with their effective role and text status.
        assertThat(columnText(grid, EMAIL_COL))
                .contains(ADMIN_EMAIL, LocalUserSeeder.PLAIN_USER_EMAIL);
        assertThat(cellFor(grid, ADMIN_EMAIL, ROLE_COL)).isEqualTo("ADMIN");
        assertThat(cellFor(grid, ADMIN_EMAIL, STATUS_COL)).isEqualTo("Enabled");
        assertThat(cellFor(grid, LocalUserSeeder.PLAIN_USER_EMAIL, ROLE_COL))
                .isEqualTo("USER");
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
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void userCannotReachRouteByUrl(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // TODO unified: Browserless Escape Hatch: navigate(UserManagementView.class)
        assertThatThrownBy(() -> browserless.navigate(UserManagementView.class))
                .isInstanceOf(Exception.class);
    }

    // ------------------------------------------------------- search & filter

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void searchNarrowsByNameOrEmail(UnifiedPage page) {
        page.navigate(UserManagementView.class);

        // The plain user's email is user@vaadin.com; searching it excludes admin.
        findTextField().withLabel("Search").setValue("user@");
        var emails = columnText(findGrid(UserSummaryDto.class), EMAIL_COL);
        assertThat(emails).containsExactly(LocalUserSeeder.PLAIN_USER_EMAIL);

        // Clearing restores the full list.
        findTextField().withLabel("Search").setValue("");
        assertThat(columnText(findGrid(UserSummaryDto.class), EMAIL_COL))
                .contains(ADMIN_EMAIL, LocalUserSeeder.PLAIN_USER_EMAIL);
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    void roleFilterNarrowsRows(UnifiedPage page) {
        page.navigate(UserManagementView.class);

        findComboBox(String.class).withLabel("Role").selectItem("ADMIN");
        var roles = columnText(findGrid(UserSummaryDto.class), ROLE_COL);
        assertThat(roles).isNotEmpty().containsOnly("ADMIN");

        findComboBox(String.class).withLabel("Role").selectItem("USER");
        assertThat(columnText(findGrid(UserSummaryDto.class), ROLE_COL))
                .isNotEmpty().containsOnly("USER");
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

        findComboBox(String.class).withLabel("Status").selectItem("Revoked");
        var grid = findGrid(UserSummaryDto.class);
        assertThat(columnText(grid, EMAIL_COL)).containsExactly("revoked.admin@vaadin.com");
        assertThat(cellFor(grid, "revoked.admin@vaadin.com", STATUS_COL))
                .isEqualTo("Revoked");

        // Combine with a role filter that excludes the (ADMIN) revoked row.
        findComboBox(String.class).withLabel("Role").selectItem("USER");
        assertThat(findGrid(UserSummaryDto.class).size()).isZero();
    }

    // ------------------------------------------------------- editor write path

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void adminPromotesUserToAdminAndGridReflectsIt(UnifiedPage page) {
        page.navigate(UserManagementView.class);
        var grid = findGrid(UserSummaryDto.class);

        openEditorFor(page, grid, LocalUserSeeder.PLAIN_USER_EMAIL);
        findRadioButtonGroup(Role.class).withLabel("Role").selectItem("ADMIN");
        findButton().withText("Save").click();

        assertThat(cellFor(findGrid(UserSummaryDto.class),
                LocalUserSeeder.PLAIN_USER_EMAIL, ROLE_COL)).isEqualTo("ADMIN");
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void adminRevokesUserAndGridReflectsIt(UnifiedPage page) {
        page.navigate(UserManagementView.class);
        var grid = findGrid(UserSummaryDto.class);

        openEditorFor(page, grid, LocalUserSeeder.PLAIN_USER_EMAIL);
        // The seeded user starts enabled; clearing the checkbox revokes access.
        findCheckbox().withLabel("Account enabled").click();
        findButton().withText("Save").click();

        assertThat(cellFor(findGrid(UserSummaryDto.class),
                LocalUserSeeder.PLAIN_USER_EMAIL, STATUS_COL)).isEqualTo("Revoked");
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void guardViolationShowsErrorSummaryAndLeavesStateUnchanged(UnifiedPage page) {
        page.navigate(UserManagementView.class);
        var grid = findGrid(UserSummaryDto.class);

        // The bootstrap admin is the only admin, so disabling itself is rejected.
        openEditorFor(page, grid, ADMIN_EMAIL);
        findCheckbox().withLabel("Account enabled").click();
        findButton().withText("Save").click();

        // The error summary (in the dialog overlay) carries the guard message.
        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("last administrator");
        // Dialog stays open (Save still present) and the grid is unchanged.
        assertThat(findButton().withText("Save").exists()).isTrue();
        assertThat(cellFor(findGrid(UserSummaryDto.class), ADMIN_EMAIL, STATUS_COL))
                .isEqualTo("Enabled");
    }

    // --------------------------------------------------------------- helpers

    private void openEditorFor(UnifiedPage page, GridLocator<?> grid, String email) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        var cell = grid.getCellComponent(rowIndexFor(grid, email), ACTION_COL);
        // TODO unified: Component Reference: test(find(Button.class, cell).withAriaLabel("Edit user " + email).single()).click(): replace it with a locator that finds the same component
        browserless.test(browserless.find(Button.class, cell).withAriaLabel("Edit user " + email).single())
                .click();
    }

    private static int rowIndexFor(GridLocator<?> grid, String email) {
        for (int row = 0; row < grid.size(); row++) {
            if (grid.getCellText(row, EMAIL_COL).equals(email)) {
                return row;
            }
        }
        throw new AssertionError("No row for " + email);
    }

    private static List<String> columnText(GridLocator<?> grid, int column) {
        var values = new ArrayList<String>();
        for (int row = 0; row < grid.size(); row++) {
            values.add(grid.getCellText(row, column));
        }
        return values;
    }

    /** The cell text in {@code column} of the row whose EMAIL cell equals {@code email}. */
    private static String cellFor(GridLocator<?> grid, String email, int column) {
        for (int row = 0; row < grid.size(); row++) {
            if (grid.getCellText(row, EMAIL_COL).equals(email)) {
                return grid.getCellText(row, column);
            }
        }
        throw new AssertionError("No row for " + email);
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
