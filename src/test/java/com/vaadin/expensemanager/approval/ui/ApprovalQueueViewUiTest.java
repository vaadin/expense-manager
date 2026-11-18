package com.vaadin.expensemanager.approval.ui;

import java.time.LocalDate;

import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.component.UnifiedTextArea;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
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
    void adminSeesSubmittedReportsAcrossOwnersWithReviewLinks(UnifiedPage page) {
        var userReport = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");
        seedSubmittedReportForOwner(ADMIN_EMAIL, LocalDate.of(2026, 6, 1), "admin trip");

        page.navigate(ApprovalQueueView.class);

        assertViewSays(page, "user trip", "admin trip", "Demo User", "Expense Admin");
        // Each row is a real router link into the /review/{id} alias.
        assertThat(reviewLink(page, userReport)).exists();
    }

    @Test
    void anEmptyQueueShowsTheEmptyState(UnifiedPage page) {
        page.navigate(ApprovalQueueView.class);

        assertThat(view(page)).hasTextContaining("Nothing to review");
    }

    @Test
    void adminOpensAReportReadOnlyAndApprovesIt(UnifiedPage page) {
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        page.navigate("review/" + id);

        // Read-only review: no owner editing affordances, but the line is shown.
        assertThat(view(page)).hasTextContaining("user trip");
        assertThat(status(page, "Submitted")).exists();
        assertNoOwnerEditing(page);
        assertThat(button(page, "Submit for approval")).doesNotExist();
        assertThat(button(page, "Delete")).doesNotExist();

        // Approve moves it to APPROVED and drops the action (no longer reviewable).
        button(page, "Approve").click();

        assertThat(status(page, "Approved")).exists();
        assertThat(button(page, "Approve")).doesNotExist();
        assertThat(reportRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.APPROVED);
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

        assertViewSays(page, "Overridden",
                "Reason: the Wednesday was personal",
                "Calculated: 2 × €54.00 = €108.00",
                "€54.00");
        // No editing surface: an approver rejects with a comment, never corrects.
        assertThat(page.find(UnifiedButton.class)
                .withAccessibleName("Edit override: Per diem allowance (full day)").visible())
                .doesNotExist();
        assertThat(page.find(UnifiedButton.class)
                .withAccessibleName("Reset to calculated: Per diem allowance (full day)").visible())
                .doesNotExist();
    }

    @Test
    void rejectingWithAnEmptyCommentShowsTheReasonRequiredErrorAndDoesNotSubmit(UnifiedPage page) {
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        page.navigate("review/" + id);
        button(page, "Reject").click();                     // opens the reason dialog
        UnifiedDialog dialog = page.find(UnifiedDialog.class);
        dialog.find(UnifiedButton.class).withText("Reject report").click(); // empty comment

        // The confirm stays enabled (ADR-0020) and the dialog's error summary shows
        // the reason-required message.
        assertThat(dialog).hasTextContaining("A rejection comment is required.");
        // Still open, nothing submitted: the report is untouched.
        assertThat(dialog).isOpen();
        assertThat(reportRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.SUBMITTED);
    }

    @Test
    void reviewHistoryShowsTerminalReportsAcrossOwnersWithTheDecisionAndReviewLinks(UnifiedPage page) {
        var approvedId = seedApprovedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "approved trip");
        seedRejectedReportForOwner(ADMIN_EMAIL, LocalDate.of(2026, 6, 1),
                "rejected trip", "Please itemise the taxi fares.");
        // A still-pending report must NOT leak into the history.
        seedSubmittedReportForOwner(ADMIN_EMAIL, LocalDate.of(2026, 6, 3), "pending trip");

        page.navigate(ReviewHistoryView.class);

        assertViewSays(page, "approved trip", "rejected trip", "Approved",
                "Rejected", "Demo User", "Expense Admin",
                "Please itemise the taxi fares.");
        assertThat(view(page).element().getText()).doesNotContain("pending trip");
        // Each row is a real router link into the /review/{id} alias.
        assertThat(reviewLink(page, approvedId)).exists();
    }

    @Test
    void anEmptyReviewHistoryShowsTheEmptyState(UnifiedPage page) {
        page.navigate(ReviewHistoryView.class);

        assertThat(view(page)).hasTextContaining("No reviewed reports yet");
    }

    @Test
    void rejectingWithAReasonRecordsItAndMovesTheReportToRejected(UnifiedPage page) {
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        page.navigate("review/" + id);
        button(page, "Reject").click();
        UnifiedDialog dialog = page.find(UnifiedDialog.class);
        dialog.find(UnifiedTextArea.class).withLabel("Rejection comment")
                .setValue("Please itemise the taxi fares.");
        dialog.find(UnifiedButton.class).withText("Reject report").click();

        // The review actions drop (no longer reviewable) and the audit trail with the
        // reason is shown to the admin too.
        assertThat(status(page, "Rejected")).exists();
        assertThat(button(page, "Reject")).doesNotExist();
        assertThat(button(page, "Approve")).doesNotExist();
        // Review mode stays read-only even though REJECTED is owner-editable — the
        // admin must not gain edit/save affordances on another user's report.
        assertNoOwnerEditing(page);
        assertThat(view(page)).hasTextContaining("Please itemise the taxi fares.");
        assertThat(reportRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.REJECTED);
    }

    // --------------------------------------------------------------- helpers

    private static void assertViewSays(UnifiedPage page, String... texts) {
        for (String text : texts) {
            assertThat(view(page)).hasTextContaining(text);
        }
    }

    private static void assertNoOwnerEditing(UnifiedPage page) {
        assertThat(button(page, "Save")).doesNotExist();
        assertThat(button(page, "Add expense")).doesNotExist();
    }

    /**
     * A visible button. Lookups include hidden components on every Testing Tool, and
     * the view hides its actions ({@code setVisible(false)}) rather than removing
     * them, so "the action is gone" means "no visible button".
     */
    private static UnifiedButton button(UnifiedPage page, String text) {
        return page.find(UnifiedButton.class).withText(text).visible();
    }

    private static UnifiedGenericComponent status(UnifiedPage page, String label) {
        return view(page).findComponent(Badge.class).withText(label);
    }

    private static UnifiedGenericComponent reviewLink(UnifiedPage page, Long reportId) {
        return view(page).findComponent(RouterLink.class).withAttribute("href", "review/" + reportId);
    }
}
