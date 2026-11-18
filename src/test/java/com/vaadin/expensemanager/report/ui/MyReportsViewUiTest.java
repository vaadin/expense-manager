package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDetails;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.expensemanager.base.ui.UnifiedVerticalLayout;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified view test (pyramid layer 3, ADR-0012) for {@link MyReportsView}
 * (UC-002, redesigned in issue #162). Runs as the seeded plain user via
 * {@link WithUserDetails}.
 *
 * <p>Covers the acceptance criteria the eye cannot check: owner scoping (only the
 * current user's reports render), newest-report-date-first ordering, the grouping
 * into "Needs your attention" (draft/rejected) vs "Closed" (submitted/approved),
 * the status and month/year period filters narrowing the sections, the empty
 * state, and the "New report" action routing to the detail view. On top of those,
 * the redesign's own contracts: the metric band's figures coming from the service
 * (year included) and rendering as zeroes for a user with nothing, the metric
 * cards being neither focusable nor clickable, per-section counts replacing the
 * page-header count, the sections being collapsible with the count surviving the
 * collapse, and each card's trip rows, created-on footer and rejection meta. Since
 * #163 it also pins the two glyphs on this view to Lucide symbols — the trip row's
 * plane is the one icon in the app the Figma design actually annotates
 * ({@code lucide/plane}, node {@code I88:12941;91:1810}), so a swap back to another
 * set is a design regression and not a matter of taste.
 *
 * <p>Assertions read the rendered text of the view or locate components by their
 * design class name (the cards are plain links, not a Grid).
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class MyReportsViewUiTest extends AbstractReportViewUiTest {

    /** The three metric cards ({@code MetricCard extends VerticalLayout}). */
    private static List<UnifiedVerticalLayout> metricCards(UnifiedPage page) {
        return view(page).find(UnifiedVerticalLayout.class).withClassName("metric-card").all();
    }

    /** The Lucide symbol ids rendered anywhere under the current view. */
    private static List<String> renderedSymbols(UnifiedPage page) {
        return view(page).findComponent(SvgIcon.class).all().stream()
                .map(icon -> icon.element().getProperty("symbol"))
                .toList();
    }

    private static List<String> sectionCounts(UnifiedPage page) {
        return view(page).find(UnifiedSpan.class).withClassName("report-list-section-count").all()
                .stream().map(count -> count.getText().strip()).toList();
    }

    private static UnifiedDetails sections(UnifiedPage page) {
        return view(page).find(UnifiedDetails.class).withClassName("report-list-section");
    }

    @Test
    void emptyStateRendersWhenOwnerHasNoReports(UnifiedPage page) {
        page.navigate(MyReportsView.class);

        assertSays(page, "No expense reports yet");
    }

    @Test
    void theEmptyStateGlyphIsALucideSymbolFromTheSprite(UnifiedPage page) {
        page.navigate(MyReportsView.class);
        assertSays(page, "No expense reports yet");

        // EmptyState took a "vaadin:name" string until #163, so this asserts the
        // widened seam really carries a built icon through to the DOM.
        var icons = view(page).findComponent(SvgIcon.class).all();
        assertThat(icons).isNotEmpty();
        assertThat(icons).allSatisfy(icon ->
                assertThat(icon.element().getProperty("src")).isEqualTo(LucideIcon.SPRITE));
        assertThat(renderedSymbols(page)).contains(LucideIcon.FILE_TEXT.glyph());
    }

    @Test
    void theTripRowDrawsTheDesignsAnnotatedPlaneGlyph(UnifiedPage page) {
        // The plane only renders on a card that actually has a trip.
        seedReportWithTravel(LocalDate.of(2026, 8, 25),
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 27, 19, 0));

        page.navigate(MyReportsView.class);

        assertThat(view(page).findComponent(SvgIcon.class)
                .withProperty("symbol", LucideIcon.PLANE.glyph())).exists();
    }

    @Test
    void listsOwnReportsNewestReportDateFirstWithStatusAndTotal(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 6, 1), "older trip");
        seedReport(LocalDate.of(2026, 7, 1), "newer trip");

        page.navigate(MyReportsView.class);

        // Both reports, their status badge, and the derived €0.00 total render.
        assertSays(page, "older trip", "newer trip", "Draft", "€0.00");
        // Newest report date first: the newer card appears above the older one.
        var text = view(page).element().getText();
        assertThat(text.indexOf("newer trip")).isLessThan(text.indexOf("older trip"));
    }

    @Test
    void statusFilterSplitsDraftFromSubmitted(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "still a draft");
        seedSubmittedReport(LocalDate.of(2026, 7, 2), "50.00");
        page.navigate(MyReportsView.class);
        UnifiedComboBox status = page.find(UnifiedComboBox.class).withLabel("Status");

        // Both sections show before any filter is applied.
        assertSays(page, "Needs your attention", "Closed");

        // Filtering by SUBMITTED leaves only the "Closed" section.
        status.selectItem("Submitted");
        assertSays(page, "Closed");
        assertDoesNotSay(page, "Needs your attention", "still a draft");

        // Filtering by DRAFT leaves only the "Needs your attention" section.
        status.selectItem("Draft");
        assertSays(page, "Needs your attention", "still a draft");
        assertDoesNotSay(page, "Closed");
    }

    @Test
    void monthAndYearFiltersNarrowTheList(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 1, 15), "january");
        seedReport(LocalDate.of(2026, 7, 15), "july");
        seedReport(LocalDate.of(2025, 7, 15), "last july");
        page.navigate(MyReportsView.class);

        page.find(UnifiedComboBox.class).withLabel("Month").selectItem("July");
        // Both Julys remain; January is filtered out.
        assertSays(page, "last july");
        assertDoesNotSay(page, "january");

        page.find(UnifiedComboBox.class).withLabel("Year").selectItem("2026");
        // July + 2026 leaves just the one report — the 2025 July drops out.
        assertSays(page, "july");
        assertDoesNotSay(page, "last july", "january");
    }

    @Test
    void listShowsOnlyTheCurrentUsersReports(UnifiedPage page) {
        // Another user's report seeded directly must not leak into this list.
        seedReportForAdmin(LocalDate.of(2026, 7, 1), "admin's report");
        seedReport(LocalDate.of(2026, 7, 2), "mine");

        page.navigate(MyReportsView.class);

        assertSays(page, "mine");
        assertDoesNotSay(page, "admin's report");
    }

    @Test
    void newReportButtonNavigatesToDetailView(UnifiedPage page) {
        page.navigate(MyReportsView.class);

        page.find(UnifiedButton.class).withText("New report").click();

        assertThat(page).isAt(ReportDetailView.class);
    }

    // ---- Metric band (docs/design/components/metric-card.md) ----

    @Test
    void metricBandShowsZeroesRatherThanBlanksForAUserWithNoReports(UnifiedPage page) {
        page.navigate(MyReportsView.class);

        // The three cards render alongside the empty state, never instead of it.
        assertThat(view(page).find(UnifiedVerticalLayout.class).withClassName("metric-card"))
                .hasCount(3);
        assertSays(page, "No expense reports yet", "Needs you", "€0.00", "In flight",
                "waiting 0 days", "Reimbursed ", "0 approved");
    }

    @Test
    void metricFiguresComeFromTheServiceIncludingTheReimbursedYear(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "a draft");
        seedSubmittedReport(LocalDate.of(2026, 7, 2), "50.00");
        var metrics = service.myMetrics();

        page.navigate(MyReportsView.class);

        // The caption's year is the service's, never LocalDate.now() in the view.
        assertSays(page, "Reimbursed " + metrics.reimbursedYear());
        assertThat(view(page).find(UnifiedSpan.class).withClassName("metric-card-value").all())
                .extracting(value -> value.getText().strip())
                .containsExactly(String.valueOf(metrics.needsYouCount()),
                        String.valueOf(metrics.inFlightCount()),
                        ReportViewSupport.formatEur(metrics.reimbursedTotal()));
    }

    @Test
    void onlyTheRejectedFragmentOfTheSubLineIsCalledOut(UnifiedPage page) {
        seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "missing receipt");
        page.navigate(MyReportsView.class);

        // The alert span holds the fragment alone — the "·" before it stays in the
        // sub-line's own (secondary) colour.
        assertThat(view(page).find(UnifiedSpan.class).withClassName("metric-card-sub-alert"))
                .hasText("1 rejected");
        UnifiedSpan subLine = view(page).find(UnifiedSpan.class).withClassName("metric-card-sub")
                .where(sub -> sub.getText().contains("1 rejected"));
        assertThat(subLine.getText().strip()).startsWith("€").contains(" · ");
    }

    @Test
    void aSubLineWithNothingRejectedCarriesNoAlertFragment(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "a draft");
        page.navigate(MyReportsView.class);
        assertSays(page, "a draft");

        assertThat(view(page).find(UnifiedSpan.class).withClassName("metric-card-sub-alert"))
                .doesNotExist();
    }

    @Test
    void metricCardsAreNeitherFocusableNorClickable(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "a draft");
        page.navigate(MyReportsView.class);
        assertSays(page, "a draft");

        assertThat(metricCards(page)).isNotEmpty().allSatisfy(card -> {
            var parts = new java.util.ArrayList<UnifiedElement>();
            parts.add(card.element());
            parts.addAll(card.element().findDescendants("*"));
            assertThat(parts).allSatisfy(part -> {
                assertThat(part.getTag()).isNotIn("a", "button", "vaadin-button");
                assertThat(part.getAttribute("tabindex")).isNull();
            });
        });
    }

    // ---- Sections (docs/design/components/report-list-section.md) ----

    @Test
    void eachSectionCarriesItsOwnCountAndThePageHeaderCarriesNone(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "draft one");
        seedReport(LocalDate.of(2026, 7, 2), "draft two");
        seedSubmittedReport(LocalDate.of(2026, 7, 3), "50.00");

        page.navigate(MyReportsView.class);

        // Singular below two, and the total that used to sit by the page title is
        // gone — two counts and a third saying "3 reports" would contradict.
        assertSays(page, "2 reports", "1 report");
        assertThat(sectionCounts(page)).containsExactly("2 reports", "1 report");
        assertDoesNotSay(page, "3 reports");
    }

    @Test
    void bothSectionsStartExpandedAndCollapsingOneKeepsItsCountVisible(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "a draft");
        seedSubmittedReport(LocalDate.of(2026, 7, 2), "50.00");
        page.navigate(MyReportsView.class);

        assertThat(sections(page)).hasCount(2);
        // The summaries are components (label + count), not text, so by position.
        List<UnifiedDetails> both = sections(page).all();
        UnifiedDetails first = both.get(0);
        assertThat(first).isOpen();
        assertThat(both.get(1)).isOpen();

        first.close();

        assertThat(first).isClosed();
        // The count is in the summary, so it survives the collapse — it is then
        // the only thing left saying what is inside.
        assertThat(sectionCounts(page)).containsExactly("1 report", "1 report");
    }

    // ---- Report card (docs/design/components/report-card.md) ----

    @Test
    void aCardListsOneRowPerTripWithItsOwnDateRange(UnifiedPage page) {
        seedReportWithTravel(LocalDate.of(2026, 8, 25),
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 27, 19, 0));
        page.navigate(MyReportsView.class);

        assertSays(page, "Helsinki", "25 Aug 2026 – 27 Aug 2026");
    }

    @Test
    void aSingleDayTripRepeatsTheDateRatherThanCollapsingTheRange(UnifiedPage page) {
        seedReportWithTravel(LocalDate.of(2026, 8, 25),
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 25, 19, 0));
        page.navigate(MyReportsView.class);

        assertSays(page, "25 Aug 2026 – 25 Aug 2026");
    }

    @Test
    void aReportWithoutTripsRendersNoTripList(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "no travel here");
        page.navigate(MyReportsView.class);

        assertSays(page, "no travel here");
        assertThat(view(page).find(UnifiedSpan.class).withClassName("report-card-trip-dates"))
                .doesNotExist();
    }

    @Test
    void theFooterDatesTheReportsCreationRatherThanItsReportDate(UnifiedPage page) {
        // A report date deliberately far from today, so "Created on" cannot be
        // passing by coincidence.
        seedReport(LocalDate.of(2020, 1, 2), "an old report date");
        page.navigate(MyReportsView.class);

        assertSays(page, "Created on " + LocalDate.now());
        assertDoesNotSay(page, "Created on 2020-01-02");
    }

    @Test
    void aRejectedCardNamesWhoRejectedItAndWhen(UnifiedPage page) {
        seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "missing receipt");
        page.navigate(MyReportsView.class);

        assertSays(page, "Rejected by Expense Admin on 2026-07-14");
    }

    @Test
    void anUnrejectedCardCarriesNoRejectionMeta(UnifiedPage page) {
        seedSubmittedReport(LocalDate.of(2026, 7, 1), "50.00");
        page.navigate(MyReportsView.class);

        assertSays(page, "Created on");
        assertDoesNotSay(page, "Rejected by");
    }
}
