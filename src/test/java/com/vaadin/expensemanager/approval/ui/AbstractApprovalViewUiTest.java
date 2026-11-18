package com.vaadin.expensemanager.approval.ui;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vaadin.expensemanager.report.domain.ExpenseLineSpec;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.GeneratedLineSpec;
import com.vaadin.expensemanager.report.domain.QuantityOverride;
import com.vaadin.expensemanager.report.domain.TravelSpec;
import com.vaadin.expensemanager.report.service.ExpenseReportRepository;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRate;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.user.UserRepository;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedMain;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared base for the approval view tests (pyramid layer 3, ADR-0012) —
 * {@link ApprovalQueueViewUiTest} and {@link ApprovalAccessUiTest}.
 *
 * <p>Modelled on {@code AdminToolsViewUiTest}, <strong>not</strong> the report
 * feature's {@code AbstractReportViewUiTest}: it is deliberately <em>not</em>
 * {@code @Transactional} (which also lets it run on every Testing Tool). Rollback
 * isolation interacts badly with browserless security-context ordering once
 * several browserless classes have run (a report opens as {@code LoginView},
 * F-020/F-008 family), whereas the non-transactional {@code @WithUserDetails}
 * browserless pattern is stable. So reports are seeded straight through the
 * repository + aggregate (bypassing the owner-scoped service, to reach any owner
 * and any status) and cleaned up explicitly in {@link #cleanUp()}.
 *
 * <p>The singleton Testcontainers Postgres is re-declared (F-008) and attaches to
 * the same container via {@code .withReuse(true)}.
 */
@UnifiedSpringBootTest
@ActiveProfiles("test")
abstract class AbstractApprovalViewUiTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Autowired
    protected ExpenseReportRepository reportRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ExpenseTypeRepository expenseTypeRepository;

    @Autowired
    protected VatRateRepository vatRateRepository;

    private final List<Long> createdReportIds = new ArrayList<>();

    /** The routed view's column inside {@code MainLayout}, for what the page says. */
    protected static UnifiedMain view(UnifiedPage page) {
        return page.find(UnifiedMain.class).withClassName("app-shell__content");
    }

    @AfterEach
    void cleanUp() {
        // No rollback here (non-transactional): remove the reports seeded through
        // the repository so the shared container stays clean for other classes.
        createdReportIds.forEach(id ->
                reportRepository.findById(id).ifPresent(reportRepository::delete));
        createdReportIds.clear();
    }

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
        return track(reportRepository.save(report).getId());
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
        return track(reportRepository.save(report).getId());
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
        return track(reportRepository.save(report).getId());
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
        return track(reportRepository.save(report).getId());
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

    private Long track(Long id) {
        createdReportIds.add(id);
        return id;
    }

    private ExpenseType firstType() {
        return expenseTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()
                .getFirst();
    }

    private VatRate firstRate() {
        return vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc().getFirst();
    }
}
