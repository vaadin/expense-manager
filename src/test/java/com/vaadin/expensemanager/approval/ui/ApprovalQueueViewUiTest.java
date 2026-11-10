package com.vaadin.expensemanager.approval.ui;

import java.time.LocalDate;

import com.vaadin.expensemanager.base.ui.ErrorSummary;
import com.vaadin.expensemanager.base.ui.unified.UnifiedTextArea;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.ui.ReportDetailView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0027 proposed) for the approval tracer
 * bullet's <strong>admin</strong> surfaces (Phase 5.1): the ADMIN-only queue
 * ({@link ApprovalQueueView}), the review history ({@link ReviewHistoryView}) and
 * the admin-review mode on {@link ReportDetailView} reached from them. Runs
 * unchanged on Browserless and Playwright. The non-admin access checks live in
 * {@link ApprovalAccessUiTest}.
 *
 * <p>Runs as the seeded admin via class-level {@link WithUserDetails}. Covers
 * cross-owner queue visibility, the empty states, the read-only review, approve
 * and reject (with and without a comment), and the overridden-line render, with
 * persisted state asserted through the repository.
 *
 * <p>The queue and history assertions read the <em>cards'</em> text, not the
 * whole page: the shell header shows the signed-in admin's name ("Expense
 * Admin"), which would make the submitter assertion pass on its own. The
 * reject-dialog error is read from the dialog's {@link ErrorSummary} — the dialog
 * is not inside the routed view (F-036).
 */
@WithUserDetails("admin@vaadin.com")
class ApprovalQueueViewUiTest extends AbstractApprovalViewUiTest {

    private static final String ADMIN_EMAIL = "admin@vaadin.com";

    @Test
    void adminSeesSubmittedReportsAcrossOwnersWithReviewLinks(UnifiedPage page) {
        var userReport = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");
        seedSubmittedReportForOwner(ADMIN_EMAIL, LocalDate.of(2026, 6, 1), "admin trip");

        page.navigate(ApprovalQueueView.class);
        screenshot(page, "queue");

        assertThat(cardsText(page)).contains("user trip", "admin trip", "Demo User",
                "Expense Admin");
        // Each row is a real router link into the /review/{id} alias.
        assertThat(cardHrefs(page)).contains("review/" + userReport);
    }

    @Test
    void anEmptyQueueShowsTheEmptyState(UnifiedPage page) {
        page.navigate(ApprovalQueueView.class);
        screenshot(page, "empty-queue");

        assertThat(hasHeading(page, "Nothing to review")).isTrue();
    }

    @Test
    void adminOpensAReportReadOnlyAndApprovesIt(UnifiedPage page) {
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        page.navigate("review/" + id);
        screenshot(page, "review");

        // Read-only review: no owner editing affordances, but the line is shown.
        assertThat(detailText(page)).contains("user trip", "Submitted");
        assertThat(hasVisibleButton(page, "Save")).isFalse();
        assertThat(hasVisibleButton(page, "Submit for approval")).isFalse();
        assertThat(hasVisibleButton(page, "Delete")).isFalse();
        assertThat(hasVisibleButton(page, "Add expense")).isFalse();

        // Approve moves it to APPROVED and drops the action (no longer reviewable).
        page.find(UnifiedButton.class).withText("Approve").single().click();
        screenshot(page, "approved");

        assertThat(reportRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.APPROVED);
        assertThat(detailText(page)).contains("Approved");
        assertThat(hasVisibleButton(page, "Approve")).isFalse();
    }

    @Test
    void anApproverSeesAnOverriddenLineWithItsReasonAndBaselineReadOnly(UnifiedPage page) {
        // Quantity Override (ADR-0024): the approver must see where a judgement call
        // was made, why, and how far it deviates from the statutory figure — through
        // the same review route, entirely read-only.
        var id = seedSubmittedReportWithOverriddenPerDiem(
                LocalUserSeeder.PLAIN_USER_EMAIL, LocalDate.of(2026, 6, 2), "user trip",
                "the Wednesday was personal");

        page.navigate("review/" + id);
        screenshot(page, "overridden-line");

        assertThat(detailText(page)).contains("Overridden",
                "Reason: the Wednesday was personal",
                "Calculated: 2 × €54.00 = €108.00",
                "€54.00");
        // No editing surface: an approver rejects with a comment, never corrects.
        assertThat(hasVisibleButtonNamed(page,
                "Edit override: Per diem allowance (full day)")).isFalse();
        assertThat(hasVisibleButtonNamed(page,
                "Reset to calculated: Per diem allowance (full day)")).isFalse();
    }

    @Test
    void rejectingWithAnEmptyCommentShowsTheReasonRequiredErrorAndDoesNotSubmit(
            UnifiedPage page) {
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        page.navigate("review/" + id);
        page.find(UnifiedButton.class).withText("Reject").single().click(); // opens the dialog
        screenshot(page, "reject-dialog");
        var dialog = rejectDialog(page);
        dialog.find(UnifiedButton.class).withText("Reject report").single()
                .click(); // confirm with an empty comment
        screenshot(page, "reason-required");

        // The confirm stays enabled (ADR-0020) and the dialog's error summary shows
        // the reason-required message.
        assertThat(dialog.findComponent(ErrorSummary.class).withClassName("error-summary")
                .single().element().getText())
                .contains("A rejection comment is required.");
        // Still open, nothing submitted: the report is untouched.
        assertThat(dialog).isOpen();
        assertThat(dialog.find(UnifiedButton.class).withText("Reject report").single())
                .isVisible();
        assertThat(reportRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.SUBMITTED);
    }

    @Test
    void reviewHistoryShowsTerminalReportsAcrossOwnersWithTheDecisionAndReviewLinks(
            UnifiedPage page) {
        var approvedId = seedApprovedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "approved trip");
        seedRejectedReportForOwner(ADMIN_EMAIL, LocalDate.of(2026, 6, 1),
                "rejected trip", "Please itemise the taxi fares.");
        // A still-pending report must NOT leak into the history.
        seedSubmittedReportForOwner(ADMIN_EMAIL, LocalDate.of(2026, 6, 3), "pending trip");

        page.navigate(ReviewHistoryView.class);
        screenshot(page, "history");

        var text = cardsText(page);
        assertThat(text).contains("approved trip", "rejected trip", "Approved",
                "Rejected", "Demo User", "Expense Admin",
                "Please itemise the taxi fares.");
        assertThat(text).doesNotContain("pending trip");
        // Each row is a real router link into the /review/{id} alias.
        assertThat(cardHrefs(page)).contains("review/" + approvedId);
    }

    @Test
    void anEmptyReviewHistoryShowsTheEmptyState(UnifiedPage page) {
        page.navigate(ReviewHistoryView.class);
        screenshot(page, "empty-history");

        assertThat(hasHeading(page, "No reviewed reports yet")).isTrue();
    }

    @Test
    void rejectingWithAReasonRecordsItAndMovesTheReportToRejected(UnifiedPage page) {
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        page.navigate("review/" + id);
        page.find(UnifiedButton.class).withText("Reject").single().click();
        var dialog = rejectDialog(page);
        dialog.find(UnifiedTextArea.class).withLabel("Rejection comment").single()
                .setValue("Please itemise the taxi fares.");
        screenshot(page, "reject-with-reason");
        dialog.find(UnifiedButton.class).withText("Reject report").single().click();
        screenshot(page, "rejected");

        assertThat(reportRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.REJECTED);
        // The review actions drop (no longer reviewable) and the audit trail with the
        // reason is shown to the admin too.
        assertThat(hasVisibleButton(page, "Reject")).isFalse();
        assertThat(hasVisibleButton(page, "Approve")).isFalse();
        // Review mode stays read-only even though REJECTED is owner-editable — the
        // admin must not gain edit/save affordances on another user's report.
        assertThat(hasVisibleButton(page, "Save")).isFalse();
        assertThat(hasVisibleButton(page, "Add expense")).isFalse();
        assertThat(detailText(page)).contains("Rejected", "Please itemise the taxi fares.");
    }

    // --------------------------------------------------------------- helpers

    private static UnifiedDialog rejectDialog(UnifiedPage page) {
        var dialog = page.find(UnifiedDialog.class).withHeaderTitle("Reject report").single();
        assertThat(dialog).isOpen();
        return dialog;
    }
}
