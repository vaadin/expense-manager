package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.locator.Locators;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseLineDto;
import com.vaadin.expensemanager.report.service.ExpenseReportRepository;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.report.service.ReceiptUpload;
import com.vaadin.expensemanager.report.service.ReportDetailDto;
import com.vaadin.expensemanager.report.service.TravelDto;
import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Browserless base for {@link ReportDetailViewUiTest} (pyramid layer 3, ADR-0012),
 * until it moves to the Unified {@link AbstractReportUiTest} like
 * {@link MyReportsViewUiTest} did. The seed helpers delegate to the shared
 * {@link ReportFixtures}.
 *
 * <p>Mirrors {@code AbstractReferenceDataViewUiTest}: the singleton
 * Testcontainers Postgres (re-declared because {@link SpringBrowserlessTest}
 * occupies the single inheritance slot, F-008), {@code implements Locators} for
 * the fluent typed locator DSL, and {@code @Transactional} rollback so reports a
 * test seeds through the service roll back and leave the shared container clean.
 * The {@link ExpenseReportService} handle seeds the current user's reports and
 * asserts persisted state behind a UI action.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
abstract class AbstractReportViewUiTest extends SpringBrowserlessTest
        implements Locators {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Autowired
    protected ExpenseReportService service;

    @Autowired
    protected ExpenseReportRepository reportRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ReferenceDataService referenceData;

    @Autowired
    private PlatformTransactionManager transactions;

    private ReportFixtures fixtures() {
        return new ReportFixtures(service, reportRepository, userRepository, referenceData,
                transactions);
    }

    protected Long seedReport(LocalDate date, String info) {
        return fixtures().seedReport(date, info);
    }

    /** Seeds one DRAFT report owned by the current user with a single line. */
    protected Long seedReportWithLine(LocalDate date, String amount) {
        return fixtures().seedReportWithLine(date, amount);
    }

    /**
     * Seeds a report with one line and submits it, so tests can open an already
     * {@code SUBMITTED} (read-only) report. Returns its id.
     */
    protected Long seedSubmittedReport(LocalDate date, String amount) {
        return fixtures().seedSubmittedReport(date, amount);
    }

    /**
     * Seeds a report owned by the current user, submits it, then has the seeded
     * admin reject it with {@code reason} — so an owner test can open its own
     * {@code REJECTED} report and see the real reason/rejecter/date. The reject
     * goes through the aggregate directly (the owner-scoped service has no admin
     * path); the report stays owned by the current user, so {@code findMine} loads
     * it. Returns its id.
     */
    protected Long seedRejectedReport(LocalDate date, String amount, String reason) {
        return fixtures().seedRejectedReport(date, amount, reason);
    }

    /**
     * Seeds a report with one line, submits it, then has the seeded admin approve
     * it — so a test can open its own {@code APPROVED} (terminal, read-only) report.
     * Like {@link #seedRejectedReport} the transition goes through the aggregate
     * directly; the report stays owned by the current user. Returns its id.
     */
    protected Long seedApprovedReport(LocalDate date, String amount) {
        return fixtures().seedApprovedReport(date, amount);
    }

    /**
     * Seeds a {@code REJECTED} report and then strips it back to zero lines while it
     * is still (owner-)editable — the fixture for the empty-resubmit block. The line
     * removal commits through the service, so the persisted report the resubmit acts
     * on truly has no lines. Returns its id.
     */
    protected Long seedEmptyRejectedReport(LocalDate date, String reason) {
        return fixtures().seedEmptyRejectedReport(date, reason);
    }

    protected static byte[] jpegBytes() {
        return ReportFixtures.jpegBytes();
    }

    protected static byte[] pdfBytes() {
        return ReportFixtures.pdfBytes();
    }

    /** Seeds a DRAFT report with one line carrying a JPEG receipt; returns its id. */
    protected Long seedReportWithReceipt(LocalDate date, String amount,
            String filename) {
        return fixtures().seedReportWithReceipt(date, amount, filename);
    }

    /** Seeds a DRAFT report with one line carrying the given receipt bytes. */
    protected Long seedReportWithReceipt(LocalDate date, String amount,
            String filename, byte[] bytes) {
        return fixtures().seedReportWithReceipt(date, amount, filename, bytes);
    }

    /** Seeds a SUBMITTED report whose single line carries a receipt. */
    protected Long seedSubmittedReportWithReceipt(LocalDate date, String amount,
            String filename) {
        return fixtures().seedSubmittedReportWithReceipt(date, amount, filename);
    }

    /** Seeds a DRAFT report with one domestic trip (11 h → €54.00); returns its id. */
    protected Long seedReportWithTravel(LocalDate date, LocalDateTime departure,
            LocalDateTime returnAt) {
        return fixtures().seedReportWithTravel(date, departure, returnAt);
    }

    /**
     * Seeds a DRAFT report with one domestic trip carrying the Phase 4.3 km / meal /
     * parking inputs, so tests can drive the kilometre / meal subtotal rows and the
     * VAT-bearing parking line. Returns its id.
     */
    protected Long seedReportWithFullTravel(LocalDate date, LocalDateTime departure,
            LocalDateTime returnAt, BigDecimal kilometres, boolean payMeal,
            BigDecimal parkingFees) {
        return fixtures().seedReportWithFullTravel(date, departure, returnAt, kilometres, payMeal, parkingFees);
    }

    /**
     * Seeds a DRAFT report with one domestic trip that earns a <em>meal allowance</em>
     * instead of a per-diem: the two are mutually exclusive under the Finnish rule
     * (issue #93), so the trip is flagged not eligible. Returns its id.
     */
    protected Long seedReportWithMealTravel(LocalDate date, LocalDateTime departure,
            LocalDateTime returnAt) {
        return fixtures().seedReportWithMealTravel(date, departure, returnAt);
    }

    /** Seeds a SUBMITTED report whose single generated line came from a trip. */
    protected Long seedSubmittedReportWithTravel(LocalDate date,
            LocalDateTime departure, LocalDateTime returnAt) {
        return fixtures().seedSubmittedReportWithTravel(date, departure, returnAt);
    }

    /** The first active expense type / VAT rate, for driving the line editor. */
    protected ExpenseTypeDto firstActiveType() {
        return fixtures().firstActiveType();
    }

    protected VatRateDto firstActiveRate() {
        return fixtures().firstActiveRate();
    }

    /**
     * Seeds a report owned by the seeded admin (a <em>different</em> user),
     * bypassing the owner-scoped service, so tests can prove the current user's
     * views never surface it.
     */
    protected Long seedReportForAdmin(LocalDate date, String info) {
        return fixtures().seedReportForAdmin(date, info);
    }
}
