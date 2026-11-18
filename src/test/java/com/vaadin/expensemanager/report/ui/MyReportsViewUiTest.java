package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.base.ui.MetricCard;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for {@link MyReportsView}
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

    private String renderedText(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // TODO unified: Browserless Escape Hatch: getCurrentView().getElement().getTextRecursively()
        return browserless.getCurrentView().getElement().getTextRecursively();
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root),
                root.getChildren().flatMap(MyReportsViewUiTest::descendants));
    }

    private List<MetricCard> metricCards(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // TODO unified: Browserless Escape Hatch: getCurrentView()
        return descendants((Component) browserless.getCurrentView())
                .filter(MetricCard.class::isInstance)
                .map(MetricCard.class::cast)
                .toList();
    }

    /** The Lucide symbol ids rendered anywhere under the current view. */
    private List<String> renderedSymbols(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        // TODO unified: Browserless Escape Hatch: getCurrentView()
        return descendants((Component) browserless.getCurrentView())
                .filter(SvgIcon.class::isInstance)
                .map(SvgIcon.class::cast)
                .map(SvgIcon::getSymbol)
                .toList();
    }

    private List<String> sectionCounts() {
        return findSpan().withClassName("report-list-section-count").components()
                .stream().map(Span::getText).toList();
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void emptyStateRendersWhenOwnerHasNoReports(UnifiedPage page) {
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("No expense reports yet");
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void theEmptyStateGlyphIsALucideSymbolFromTheSprite(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        page.navigate(MyReportsView.class);

        // EmptyState took a "vaadin:name" string until #163, so this asserts the
        // widened seam really carries a built icon through to the DOM.
        // TODO unified: Browserless Escape Hatch: getCurrentView()
        var icons = descendants((Component) browserless.getCurrentView())
                .filter(SvgIcon.class::isInstance).map(SvgIcon.class::cast).toList();
        assertThat(icons).isNotEmpty();
        assertThat(icons).allSatisfy(
                icon -> assertThat(icon.getSrc()).isEqualTo(LucideIcon.SPRITE));
        assertThat(renderedSymbols(page)).contains(LucideIcon.FILE_TEXT.glyph());
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void theTripRowDrawsTheDesignsAnnotatedPlaneGlyph(UnifiedPage page) {
        // The plane only renders on a card that actually has a trip.
        seedReportWithTravel(LocalDate.of(2026, 8, 25),
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 27, 19, 0));

        page.navigate(MyReportsView.class);

        assertThat(renderedSymbols(page)).contains(LucideIcon.PLANE.glyph());
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void listsOwnReportsNewestReportDateFirstWithStatusAndTotal(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 6, 1), "older trip");
        seedReport(LocalDate.of(2026, 7, 1), "newer trip");

        page.navigate(MyReportsView.class);
        var text = renderedText(page);

        // Both reports, their status badge, and the derived €0.00 total render.
        assertThat(text).contains("older trip").contains("newer trip")
                .contains("Draft").contains("€0.00");
        // Newest report date first: the newer card appears above the older one.
        assertThat(text.indexOf("newer trip")).isLessThan(text.indexOf("older trip"));
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void statusFilterSplitsDraftFromSubmitted(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "still a draft");
        seedSubmittedReport(LocalDate.of(2026, 7, 2), "50.00");
        page.navigate(MyReportsView.class);

        // Both sections show before any filter is applied.
        assertThat(renderedText(page)).contains("Needs your attention").contains("Closed");

        // Filtering by SUBMITTED leaves only the "Closed" section.
        findComboBox(com.vaadin.expensemanager.report.domain.ReportStatus.class)
                .withLabel("Status").selectItem("Submitted");
        assertThat(renderedText(page)).contains("Closed")
                .doesNotContain("Needs your attention")
                .doesNotContain("still a draft");

        // Filtering by DRAFT leaves only the "Needs your attention" section.
        findComboBox(com.vaadin.expensemanager.report.domain.ReportStatus.class)
                .withLabel("Status").selectItem("Draft");
        assertThat(renderedText(page)).contains("Needs your attention")
                .contains("still a draft")
                .doesNotContain("Closed");
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void monthAndYearFiltersNarrowTheList(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 1, 15), "january");
        seedReport(LocalDate.of(2026, 7, 15), "july");
        seedReport(LocalDate.of(2025, 7, 15), "last july");
        page.navigate(MyReportsView.class);

        findComboBox(java.time.Month.class).withLabel("Month").selectItem("July");
        // Both Julys remain; January is filtered out.
        assertThat(renderedText(page)).contains("last july").doesNotContain("january");

        findComboBox(Integer.class).withLabel("Year").selectItem("2026");
        // July + 2026 leaves just the one report — the 2025 July drops out.
        assertThat(renderedText(page)).contains("july")
                .doesNotContain("last july").doesNotContain("january");
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void listShowsOnlyTheCurrentUsersReports(UnifiedPage page) {
        // Another user's report seeded directly must not leak into this list.
        seedReportForAdmin(LocalDate.of(2026, 7, 1), "admin's report");
        seedReport(LocalDate.of(2026, 7, 2), "mine");

        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("mine").doesNotContain("admin's report");
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void newReportButtonNavigatesToDetailView(UnifiedPage page) {
        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
        page.navigate(MyReportsView.class);

        findButton().withText("New report").click();

        // TODO unified: Browserless Escape Hatch: getCurrentView()
        assertThat(browserless.getCurrentView()).isInstanceOf(ReportDetailView.class);
    }

    // ---- Metric band (docs/design/components/metric-card.md) ----

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void metricBandShowsZeroesRatherThanBlanksForAUserWithNoReports(UnifiedPage page) {
        page.navigate(MyReportsView.class);
        var text = renderedText(page);

        // The three cards render alongside the empty state, never instead of it.
        assertThat(metricCards(page)).hasSize(3);
        assertThat(text).contains("No expense reports yet")
                .contains("Needs you").contains("€0.00")
                .contains("In flight").contains("waiting 0 days")
                .contains("Reimbursed ").contains("0 approved");
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void metricFiguresComeFromTheServiceIncludingTheReimbursedYear(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "a draft");
        seedSubmittedReport(LocalDate.of(2026, 7, 2), "50.00");
        var metrics = service.myMetrics();

        page.navigate(MyReportsView.class);
        var text = renderedText(page);

        // The caption's year is the service's, never LocalDate.now() in the view.
        assertThat(text).contains("Reimbursed " + metrics.reimbursedYear());
        assertThat(findSpan().withClassName("metric-card-value").components())
                .extracting(Span::getText)
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
        var alert = findSpan().withClassName("metric-card-sub-alert").component();
        assertThat(alert.getText()).isEqualTo("1 rejected");
        assertThat(alert.getParent()).get()
                .satisfies(parent -> assertThat(parent.getElement()
                        .getTextRecursively()).startsWith("€").contains(" · "));
    }

    @Test
    void aSubLineWithNothingRejectedCarriesNoAlertFragment(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "a draft");
        page.navigate(MyReportsView.class);

        assertThat(findSpan().withClassName("metric-card-sub-alert").exists())
                .isFalse();
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void metricCardsAreNeitherFocusableNorClickable(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "a draft");
        page.navigate(MyReportsView.class);

        assertThat(metricCards(page)).isNotEmpty().allSatisfy(card ->
                assertThat(descendants(card)).allSatisfy(part -> {
                    assertThat(part).isNotInstanceOfAny(RouterLink.class,
                            Anchor.class, Button.class);
                    assertThat(part.getElement().getAttribute("tabindex")).isNull();
                }));
    }

    // ---- Sections (docs/design/components/report-list-section.md) ----

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void eachSectionCarriesItsOwnCountAndThePageHeaderCarriesNone(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "draft one");
        seedReport(LocalDate.of(2026, 7, 2), "draft two");
        seedSubmittedReport(LocalDate.of(2026, 7, 3), "50.00");

        page.navigate(MyReportsView.class);

        // Singular below two, and the total that used to sit by the page title is
        // gone — two counts and a third saying "3 reports" would contradict.
        assertThat(sectionCounts()).containsExactly("2 reports", "1 report");
        assertThat(renderedText(page)).doesNotContain("3 reports");
    }

    @Test
    void bothSectionsStartExpandedAndCollapsingOneKeepsItsCountVisible(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "a draft");
        seedSubmittedReport(LocalDate.of(2026, 7, 2), "50.00");
        page.navigate(MyReportsView.class);

        var sections = findDetails().withClassName("report-list-section").components();
        assertThat(sections).hasSize(2).allSatisfy(
                section -> assertThat(section.isOpened()).isTrue());

        // Locator indices are 1-based.
        findDetails().withClassName("report-list-section").atIndex(1).closeDetails();

        assertThat(sections.get(0).isOpened()).isFalse();
        // The count is in the summary, so it survives the collapse — it is then
        // the only thing left saying what is inside.
        assertThat(sectionCounts()).containsExactly("1 report", "1 report");
    }

    // ---- Report card (docs/design/components/report-card.md) ----

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void aCardListsOneRowPerTripWithItsOwnDateRange(UnifiedPage page) {
        seedReportWithTravel(LocalDate.of(2026, 8, 25),
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 27, 19, 0));
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("Helsinki")
                .contains("25 Aug 2026 – 27 Aug 2026");
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void aSingleDayTripRepeatsTheDateRatherThanCollapsingTheRange(UnifiedPage page) {
        seedReportWithTravel(LocalDate.of(2026, 8, 25),
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 25, 19, 0));
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("25 Aug 2026 – 25 Aug 2026");
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void aReportWithoutTripsRendersNoTripList(UnifiedPage page) {
        seedReport(LocalDate.of(2026, 7, 1), "no travel here");
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("no travel here");
        assertThat(findSpan().withClassName("report-card-trip-dates").exists())
                .isFalse();
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void theFooterDatesTheReportsCreationRatherThanItsReportDate(UnifiedPage page) {
        // A report date deliberately far from today, so "Created on" cannot be
        // passing by coincidence.
        seedReport(LocalDate.of(2020, 1, 2), "an old report date");
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("Created on " + LocalDate.now())
                .doesNotContain("Created on 2020-01-02");
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void aRejectedCardNamesWhoRejectedItAndWhen(UnifiedPage page) {
        seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "missing receipt");
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page))
                .contains("Rejected by Expense Admin on 2026-07-14");
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void anUnrejectedCardCarriesNoRejectionMeta(UnifiedPage page) {
        seedSubmittedReport(LocalDate.of(2026, 7, 1), "50.00");
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("Created on")
                .doesNotContain("Rejected by");
    }
}
