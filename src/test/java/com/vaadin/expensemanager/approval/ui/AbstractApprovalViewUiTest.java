package com.vaadin.expensemanager.approval.ui;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.vaadin.expensemanager.base.AbstractUiTest;
import com.vaadin.expensemanager.report.domain.ExpenseLineSpec;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.GeneratedLineSpec;
import com.vaadin.expensemanager.report.domain.QuantityOverride;
import com.vaadin.expensemanager.report.domain.TravelSpec;
import com.vaadin.expensemanager.report.service.ExpenseReportRepository;
import com.vaadin.expensemanager.report.ui.ReportDetailView;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRate;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.user.UserRepository;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Shared base for the Unified approval view tests (pyramid layer 3, ADR-0027
 * proposed) — {@link ApprovalQueueViewUiTest} and {@link ApprovalAccessUiTest}.
 *
 * <p>Extends {@link AbstractUiTest}: no {@code @Transactional} and no explicit
 * clean-up. Reports are seeded straight through the repository + aggregate
 * (bypassing the owner-scoped service, to reach any owner and any status); every
 * seed commits, so a browser's server thread sees it, and the
 * {@code DatabaseSnapshotExtension} removes it after each test.
 *
 * <p>The query helpers below are the portable reads every test shares: the
 * review page's text scoped to the {@code .report-detail} view, the queue and
 * history cards by their {@code .report-card} link, view titles by {@code H2}, and
 * "is there a <em>visible</em> button saying X". The last one cannot be a plain
 * {@code exists()}: the view hides its actions with {@code setVisible(false)}, and
 * a button hidden <em>after</em> it rendered (Approve/Reject once the decision is
 * made) stays in a browser's DOM with {@code hidden} and is still matched on
 * Playwright, while Browserless drops it.
 */
abstract class AbstractApprovalViewUiTest extends AbstractUiTest {

    @Autowired
    protected ExpenseReportRepository reportRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ExpenseTypeRepository expenseTypeRepository;

    @Autowired
    protected VatRateRepository vatRateRepository;

    // ------------------------------------------------------------- queries

    /** The rendered text of the report detail view (owner or review mode). */
    protected static String detailText(UnifiedPage page) {
        return page.findComponent(ReportDetailView.class).withClassName("report-detail")
                .single().element().getText();
    }

    /** The queue / history cards: one {@link RouterLink} per report. */
    protected static List<UnifiedGenericComponent> reportCards(UnifiedPage page) {
        return page.findComponent(RouterLink.class).withClassName("report-card").all();
    }

    /** Every card's text, joined — what the queue / history list shows. */
    protected static String cardsText(UnifiedPage page) {
        return String.join("\n", reportCards(page).stream()
                .map(card -> card.element().getText()).toList());
    }

    /** The hrefs of the cards, e.g. {@code review/42}. */
    protected static List<String> cardHrefs(UnifiedPage page) {
        return reportCards(page).stream()
                .map(card -> card.element().getAttribute("href")).toList();
    }

    /** Whether an {@code H2} with exactly {@code text} renders (view titles, empty states). */
    protected static boolean hasHeading(UnifiedPage page, String text) {
        return page.findComponent(H2.class).withText(text).exists();
    }

    /**
     * Whether a <em>visible</em> button saying exactly {@code text} is on the page.
     * Filters on {@code isVisible()} because a Playwright query still matches a
     * button the server hid after it had rendered.
     */
    protected static boolean hasVisibleButton(UnifiedPage page, String text) {
        return page.find(UnifiedButton.class).withText(text).all().stream()
                .anyMatch(UnifiedButton::isVisible);
    }

    /** As {@link #hasVisibleButton}, by accessible name (icon-only buttons). */
    protected static boolean hasVisibleButtonNamed(UnifiedPage page, String name) {
        return page.find(UnifiedButton.class).withAccessibleName(name).all().stream()
                .anyMatch(UnifiedButton::isVisible);
    }

    // --------------------------------------------------------------- seeds

    /**
     * Seeds a SUBMITTED report owned by {@code email} (any user), bypassing the
     * owner-scoped service, so an admin test sees another user's report in the
     * queue. Returns its id.
     */
    protected Long seedSubmittedReportForOwner(String email, LocalDate date,
            String info) {
        var owner = userRepository.findByEmail(email).orElseThrow();
        var report = new ExpenseReport(owner, date, info);
        report.reconcileLines(List.of(ExpenseLineSpec.of(null, firstType(),
                new BigDecimal("100.00"), firstRate(), null)));
        report.submit(owner, Instant.parse("2026-07-12T09:00:00Z"));
        return reportRepository.save(report).getId();
    }

    /**
     * Seeds an APPROVED report owned by {@code email} (the seeded admin recorded as
     * approver), so an owner test opens its own already-approved report read-only.
     * Returns its id.
     */
    protected Long seedApprovedReportForOwner(String email, LocalDate date,
            String info) {
        var owner = userRepository.findByEmail(email).orElseThrow();
        var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
        var report = new ExpenseReport(owner, date, info);
        report.reconcileLines(List.of(ExpenseLineSpec.of(null, firstType(),
                new BigDecimal("100.00"), firstRate(), null)));
        report.submit(owner, Instant.parse("2026-07-12T09:00:00Z"));
        report.approve(admin, Instant.parse("2026-07-14T08:00:00Z"));
        return reportRepository.save(report).getId();
    }

    /**
     * Seeds a REJECTED report owned by {@code email} (the seeded admin recorded as
     * the rejecting reviewer, with a reason), so a history test sees a rejected
     * outcome. Returns its id.
     */
    protected Long seedRejectedReportForOwner(String email, LocalDate date,
            String info, String reason) {
        var owner = userRepository.findByEmail(email).orElseThrow();
        var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
        var report = new ExpenseReport(owner, date, info);
        report.reconcileLines(List.of(ExpenseLineSpec.of(null, firstType(),
                new BigDecimal("100.00"), firstRate(), null)));
        report.submit(owner, Instant.parse("2026-07-12T09:00:00Z"));
        report.reject(admin, reason, Instant.parse("2026-07-15T08:00:00Z"));
        return reportRepository.save(report).getId();
    }

    /**
     * Seeds a SUBMITTED report owned by {@code email} whose trip carries a
     * <strong>Quantity Override</strong> (ADR-0024): a 55 h domestic trip the
     * calculator awards 2 full days, of which the owner claimed only 1, with a
     * reason. Built through the aggregate like every other seed here, so the review
     * render — badge, reason, and the baseline the mapper recomputes — is the only
     * thing under test. Returns its id.
     */
    protected Long seedSubmittedReportWithOverriddenPerDiem(String email, LocalDate date,
            String info, String reason) {
        var owner = userRepository.findByEmail(email).orElseThrow();
        var report = new ExpenseReport(owner, date, info);
        var departure = LocalDateTime.of(2026, 7, 1, 8, 0);
        var claimed = new GeneratedLineSpec(GeneratedLineKind.PER_DIEM_FULL,
                perDiemType(), zeroVatRate(), new BigDecimal("54.00"), BigDecimal.ONE,
                "Per diem allowance (full day): 1 × €54.00 = €54.00 "
                        + "— overridden from 2 days: " + reason);
        report.reconcileTravels(List.of(new TravelSpec(null, departure,
                departure.plusHours(55), "Helsinki", "Client visit", "Finland",
                false, false, false, BigDecimal.ZERO.setScale(2), false,
                BigDecimal.ZERO.setScale(2),
                Map.of(GeneratedLineKind.PER_DIEM_FULL,
                        new QuantityOverride(BigDecimal.ONE, reason)),
                List.of(claimed))));
        report.submit(owner, Instant.parse("2026-07-12T09:00:00Z"));
        return reportRepository.save(report).getId();
    }

    private ExpenseType perDiemType() {
        return expenseTypeRepository
                .findFirstByNameIgnoreCaseAndActiveTrueOrderByDisplayOrderAscIdAsc(
                        "Travel allowance")
                .orElseThrow();
    }

    private VatRate zeroVatRate() {
        return vatRateRepository
                .findFirstByValueAndActiveTrueOrderByDisplayOrderAscIdAsc(
                        BigDecimal.ZERO.setScale(2))
                .orElseThrow();
    }

    private ExpenseType firstType() {
        return expenseTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()
                .getFirst();
    }

    private VatRate firstRate() {
        return vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc().getFirst();
    }
}
