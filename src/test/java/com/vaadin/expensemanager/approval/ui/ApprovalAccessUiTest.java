package com.vaadin.expensemanager.approval.ui;

import java.time.LocalDate;

import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.report.ui.ReportDetailView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void aNonAdminCannotReachTheQueueRoute(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // Navigation access control rejects a USER: the ADMIN-only view never
        // resolves (mirrors AdminToolsViewUiTest).
        // TODO unified: Browserless Escape Hatch: navigate(ApprovalQueueView.class)
        assertThatThrownBy(() -> browserless.navigate(ApprovalQueueView.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void aNonAdminCannotReachTheReviewHistoryRoute(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // TODO unified: Browserless Escape Hatch: navigate(ReviewHistoryView.class)
        assertThatThrownBy(() -> browserless.navigate(ReviewHistoryView.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void aNonAdminCannotEnterReviewMode(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        // The review alias is @PermitAll but gated in-view: a USER is forwarded to
        // the dashboard, never rendering the report in review mode.
        // TODO unified: Browserless Escape Hatch:// The review alias is @PermitAll but gated in-view: a USER is forwarded to// the dashboard, never rendering the report in review mode.navigate("review/" + id, DashboardView.class)
        browserless.navigate("review/" + id, DashboardView.class);
        // TODO unified: Browserless Escape Hatch: getCurrentView()
        assertThat(browserless.getCurrentView()).isInstanceOf(DashboardView.class);
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void ownerSeesTheirApprovedReportReadOnly(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        var id = seedApprovedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        page.navigate(ReportDetailView.class, id);

        // TODO unified: Browserless Escape Hatch: getCurrentView().getElement().getTextRecursively()
        var text = browserless.getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("Approved");
        assertThat(findButton().withText("Save").exists()).isFalse();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();
        assertThat(findButton().withText("Delete").exists()).isFalse();
        assertThat(findButton().withText("Add expense").exists()).isFalse();
        // The owner never sees an Approve action — that is the admin's alone.
        assertThat(findButton().withText("Approve").exists()).isFalse();
    }
}
