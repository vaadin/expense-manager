package com.vaadin.expensemanager.approval.ui;

import java.time.LocalDate;

import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.ui.ReportDetailView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for the approval tracer
 * bullet's <strong>admin</strong> surfaces (Phase 5.1): the ADMIN-only queue
 * ({@link ApprovalQueueView}) and the admin-review mode on
 * {@link ReportDetailView} reached from it. The non-admin access checks live in
 * {@link ApprovalAccessUiTest}.
 *
 * <p>Runs as the seeded admin via class-level {@link WithUserDetails} — the same
 * shape as {@code ReportDetailViewUiTest} (a method-level mix of users on this
 * {@code @Transactional} base loses the security context under full-suite
 * ordering, F-020, so admin and user cases are split into two classes). Covers
 * cross-owner queue visibility, the empty state, and the read-only review +
 * approve happy path, with persisted state asserted through the repository. Mind
 * F-035 (signal short-circuit) and F-036 (overlay/visibility).
 *
 * <p>Runs as the seeded admin via class-level {@link WithUserDetails}, the same
 * pattern as {@code ReportDetailViewUiTest}/{@code AdminToolsViewUiTest}.
 */
@WithUserDetails("admin@vaadin.com")
class ApprovalQueueViewUiTest extends AbstractApprovalViewUiTest {

    private static final String ADMIN_EMAIL = "admin@vaadin.com";

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void adminSeesSubmittedReportsAcrossOwnersWithReviewLinks(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        var userReport = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");
        seedSubmittedReportForOwner(ADMIN_EMAIL, LocalDate.of(2026, 6, 1), "admin trip");

        page.navigate(ApprovalQueueView.class);

        // TODO unified: Browserless Escape Hatch: getCurrentView().getElement().getTextRecursively()
        var text = browserless.getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("user trip", "admin trip", "Demo User",
                "Expense Admin");
        // Each row is a real router link into the /review/{id} alias.
        // TODO unified: Browserless Escape Hatch: $(RouterLink.class).all().stream().anyMatch(a -> ("review/" + userReport).equals(a.getElement().getAttribute("href"))), no Wrapper for RouterLink: write one (ADR 0003)
        assertThat(browserless.$(RouterLink.class).all().stream()
                .anyMatch(a -> ("review/" + userReport).equals(
                        a.getElement().getAttribute("href")))).isTrue();
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void anEmptyQueueShowsTheEmptyState(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        page.navigate(ApprovalQueueView.class);

        // TODO unified: Browserless Escape Hatch: getCurrentView().getElement().getTextRecursively()
        assertThat(browserless.getCurrentView().getElement().getTextRecursively())
                .contains("Nothing to review");
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void adminOpensAReportReadOnlyAndApprovesIt(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        // TODO unified: Browserless Escape Hatch:navigate("review/" + id, ReportDetailView.class)
        browserless.navigate("review/" + id, ReportDetailView.class);

        // Read-only review: no owner editing affordances, but the line is shown.
        // TODO unified: Browserless Escape Hatch: getCurrentView().getElement().getTextRecursively()
        var text = browserless.getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("user trip", "Submitted");
        assertThat(findButton().withText("Save").exists()).isFalse();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();
        assertThat(findButton().withText("Delete").exists()).isFalse();
        assertThat(findButton().withText("Add expense").exists()).isFalse();

        // Approve moves it to APPROVED and drops the action (no longer reviewable).
        findButton().withText("Approve").click();

        assertThat(reportRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.APPROVED);
        // TODO unified: Browserless Escape Hatch: getCurrentView().getElement().getTextRecursively()
        assertThat(browserless.getCurrentView().getElement().getTextRecursively())
                .contains("Approved");
        assertThat(findButton().withText("Approve").exists()).isFalse();
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void anApproverSeesAnOverriddenLineWithItsReasonAndBaselineReadOnly(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // Quantity Override (ADR-0024): the approver must see where a judgement call
        // was made, why, and how far it deviates from the statutory figure — through
        // the same review route, entirely read-only.
        var id = seedSubmittedReportWithOverriddenPerDiem(
                LocalUserSeeder.PLAIN_USER_EMAIL, LocalDate.of(2026, 6, 2), "user trip",
                "the Wednesday was personal");

        // TODO unified: Browserless Escape Hatch:navigate("review/" + id, ReportDetailView.class)
        browserless.navigate("review/" + id, ReportDetailView.class);

        // TODO unified: Browserless Escape Hatch: getCurrentView().getElement().getTextRecursively()
        var text = browserless.getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("Overridden",
                "Reason: the Wednesday was personal",
                "Calculated: 2 × €54.00 = €108.00",
                "€54.00");
        // No editing surface: an approver rejects with a comment, never corrects.
        assertThat(findButton()
                .withAriaLabel("Edit override: Per diem allowance (full day)").exists())
                .isFalse();
        assertThat(findButton()
                .withAriaLabel("Reset to calculated: Per diem allowance (full day)")
                .exists()).isFalse();
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void rejectingWithAnEmptyCommentShowsTheReasonRequiredErrorAndDoesNotSubmit(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        // TODO unified: Browserless Escape Hatch:navigate("review/" + id, ReportDetailView.class)
        browserless.navigate("review/" + id, ReportDetailView.class);
        findButton().withText("Reject").click();          // opens the reason dialog
        findButton().withText("Reject report").click();   // confirm with empty comment

        // The confirm stays enabled (ADR-0020) and the dialog's error summary shows
        // the reason-required message — asserted under the UI, as the dialog overlay
        // attaches to the UI, not the routed view (F-036).
        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("A rejection comment is required.");
        // Still open, nothing submitted: the report is untouched.
        assertThat(findButton().withText("Reject report").exists()).isTrue();
        assertThat(reportRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.SUBMITTED);
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void reviewHistoryShowsTerminalReportsAcrossOwnersWithTheDecisionAndReviewLinks(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        var approvedId = seedApprovedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "approved trip");
        seedRejectedReportForOwner(ADMIN_EMAIL, LocalDate.of(2026, 6, 1),
                "rejected trip", "Please itemise the taxi fares.");
        // A still-pending report must NOT leak into the history.
        seedSubmittedReportForOwner(ADMIN_EMAIL, LocalDate.of(2026, 6, 3), "pending trip");

        page.navigate(ReviewHistoryView.class);

        // TODO unified: Browserless Escape Hatch: getCurrentView().getElement().getTextRecursively()
        var text = browserless.getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("approved trip", "rejected trip", "Approved",
                "Rejected", "Demo User", "Expense Admin",
                "Please itemise the taxi fares.");
        assertThat(text).doesNotContain("pending trip");
        // Each row is a real router link into the /review/{id} alias.
        // TODO unified: Browserless Escape Hatch: $(RouterLink.class).all().stream().anyMatch(a -> ("review/" + approvedId).equals(a.getElement().getAttribute("href"))), no Wrapper for RouterLink: write one (ADR 0003)
        assertThat(browserless.$(RouterLink.class).all().stream()
                .anyMatch(a -> ("review/" + approvedId).equals(
                        a.getElement().getAttribute("href")))).isTrue();
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void anEmptyReviewHistoryShowsTheEmptyState(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        page.navigate(ReviewHistoryView.class);

        // TODO unified: Browserless Escape Hatch: getCurrentView().getElement().getTextRecursively()
        assertThat(browserless.getCurrentView().getElement().getTextRecursively())
                .contains("No reviewed reports yet");
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void rejectingWithAReasonRecordsItAndMovesTheReportToRejected(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        // TODO unified: Browserless Escape Hatch:navigate("review/" + id, ReportDetailView.class)
        browserless.navigate("review/" + id, ReportDetailView.class);
        findButton().withText("Reject").click();
        findTextArea().withLabel("Rejection comment")
                .setValue("Please itemise the taxi fares.");
        findButton().withText("Reject report").click();

        assertThat(reportRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.REJECTED);
        // The review actions drop (no longer reviewable) and the audit trail with the
        // reason is shown to the admin too.
        assertThat(findButton().withText("Reject").exists()).isFalse();
        assertThat(findButton().withText("Approve").exists()).isFalse();
        // Review mode stays read-only even though REJECTED is owner-editable — the
        // admin must not gain edit/save affordances on another user's report.
        assertThat(findButton().withText("Save").exists()).isFalse();
        assertThat(findButton().withText("Add expense").exists()).isFalse();
        // TODO unified: Browserless Escape Hatch: getCurrentView().getElement().getTextRecursively()
        assertThat(browserless.getCurrentView().getElement().getTextRecursively())
                .contains("Rejected", "Please itemise the taxi fares.");
    }
}
