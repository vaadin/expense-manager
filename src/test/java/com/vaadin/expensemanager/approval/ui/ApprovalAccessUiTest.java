package com.vaadin.expensemanager.approval.ui;

import java.time.LocalDate;

import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.report.ui.ReportDetailView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.html.H2;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * Browserless view test (pyramid layer 3, ADR-0008/ADR-0012) for the approval
 * flow's <strong>non-admin</strong> access rules (Phase 5.1): a plain USER is kept
 * out of the queue route and out of admin review mode, and sees their own
 * approved report read-only.
 *
 * <p>Runs as the seeded plain user via class-level {@link WithUserDetails} (split
 * from the admin cases in {@link ApprovalQueueViewUiTest} so each class pins one
 * user — a method-level mix loses the security context under full-suite ordering,
 * F-020).
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class ApprovalAccessUiTest extends AbstractApprovalViewUiTest {

    @Test
    void aNonAdminCannotReachTheQueueRoute(UnifiedPage page) {
        // Navigation access control rejects a USER: the ADMIN-only view never
        // resolves (mirrors AdminToolsViewUiTest) — the user gets the not-found page.
        page.navigate(ApprovalQueueView.class);
        assertDenied(page);
    }

    @Test
    void aNonAdminCannotReachTheReviewHistoryRoute(UnifiedPage page) {
        page.navigate(ReviewHistoryView.class);
        assertDenied(page);
    }

    @Test
    void aNonAdminCannotEnterReviewMode(UnifiedPage page) {
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        // The review alias is @PermitAll but gated in-view: a USER is forwarded to
        // the dashboard, never rendering the report in review mode.
        page.navigate("review/" + id);
        assertThat(page).isAt(DashboardView.class);
    }

    @Test
    void ownerSeesTheirApprovedReportReadOnly(UnifiedPage page) {
        var id = seedApprovedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        page.navigate(ReportDetailView.class, id);

        assertThat(view(page).findComponent(Badge.class).withText("Approved")).exists();
        for (String action : new String[] { "Save", "Submit for approval", "Delete",
                "Add expense",
                // The owner never sees an Approve action — that is the admin's alone.
                "Approve" }) {
            // visible(): the view hides its actions rather than removing them.
            assertThat(page.find(UnifiedButton.class).withText(action).visible()).doesNotExist();
        }
    }

    private static void assertDenied(UnifiedPage page) {
        assertThat(page.findComponent(H2.class).withText("Page not found")).exists();
        assertThat(page.find(UnifiedGrid.class)).doesNotExist();
    }
}
