package com.vaadin.expensemanager.report.ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.vaadin.expensemanager.base.ui.EmptyState;
import com.vaadin.expensemanager.base.ui.HasHeaderState;
import com.vaadin.expensemanager.base.ui.HeaderState;
import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.base.ui.MetricCard;
import com.vaadin.expensemanager.base.ui.ReportListSection;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.report.service.ReportMetricsDto;
import com.vaadin.expensemanager.report.service.ReportSummaryDto;
import com.vaadin.expensemanager.report.service.TripSummaryDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import jakarta.annotation.security.PermitAll;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatTripDates;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.statusBadge;

/**
 * The report owner's list of their own reports (UC-002, ADR-0017) — the design's
 * "My Expenses": a band of three metrics, the filters, and two collapsible
 * sections of report cards.
 *
 * <p>Owner-scoped by construction: it renders only {@link ExpenseReportService}'s
 * {@code listMine()} and {@code myMetrics()} (the service filters on the current
 * user, ADR-0008), newest report-date first. Reports are grouped into two
 * sections — <strong>Needs your attention</strong> ({@code DRAFT}/{@code
 * REJECTED}, the reports the owner can still act on) and <strong>Closed</strong>
 * ({@code SUBMITTED}/{@code APPROVED}) — each a collapsible group carrying its
 * own count, over a stack of cards showing the note, the report's trips, when it
 * was created, a status badge and the derived total. The card is a
 * {@link RouterLink} (a real, keyboard-operable link, not a click-only row) to
 * the detail view. A <strong>status filter</strong> and a <strong>month + year
 * period filter</strong> on the report date narrow the sections in memory; when
 * the owner has no reports at all, the {@link EmptyState} from {@code base/}
 * renders under three zeroed metric cards — the band never disappears, because a
 * missing card reads as a broken page.
 *
 * <p><strong>Every number comes from the service</strong>, the reimbursed year
 * included (issue #147): a caption computed from {@code LocalDate.now()} here
 * could disagree with the figure under it at a year boundary.
 *
 * <p>{@code @PermitAll} — any authenticated user manages their own reports;
 * there is no admin/user split here. Accessible and usable at ~360px (ADR-0020):
 * the whole view is a single centred column that scrolls, the metric band and
 * the filters wrap rather than overflow, and status is shown as a text badge
 * (never colour alone).
 */
@Route("reports")
@PageTitle("My reports")
@PermitAll
public class MyReportsView extends VerticalLayout implements HasHeaderState {

    private static final Locale LABEL_LOCALE = Locale.ENGLISH;

    private final transient List<ReportSummaryDto> reports;

    private final ComboBox<ReportStatus> statusFilter = new ComboBox<>("Status");
    private final ComboBox<Month> monthFilter = new ComboBox<>("Month");
    private final ComboBox<Integer> yearFilter = new ComboBox<>("Year");

    /** Repopulated on every filter change with the two grouped sections. */
    private final VerticalLayout sections = new VerticalLayout();

    public MyReportsView(ExpenseReportService service) {
        this.reports = service.listMine();
        // A role name for the view's root, so a test (or a style) can scope to it.
        addClassName("my-reports-view");
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        // One centred, mobile-first column (the mockup's single-column shell).
        setAlignItems(FlexComponent.Alignment.CENTER);

        var content = new VerticalLayout(header(), metricsRow(service.myMetrics()));
        content.setPadding(false);
        // The design's rhythm: 40px between the header, the band, the filters and
        // the sections alike.
        content.setSpacing("var(--em-section-gap)");
        content.setWidthFull();
        content.setMaxWidth("46rem");

        if (reports.isEmpty()) {
            content.add(new EmptyState(LucideIcon.FILE_TEXT.create(), "No expense reports yet",
                    "Create your first report to start tracking expenses."));
            add(content);
            return;
        }

        sections.setWidthFull();
        sections.setPadding(false);
        sections.setSpacing("var(--em-section-gap)");
        content.add(filterBar(), sections);
        add(content);
        applyFilters();
    }

    /** Title + the primary "New report" action; the counts live in the sections. */
    private Component header() {
        var title = new H2("My reports");
        title.addClassName("reports-title");

        var newReport = new Button("New report", LucideIcon.PLUS.create(),
                event -> getUI().ifPresent(ui -> ui.navigate(ReportDetailView.class)));
        newReport.addThemeVariants(ButtonVariant.PRIMARY);

        var bar = new HorizontalLayout(title, newReport);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return bar;
    }

    /**
     * The three at-a-glance metrics, straight from {@code myMetrics()} — caption,
     * figure and sub-line all from the same pass over the same reports, so no two
     * of them can disagree. Rendered for everyone, including a user with nothing:
     * they see zeroes rather than a missing band.
     */
    private Component metricsRow(ReportMetricsDto metrics) {
        var needsYou = new MetricCard("Needs you",
                String.valueOf(metrics.needsYouCount()),
                formatEur(metrics.needsYouTotal()),
                // A breakdown of the figure, so it is absent rather than "0
                // rejected" when nothing is — an alert-red zero is a false alarm.
                metrics.rejectedCount() > 0
                        ? metrics.rejectedCount() + " rejected" : null);

        var inFlight = new MetricCard("In flight",
                String.valueOf(metrics.inFlightCount()),
                "waiting " + metrics.inFlightWaitDays()
                        + (metrics.inFlightWaitDays() == 1 ? " day" : " days"));

        // The year is the service's, never LocalDate.now() — see the class javadoc.
        var reimbursed = new MetricCard("Reimbursed " + metrics.reimbursedYear(),
                formatEur(metrics.reimbursedTotal()),
                metrics.approvedCount() + " approved");

        var row = new HorizontalLayout(needsYou, inFlight, reimbursed);
        row.addClassName("metrics-row");
        row.setWidthFull();
        row.setPadding(false);
        row.setSpacing("var(--vaadin-gap-l)");
        return row;
    }

    private HorizontalLayout filterBar() {
        statusFilter.setItems(ReportStatus.values());
        statusFilter.setItemLabelGenerator(ReportViewSupport::statusLabel);
        statusFilter.setClearButtonVisible(true);
        statusFilter.setPlaceholder("All statuses");
        statusFilter.addValueChangeListener(event -> applyFilters());

        monthFilter.setItems(Month.values());
        monthFilter.setItemLabelGenerator(
                month -> month.getDisplayName(TextStyle.FULL, LABEL_LOCALE));
        monthFilter.setClearButtonVisible(true);
        monthFilter.setPlaceholder("All months");
        monthFilter.addValueChangeListener(event -> applyFilters());

        yearFilter.setItems(distinctYears());
        yearFilter.setClearButtonVisible(true);
        yearFilter.setPlaceholder("All years");
        yearFilter.addValueChangeListener(event -> applyFilters());

        var bar = new HorizontalLayout(statusFilter, monthFilter, yearFilter);
        bar.setAlignItems(FlexComponent.Alignment.END);
        // Wrap so the three filters stack rather than overflow at ~360px (ADR-0020).
        bar.addClassName("reports-filter-bar");
        return bar;
    }

    /**
     * Rebuilds the two grouped sections from the current filter selection. Each
     * filter is independent; an unset filter matches all. Empty groups are
     * omitted, and a filter combination that matches nothing shows a hint rather
     * than a blank column.
     */
    private void applyFilters() {
        var status = statusFilter.getValue();
        var month = monthFilter.getValue();
        var year = yearFilter.getValue();
        var filtered = reports.stream()
                .filter(dto -> (status == null || dto.status() == status)
                        && (month == null || dto.reportDate().getMonth() == month)
                        && (year == null || dto.reportDate().getYear() == year))
                .toList();

        var actionable = filtered.stream()
                .filter(dto -> dto.status().isEditable())
                .toList();
        var done = filtered.stream()
                .filter(dto -> !dto.status().isEditable())
                .toList();

        sections.removeAll();
        if (!actionable.isEmpty()) {
            sections.add(section("Needs your attention", actionable, true));
        }
        if (!done.isEmpty()) {
            sections.add(section("Closed", done, false));
        }
        if (filtered.isEmpty()) {
            var none = new Span("No reports match these filters.");
            none.addClassName("no-results");
            sections.add(none);
        }
    }

    /**
     * One collapsible section: chevron, uppercase label and this group's own
     * count over a stack of report cards. The label is passed in sentence case —
     * the uppercasing is CSS, so the accessible name stays a sentence.
     */
    private Component section(String title, List<ReportSummaryDto> rows,
            boolean actionable) {
        var stack = new VerticalLayout();
        stack.addClassName("report-list-section-cards");
        stack.setWidthFull();
        stack.setPadding(false);
        stack.setSpacing("var(--em-card-padding)");
        rows.forEach(dto -> stack.add(reportCard(dto, actionable)));

        return new ReportListSection(title, countLabel(rows.size()), stack);
    }

    /** {@code "1 report"} / {@code "2 reports"} — singular below two. */
    private static String countLabel(int count) {
        return count + (count == 1 ? " report" : " reports");
    }

    /**
     * One clickable report card, rendered as a {@link RouterLink} so it is a
     * real keyboard-operable link to the detail view (ADR-0020), not a click-only
     * row — the whole card is one tab stop and one click target. Actionable
     * (draft/rejected) cards get a raised surface; submitted and closed reports
     * sit flat with a border and a dimmed title. The card's flex column lives in
     * the {@code report-card} CSS class — a RouterLink is not a Vaadin layout.
     */
    private Component reportCard(ReportSummaryDto dto, boolean actionable) {
        var title = new Span(dto.additionalInformation() == null
                || dto.additionalInformation().isBlank()
                ? "Expense report" : dto.additionalInformation());
        title.addClassName("report-card-title");

        var titleRow = new HorizontalLayout(title, statusBadge(dto.status()));
        titleRow.addClassName("report-card-title-row");
        titleRow.setWidthFull();
        titleRow.setAlignItems(FlexComponent.Alignment.CENTER);
        titleRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        titleRow.expand(title);

        var card = new RouterLink();
        card.setRoute(ReportDetailView.class, dto.id());
        card.add(titleRow);
        // A report without travel gets no list at all — no empty box, no stray rule.
        if (!dto.trips().isEmpty()) {
            card.add(trips(dto.trips()));
        }
        card.add(footer(dto));
        card.addClassName("report-card");
        if (actionable) {
            card.addClassName("report-card--actionable");
        }
        return card;
    }

    /**
     * The card's trip list — <strong>one row per trip, not per leg</strong>.
     * {@code destinations} is a single free-text string the user typed, so a row
     * reading "Turku → Helsinki → Copenhagen" is one trip whose arrows are user
     * data; the app neither parses nor composes a route.
     */
    private Component trips(List<TripSummaryDto> trips) {
        var list = new VerticalLayout();
        list.addClassName("report-card-trips");
        list.setWidthFull();
        list.setPadding(false);
        list.setSpacing(false);
        trips.forEach(trip -> list.add(tripRow(trip)));
        return list;
    }

    private Component tripRow(TripSummaryDto trip) {
        // The design's inline role size, beside the row's 16px text (88:12941).
        var plane = LucideIcon.PLANE.create(LucideIcon.SIZE_S);

        var route = new HorizontalLayout(plane, new Span(trip.destinations()));
        route.setPadding(false);
        route.setSpacing("var(--vaadin-gap-s)");
        route.setAlignItems(FlexComponent.Alignment.CENTER);

        var dates = new Span(formatTripDates(trip.departureAt(), trip.returnAt()));
        dates.addClassName("report-card-trip-dates");

        var row = new HorizontalLayout(route, dates);
        row.addClassName("report-card-trip");
        row.setWidthFull();
        row.setPadding(false);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return row;
    }

    /**
     * When the report was created on the left, its total on the right. The date
     * is {@code createdAt}, not the user-entered {@code reportDate} the list
     * sorts and filters on — the design's label says "Created on", and the two
     * are different facts (#147). A rejected report carries a second meta entry
     * naming who rejected it and when.
     */
    private Component footer(ReportSummaryDto dto) {
        var meta = new HorizontalLayout(
                new Span("Created on " + localDate(dto.createdAt())));
        meta.addClassName("report-card-meta");
        meta.setPadding(false);
        meta.setSpacing("var(--vaadin-gap-s)");
        meta.setAlignItems(FlexComponent.Alignment.CENTER);
        if (dto.status() == ReportStatus.REJECTED && dto.rejectedAt() != null) {
            meta.add(new Span("·"), new Span("Rejected by " + dto.rejectedByName()
                    + " on " + localDate(dto.rejectedAt())));
        }

        var total = new Span(formatEur(dto.total()));
        total.addClassName("report-card-total");

        var footer = new HorizontalLayout(meta, total);
        footer.addClassName("report-card-footer");
        footer.setWidthFull();
        footer.setAlignItems(FlexComponent.Alignment.BASELINE);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return footer;
    }

    /** An audit {@link Instant} as the local calendar date the design draws. */
    private static String localDate(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneId.systemDefault()).toString();
    }

    private List<Integer> distinctYears() {
        return reports.stream()
                .map(dto -> dto.reportDate().getYear())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /**
     * The report list is the one screen the design gives the tall header
     * (#146) — it is where a greeting belongs.
     */
    @Override
    public HeaderState headerState() {
        return HeaderState.HOME;
    }

    /**
     * The hero's status line, over the same set the "Needs your attention"
     * section groups: the reports whose status still lets the owner act.
     */
    @Override
    public String headerMessage() {
        var waiting = reports.stream()
                .filter(dto -> dto.status().isEditable())
                .count();
        if (waiting == 0) {
            return "Nothing needs your attention right now";
        }
        return waiting == 1
                ? "1 item needs your attention, get to it soon"
                : waiting + " items need your attention, get to it soon";
    }
}
