package com.vaadin.expensemanager.approval.ui;

import java.time.LocalDate;

import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.report.ui.ReportDetailView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.testing.unified.UnifiedPage;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0027 proposed) for the approval flow's
 * <strong>non-admin</strong> access rules (Phase 5.1): a plain USER is kept out of
 * the queue and history routes and out of admin review mode, and sees their own
 * approved report read-only. Runs unchanged on Browserless and Playwright.
 *
 * <p>Runs as the seeded plain user via class-level {@link WithUserDetails}; the
 * admin cases live in {@link ApprovalQueueViewUiTest}.
 *
 * <p>The route-denial tests used to expect {@code navigate(View.class)} to throw,
 * which only Browserless can do. They now navigate by URL, as a user would, and
 * assert the not-found state renders and the denied view does not — the same
 * shape as {@code UserManagementViewUiTest#userCannotReachRouteByUrl}.
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class ApprovalAccessUiTest extends AbstractApprovalViewUiTest {

    @Test
    void aNonAdminCannotReachTheQueueRoute(UnifiedPage page) {
        page.navigate(DashboardView.class);
        page.navigate("approvals");
        screenshot(page, "approvals-denied");

        // Navigation access control rejects a USER: the ADMIN-only view never
        // renders, and the shell shows its not-found state instead (no leak that
        // the route exists).
        assertThat(hasHeading(page, "Page not found")).isTrue();
        assertThat(hasHeading(page, "Approvals")).isFalse();
        assertThat(reportCards(page)).isEmpty();
    }

    @Test
    void aNonAdminCannotReachTheReviewHistoryRoute(UnifiedPage page) {
        page.navigate(DashboardView.class);
        page.navigate("approval-history");
        screenshot(page, "history-denied");

        assertThat(hasHeading(page, "Page not found")).isTrue();
        assertThat(hasHeading(page, "Review history")).isFalse();
        assertThat(reportCards(page)).isEmpty();
    }

    @Test
    void aNonAdminCannotEnterReviewMode(UnifiedPage page) {
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        // The review alias is @PermitAll but gated in-view: a USER is forwarded to
        // the dashboard, never rendering the report in review mode.
        page.navigate("review/" + id);
        screenshot(page, "review-forwarded");

        assertThat(page).isAt(DashboardView.class);
        assertThat(page.findComponent(ReportDetailView.class)
                .withClassName("report-detail").exists()).isFalse();
    }

    @Test
    void ownerSeesTheirApprovedReportReadOnly(UnifiedPage page) {
        var id = seedApprovedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        page.navigate(ReportDetailView.class, id);
        screenshot(page, "approved-read-only");

        assertThat(detailText(page)).contains("Approved");
        assertThat(hasVisibleButton(page, "Save")).isFalse();
        assertThat(hasVisibleButton(page, "Submit for approval")).isFalse();
        assertThat(hasVisibleButton(page, "Delete")).isFalse();
        assertThat(hasVisibleButton(page, "Add expense")).isFalse();
        // The owner never sees an Approve action — that is the admin's alone.
        assertThat(hasVisibleButton(page, "Approve")).isFalse();
    }
}
