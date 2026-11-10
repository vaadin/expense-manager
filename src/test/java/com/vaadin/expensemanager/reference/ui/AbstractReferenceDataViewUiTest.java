package com.vaadin.expensemanager.reference.ui;

import java.util.List;

import com.vaadin.expensemanager.base.AbstractUiTest;
import com.vaadin.expensemanager.base.ui.NavGroup;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Shared base for the reference-data settings-screen Unified UI tests (pyramid
 * layer 3, ADR-0027 proposed) — {@link VatRateViewUiTest},
 * {@link ExpenseTypeViewUiTest} and {@link ReferenceConfigViewUiTest} (issue #22,
 * ADR-0008/0018). Runs unchanged on Browserless and Playwright.
 *
 * <p>Holds what the three share: the {@link ReferenceDataService} handle used to
 * assert the active-options query behind an admin's UI action, and the grid
 * helpers. The Postgres container, the app and data isolation come from
 * {@link AbstractUiTest}: every add/edit/reorder/deactivate commits, and the
 * {@code DatabaseSnapshotExtension} restores the Flyway seed after each test (no
 * {@code @Transactional}, F-083).
 */
abstract class AbstractReferenceDataViewUiTest extends AbstractUiTest {

    /** Header of the row-action column on both screens. */
    protected static final String ACTIONS = "Actions";

    /** Resolves the signed-in user for {@code navEntryLabels()}. */
    @Autowired
    protected AuthenticationContext authenticationContext;

    /**
     * The same service the views inject — used to assert the active-options query
     * an admin's UI action produced. In the test JVM on every Testing Tool; the
     * tests run as {@code admin@vaadin.com}, so its {@code @RolesAllowed("ADMIN")}
     * reads succeed.
     */
    @Autowired
    protected ReferenceDataService service;

    /** The screen's one grid. Lazy: each use finds it again. */
    protected static UnifiedGrid grid(UnifiedPage page) {
        return page.find(UnifiedGrid.class).single();
    }

    /** The rendered text of one grid column across every row, top to bottom. */
    protected static List<String> columnText(UnifiedPage page, String header) {
        return grid(page).column(header).getCellTexts();
    }

    /**
     * The row-action button carrying {@code accessibleName} in the given grid row's
     * actions cell. The buttons live in a component column, so they are reached
     * through the row's cell: a page-wide lookup misses them on Browserless (F-085).
     */
    protected static UnifiedButton rowAction(UnifiedPage page, int row, String accessibleName) {
        return grid(page).row(row).cell(ACTIONS).find(UnifiedButton.class)
                .withAccessibleName(accessibleName).single();
    }

    /** The button with this visible text — "New", "Save". */
    protected static UnifiedButton button(UnifiedPage page, String text) {
        return page.find(UnifiedButton.class).withText(text).single();
    }

    /**
     * The navigation entries the signed-in user can reach, by label (#146).
     *
     * <p>Asks the model the shell renders from: two of the three nav groups render
     * as menus whose items exist only while open (F-071). Portable as it stands —
     * the server is in the test JVM on every Testing Tool, and
     * {@code @WithUserDetails} sets the test thread's user.
     */
    protected List<String> navEntryLabels() {
        return NavGroup.allVisibleTo(authenticationContext).stream()
                .map(NavGroup.NavItem::label)
                .toList();
    }
}
