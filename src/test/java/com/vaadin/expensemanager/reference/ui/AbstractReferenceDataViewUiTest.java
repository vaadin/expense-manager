package com.vaadin.expensemanager.reference.ui;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.locator.Locators;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.GridLocator;
import com.vaadin.expensemanager.base.ui.NavGroup;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared base for the two reference-data settings-screen view tests (pyramid
 * layer 3, ADR-0012) — {@link VatRateViewUiTest} and
 * {@link ExpenseTypeViewUiTest} (issue #22, ADR-0008/0018).
 *
 * <p>Consolidates the boilerplate the two screens share so neither test repeats
 * it — the singleton Testcontainers Postgres, the browserless setup, the modern
 * locator DSL, and the {@link ReferenceDataService} handle used to assert the
 * active-options query behind an admin's UI action:
 * <ul>
 *   <li><strong>{@code implements Locators}</strong> opts this
 *       {@link SpringBrowserlessTest} subclass into the fluent typed locator API
 *       ({@code findButton()}, {@code findGrid(..)}, …) — the documented
 *       replacement for the deprecated {@code $()} / {@code $view()} aliases.</li>
 *   <li>The <strong>singleton {@link PostgreSQLContainer}</strong> mirrors
 *       {@code AbstractIntegrationTest}: {@link SpringBrowserlessTest} occupies
 *       the single inheritance slot, so we re-declare it here rather than extend
 *       that base (F-008). Testcontainers reuse means both tests share one Docker
 *       container — a shared base class is the composition helper F-008 called
 *       for.</li>
 *   <li><strong>{@code @Transactional}</strong> rolls back each mutating test
 *       method (add/edit/reorder/deactivate all write), so the shared container
 *       stays at its Flyway seed for the next test — the browserless UI, the
 *       view, and the {@code @Transactional} service all run on the one test
 *       thread and share the rolled-back transaction.</li>
 * </ul>
 */
// TODO unified: Browserless-only pattern: @Transactional rolls back what the views write only on Browserless; commit test data and clean up to run everywhere (spec 02)
@UnifiedSpringBootTest
@ActiveProfiles("test")
@Transactional
@RequiresTestingTool(TestingTool.BROWSERLESS)
abstract class AbstractReferenceDataViewUiTest
        implements Locators {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    /**
     * The same service the views inject — used to assert the active-options query
     * an admin's UI action produced (which has no dedicated view surface until the
     * Phase 2.3 line editor exists). The tests run as {@code admin@vaadin.com}, so
     * its {@code @RolesAllowed("ADMIN")} reads succeed.
     */
    /** Resolves the signed-in user for {@code navEntryLabels()}. */
    @Autowired
    protected AuthenticationContext authenticationContext;

    @Autowired
    protected ReferenceDataService service;

    /** The rendered text of one grid column across every row, top to bottom. */
    protected static List<String> columnText(GridLocator<?> grid, int column) {
        var values = new ArrayList<String>();
        for (int row = 0; row < grid.size(); row++) {
            values.add(grid.getCellText(row, column));
        }
        return values;
    }

    /**
     * The row-action button carrying {@code ariaLabel} in the given grid row's
     * actions cell. The action buttons live inside a {@code Grid} component
     * column, so they aren't reachable from a UI-wide {@code findButton()} —
     * scope the search to the cell component via {@code getCellComponent} first.
     */
    protected Button rowActionButton(UnifiedPage page, GridLocator<?> grid, int row, int actionsColumn, String ariaLabel) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        var cell = grid.getCellComponent(row, actionsColumn);
        // TODO unified: Browserless Escape Hatch: find(Button.class, cell).withAriaLabel(ariaLabel).single()
        return browserless.find(Button.class, cell).withAriaLabel(ariaLabel).single();
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
    protected List<String> navEntryLabels() {
        return NavGroup.allVisibleTo(authenticationContext).stream()
                .map(NavGroup.NavItem::label)
                .toList();
    }
}
