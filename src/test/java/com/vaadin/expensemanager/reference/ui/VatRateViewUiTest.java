package com.vaadin.expensemanager.reference.ui;

import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.html.H2;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedGrid;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified view test (pyramid layer 3, ADR-0012) for {@link VatRateView} —
 * the ADMIN-only VAT-rate settings screen (issue #22, ADR-0008/0018).
 *
 * <p>The grid + add/edit + reorder + active-toggle mechanics are the shared
 * {@link ReferenceConfigView}, covered once in {@link ReferenceConfigViewUiTest}
 * (driven through this view). This class asserts only what is
 * <em>kind-specific</em> to VAT rates: the two-layer access control and the
 * single percent-formatted rate column.
 */
class VatRateViewUiTest extends AbstractReferenceDataViewUiTest {

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesViewWithSeededRatesRendered(UnifiedPage page) {
        page.navigate(VatRateView.class);
        // Percent formatting (trailing zeros trimmed) is the VAT-rate column's own concern.
        assertThat(page.find(UnifiedGrid.class).column("Rate"))
                .hasCellTexts("25.5 %", "13.5 %", "10 %", "0 %");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesMenuEntry(UnifiedPage page) {
        page.navigate(DashboardView.class);
        assertThat(navEntryLabels()).contains("VAT rates");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userSeesNoMenuEntry(UnifiedPage page) {
        page.navigate(DashboardView.class);
        assertThat(navEntryLabels()).doesNotContain("VAT rates");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachRouteByUrl(UnifiedPage page) {
        page.navigate(VatRateView.class);

        // Access control answers a denied route with the not-found page, never the grid.
        assertThat(page.findComponent(H2.class).withText("Page not found")).exists();
        assertThat(page.find(UnifiedGrid.class)).doesNotExist();
    }
}
