package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
import com.vaadin.expensemanager.base.TableSnapshot;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedMain;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * Shared base for the report view tests (pyramid layer 3, ADR-0012) —
 * {@link MyReportsViewUiTest} and {@link ReportDetailViewUiTest}. Unified UI
 * tests: they run on every Testing Tool.
 *
 * <p>Mirrors {@code AbstractReferenceDataViewUiTest}: the singleton
 * Testcontainers Postgres (F-008), and a {@link TableSnapshot} of
 * {@code expense_report} — plus the reference tables a test may add a type or rate
 * to — taken before each test and restored after it, so the reports a test seeds —
 * or the view saves — are deleted again (their lines, trips, receipts and status
 * history go with them, {@code on delete cascade}).
 * A browser's request threads never see a test transaction, so the former
 * {@code @Transactional} rollback only worked on Browserless. The
 * {@link ExpenseReportService} handle seeds the current user's reports and
 * asserts persisted state behind a UI action.
 */
@UnifiedSpringBootTest
@ActiveProfiles("test")
abstract class AbstractReportViewUiTest {

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
    private JdbcTemplate jdbc;

    /** For seeds that walk a lazy association outside the service layer. */
    @Autowired
    protected TransactionTemplate transactions;

    private TableSnapshot reports;

    @BeforeEach
    void snapshotReports() {
        // Parents first: restore runs in reverse, so the reports (and the lines that
        // point at a type) go before a type a test created is deleted.
        reports = TableSnapshot.take(jdbc, "vat_rate", "expense_type", "expense_report");
    }

    @AfterEach
    void restoreReports() {
        reports.restore();
    }

    /** The routed view's column inside {@code MainLayout}, for what the page says. */
    protected static UnifiedMain view(UnifiedPage page) {
        return page.find(UnifiedMain.class).withClassName("app-shell__content");
    }

    /** The page says {@code text} (retrying until it does). */
    protected static void assertSays(UnifiedPage page, String... texts) {
        for (String text : texts) {
            assertThat(view(page)).hasTextContaining(text);
        }
    }

    /** The page does not say {@code text} (waiting until it no longer does). */
    protected static void assertDoesNotSay(UnifiedPage page, String... texts) {
        for (String text : texts) {
            page.waitUntil(() -> !view(page).element().getText().contains(text));
        }
    }

    /** Seeds one DRAFT report owned by the current user; returns its id. */
    protected Long seedReport(LocalDate date, String info) {
        var zero = BigDecimal.ZERO.setScale(2);
        return service.create(new ReportDetailDto(null, date, info,
                ReportStatus.DRAFT, 0L, List.of(), zero, zero, zero));
    }

    /** Seeds one DRAFT report owned by the current user with a single line. */
    protected Long seedReportWithLine(LocalDate date, String amount) {
        var type = referenceData.activeExpenseTypes().getFirst();
        var rate = referenceData.activeVatRates().getFirst();
        var line = ExpenseLineDto.of(null, type.id(), type.name(), type.icon(),
                rate.id(), rate.value(), new BigDecimal(amount), null);
        var zero = BigDecimal.ZERO.setScale(2);
        return service.create(new ReportDetailDto(null, date, "seed",
                ReportStatus.DRAFT, 0L, List.of(line), zero, zero, zero));
    }

    /**
     * Seeds a report with one line and submits it, so tests can open an already
     * {@code SUBMITTED} (read-only) report. Returns its id.
     */
    protected Long seedSubmittedReport(LocalDate date, String amount) {
        var id = seedReportWithLine(date, amount);
        service.submit(id, service.findMine(id).version());
        return id;
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
        var id = seedSubmittedReport(date, amount);
        // In a transaction of its own: the aggregate's status history is lazy, and
        // these tests commit what they write (no class-level @Transactional).
        transactions.executeWithoutResult(status -> {
            var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
            var report = reportRepository.findById(id).orElseThrow();
            report.reject(admin, reason, Instant.parse("2026-07-14T08:00:00Z"));
            reportRepository.save(report);
        });
        return id;
    }

    /**
     * Seeds a report with one line, submits it, then has the seeded admin approve
     * it — so a test can open its own {@code APPROVED} (terminal, read-only) report.
     * Like {@link #seedRejectedReport} the transition goes through the aggregate
     * directly; the report stays owned by the current user. Returns its id.
     */
    protected Long seedApprovedReport(LocalDate date, String amount) {
        var id = seedSubmittedReport(date, amount);
        transactions.executeWithoutResult(status -> {
            var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
            var report = reportRepository.findById(id).orElseThrow();
            report.approve(admin, Instant.parse("2026-07-14T08:00:00Z"));
            reportRepository.save(report);
        });
        return id;
    }

    /**
     * Seeds a {@code REJECTED} report and then strips it back to zero lines while it
     * is still (owner-)editable — the fixture for the empty-resubmit block. The line
     * removal commits through the service, so the persisted report the resubmit acts
     * on truly has no lines. Returns its id.
     */
    protected Long seedEmptyRejectedReport(LocalDate date, String reason) {
        var id = seedRejectedReport(date, "100", reason);
        var loaded = service.findMine(id);
        var zero = BigDecimal.ZERO.setScale(2);
        service.update(id, new ReportDetailDto(id, loaded.reportDate(),
                loaded.additionalInformation(), loaded.status(), loaded.version(),
                List.of(), zero, zero, zero), loaded.version());
        return id;
    }

    /** A minimal but valid JPEG (magic bytes {@code FF D8 FF} + padding). */
    protected static byte[] jpegBytes() {
        byte[] file = new byte[16];
        file[0] = (byte) 0xFF;
        file[1] = (byte) 0xD8;
        file[2] = (byte) 0xFF;
        return file;
    }

    /** A minimal but valid PDF (magic bytes {@code 25 50 44 46} — {@code %PDF}). */
    protected static byte[] pdfBytes() {
        byte[] file = new byte[16];
        file[0] = (byte) 0x25;
        file[1] = (byte) 0x50;
        file[2] = (byte) 0x44;
        file[3] = (byte) 0x46;
        return file;
    }

    /** Seeds a DRAFT report with one line carrying a JPEG receipt; returns its id. */
    protected Long seedReportWithReceipt(LocalDate date, String amount,
            String filename) {
        return seedReportWithReceipt(date, amount, filename, jpegBytes());
    }

    /** Seeds a DRAFT report with one line carrying the given receipt bytes. */
    protected Long seedReportWithReceipt(LocalDate date, String amount,
            String filename, byte[] bytes) {
        var type = referenceData.activeExpenseTypes().getFirst();
        var rate = referenceData.activeVatRates().getFirst();
        var line = ExpenseLineDto.of(null, type.id(), type.name(), type.icon(),
                rate.id(), rate.value(), new BigDecimal(amount), null);
        var zero = BigDecimal.ZERO.setScale(2);
        return service.create(new ReportDetailDto(null, date, "seed",
                ReportStatus.DRAFT, 0L, List.of(line), zero, zero, zero),
                Map.of(0, new ReceiptUpload(bytes, filename)));
    }

    /** Seeds a SUBMITTED report whose single line carries a receipt. */
    protected Long seedSubmittedReportWithReceipt(LocalDate date, String amount,
            String filename) {
        var id = seedReportWithReceipt(date, amount, filename);
        service.submit(id, service.findMine(id).version());
        return id;
    }

    /** Seeds a DRAFT report with one domestic trip (11 h → €54.00); returns its id. */
    protected Long seedReportWithTravel(LocalDate date, LocalDateTime departure,
            LocalDateTime returnAt) {
        var zero = BigDecimal.ZERO.setScale(2);
        var travel = TravelDto.domestic(null, departure, returnAt, "Helsinki",
                "Client visit", false, false, false, zero, false, zero);
        return service.create(new ReportDetailDto(null, date, "seed",
                ReportStatus.DRAFT, 0L, List.of(), List.of(travel), zero, zero, zero,
                zero, zero, zero));
    }

    /**
     * Seeds a DRAFT report with one domestic trip carrying the Phase 4.3 km / meal /
     * parking inputs, so tests can drive the kilometre / meal subtotal rows and the
     * VAT-bearing parking line. Returns its id.
     */
    protected Long seedReportWithFullTravel(LocalDate date, LocalDateTime departure,
            LocalDateTime returnAt, BigDecimal kilometres, boolean payMeal,
            BigDecimal parkingFees) {
        var zero = BigDecimal.ZERO.setScale(2);
        var travel = TravelDto.domestic(null, departure, returnAt, "Helsinki",
                "Client visit", false, false, false, kilometres, payMeal, parkingFees);
        return service.create(new ReportDetailDto(null, date, "seed",
                ReportStatus.DRAFT, 0L, List.of(), List.of(travel), zero, zero, zero,
                zero, zero, zero));
    }

    /**
     * Seeds a DRAFT report with one domestic trip that earns a <em>meal allowance</em>
     * instead of a per-diem: the two are mutually exclusive under the Finnish rule
     * (issue #93), so the trip is flagged not eligible. Returns its id.
     */
    protected Long seedReportWithMealTravel(LocalDate date, LocalDateTime departure,
            LocalDateTime returnAt) {
        var zero = BigDecimal.ZERO.setScale(2);
        var travel = TravelDto.domestic(null, departure, returnAt, "Helsinki",
                "Client visit", true, false, false, zero, true, zero);
        return service.create(new ReportDetailDto(null, date, "seed",
                ReportStatus.DRAFT, 0L, List.of(), List.of(travel), zero, zero, zero,
                zero, zero, zero));
    }

    /** Seeds a SUBMITTED report whose single generated line came from a trip. */
    protected Long seedSubmittedReportWithTravel(LocalDate date,
            LocalDateTime departure, LocalDateTime returnAt) {
        var id = seedReportWithTravel(date, departure, returnAt);
        service.submit(id, service.findMine(id).version());
        return id;
    }

    /** The first active expense type / VAT rate, for driving the line editor. */
    protected ExpenseTypeDto firstActiveType() {
        return referenceData.activeExpenseTypes().getFirst();
    }

    protected VatRateDto firstActiveRate() {
        return referenceData.activeVatRates().getFirst();
    }

    /**
     * Seeds a report owned by the seeded admin (a <em>different</em> user),
     * bypassing the owner-scoped service, so tests can prove the current user's
     * views never surface it.
     */
    protected Long seedReportForAdmin(LocalDate date, String info) {
        var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
        return reportRepository.save(new ExpenseReport(admin, date, info)).getId();
    }
}
