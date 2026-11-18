package com.vaadin.expensemanager.reference.ui;

import java.util.List;

import com.vaadin.expensemanager.base.TableSnapshot;
import com.vaadin.expensemanager.base.ui.NavGroup;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared base for the reference-data settings-screen view tests (pyramid layer 3,
 * ADR-0012) — {@link VatRateViewUiTest}, {@link ExpenseTypeViewUiTest} and
 * {@link ReferenceConfigViewUiTest} (issue #22, ADR-0008/0018). Unified UI tests:
 * they run on every Testing Tool.
 *
 * <p>Consolidates what the screens share so no test repeats it:
 * <ul>
 *   <li>The <strong>singleton {@link PostgreSQLContainer}</strong> mirrors
 *       {@code AbstractIntegrationTest} (F-008). Testcontainers reuse means every
 *       test shares one Docker container.</li>
 *   <li><strong>A {@link TableSnapshot} of {@code vat_rate} and
 *       {@code expense_type}</strong>, taken before each test and restored after
 *       it. The add/edit/reorder/deactivate tests commit what they write — a
 *       browser's request threads never see a test transaction, so the former
 *       {@code @Transactional} rollback only worked on Browserless.</li>
 *   <li>The {@link ReferenceDataService} handle used to assert the active-options
 *       query behind an admin's UI action.</li>
 * </ul>
 */
@UnifiedSpringBootTest
@ActiveProfiles("test")
abstract class AbstractReferenceDataViewUiTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    /** Resolves the signed-in user for {@code navEntryLabels()}. */
    @Autowired
    protected AuthenticationContext authenticationContext;

    /**
     * The same service the views inject — used to assert the active-options query
     * an admin's UI action produced. The tests run as {@code admin@vaadin.com}, so
     * its {@code @RolesAllowed("ADMIN")} reads succeed.
     */
    @Autowired
    protected ReferenceDataService service;

    @Autowired
    private JdbcTemplate jdbc;

    private TableSnapshot referenceData;

    @BeforeEach
    void snapshotReferenceData() {
        referenceData = TableSnapshot.take(jdbc, "vat_rate", "expense_type");
    }

    @AfterEach
    void restoreReferenceData() {
        referenceData.restore();
    }

    /**
     * The row-action button named {@code ariaLabel} in the Actions cell of the row
     * whose {@code column} reads {@code rowText}. The buttons are icon-only (or read
     * a bare verb), so their accessible name is what tells them apart.
     */
    protected static UnifiedButton rowAction(UnifiedGrid grid, String column, String rowText,
            String ariaLabel) {
        return grid.row().withCellText(column, rowText).cell("Actions")
                .find(UnifiedButton.class).withAccessibleName(ariaLabel);
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
