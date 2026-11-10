package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.base.ui.unified.UnifiedDetails;
import com.vaadin.expensemanager.base.ui.unified.UnifiedMetricCard;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedContainer;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0027 proposed) for {@link MyReportsView}
 * (UC-002, redesigned in issue #162). Runs as the seeded plain user via
 * {@link WithUserDetails}, unchanged on Browserless and Playwright.
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
 * <p>Assertions read the view's rendered text or locate elements by their design
 * class name (the cards are plain links, not a Grid). <strong>Section labels are
 * compared ignoring case:</strong> {@code .section-label} is
 * {@code text-transform: uppercase}, and a browser's text is what the user sees
 * ("NEEDS YOUR ATTENTION") while Browserless reads the server-side string.
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class MyReportsViewUiTest extends AbstractReportUiTest {

    private static String renderedText(UnifiedPage page) {
        return page.findComponent(MyReportsView.class).withClassName("my-reports-view")
                .single().element().getText();
    }

    private static List<UnifiedMetricCard> metricCards(UnifiedPage page) {
        return page.find(UnifiedMetricCard.class).withClassName("metric-card").all();
    }

    private static List<String> spanTexts(UnifiedPage page, String className) {
        return page.findComponent(Span.class).withClassName(className).all().stream()
                .map(span -> span.element().getText()).toList();
    }

    /** The Lucide symbol ids rendered anywhere on the page. */
    private static List<String> renderedSymbols(UnifiedPage page) {
        return page.findComponent(SvgIcon.class).all().stream()
                .map(icon -> icon.element().getProperty("symbol"))
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<String> sectionCounts(UnifiedPage page) {
        return spanTexts(page, "report-list-section-count");
    }

    private static List<UnifiedDetails> sections(UnifiedPage page) {
        return page.find(UnifiedDetails.class).withClassName("report-list-section").all();
    }

    @Test
    void emptyStateRendersWhenOwnerHasNoReports(UnifiedPage page) {
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("No expense reports yet");
    }

    @Test
    void theEmptyStateGlyphIsALucideSymbolFromTheSprite(UnifiedPage page) {
        page.navigate(MyReportsView.class);

        // EmptyState took a "vaadin:name" string until #163, so this asserts the
        // widened seam really carries a built icon through to the DOM.
        // In a browser SvgIcon's tag, <vaadin-icon>, also matches the shell's
        // font icons, so only the symbol-bearing (sprite) icons are checked.
        var icons = page.findComponent(SvgIcon.class).all().stream()
                .filter(icon -> icon.element().getProperty("symbol") != null).toList();
        assertThat(icons).isNotEmpty().allSatisfy(icon -> assertThat(
                icon.element().getProperty("src")).isEqualTo(LucideIcon.SPRITE));
        assertThat(renderedSymbols(page)).contains(LucideIcon.FILE_TEXT.glyph());
    }

    @Test
    void theTripRowDrawsTheDesignsAnnotatedPlaneGlyph(UnifiedPage page) {
        // The plane only renders on a card that actually has a trip.
        fixtures.seedReportWithTravel(LocalDate.of(2026, 8, 25),
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 27, 19, 0));

        page.navigate(MyReportsView.class);

        assertThat(renderedSymbols(page)).contains(LucideIcon.PLANE.glyph());
    }

    @Test
    void listsOwnReportsNewestReportDateFirstWithStatusAndTotal(UnifiedPage page) {
        fixtures.seedReport(LocalDate.of(2026, 6, 1), "older trip");
        fixtures.seedReport(LocalDate.of(2026, 7, 1), "newer trip");

        page.navigate(MyReportsView.class);
        var text = renderedText(page);

        // Both reports, their status badge, and the derived €0.00 total render.
        assertThat(text).contains("older trip").contains("newer trip")
                .contains("Draft").contains("€0.00");
        // Newest report date first: the newer card appears above the older one.
        assertThat(text.indexOf("newer trip")).isLessThan(text.indexOf("older trip"));
    }

    @Test
    void statusFilterSplitsDraftFromSubmitted(UnifiedPage page) {
        fixtures.seedReport(LocalDate.of(2026, 7, 1), "still a draft");
        fixtures.seedSubmittedReport(LocalDate.of(2026, 7, 2), "50.00");
        page.navigate(MyReportsView.class);

        // Both sections show before any filter is applied.
        assertThat(renderedText(page)).containsIgnoringCase("Needs your attention")
                .containsIgnoringCase("Closed");

        // Filtering by SUBMITTED leaves only the "Closed" section.
        comboBox(page, "Status").selectItem("Submitted");
        assertThat(renderedText(page)).containsIgnoringCase("Closed")
                .doesNotContainIgnoringCase("Needs your attention")
                .doesNotContain("still a draft");

        // Filtering by DRAFT leaves only the "Needs your attention" section.
        comboBox(page, "Status").selectItem("Draft");
        assertThat(renderedText(page)).containsIgnoringCase("Needs your attention")
                .contains("still a draft")
                .doesNotContainIgnoringCase("Closed");
    }

    @Test
    void monthAndYearFiltersNarrowTheList(UnifiedPage page) {
        fixtures.seedReport(LocalDate.of(2026, 1, 15), "january");
        fixtures.seedReport(LocalDate.of(2026, 7, 15), "july");
        fixtures.seedReport(LocalDate.of(2025, 7, 15), "last july");
        page.navigate(MyReportsView.class);

        comboBox(page, "Month").selectItem("July");
        // Both Julys remain; January is filtered out.
        assertThat(renderedText(page)).contains("last july").doesNotContain("january");

        comboBox(page, "Year").selectItem("2026");
        // July + 2026 leaves just the one report — the 2025 July drops out.
        assertThat(renderedText(page)).contains("july")
                .doesNotContain("last july").doesNotContain("january");
    }

    @Test
    void listShowsOnlyTheCurrentUsersReports(UnifiedPage page) {
        // Another user's report seeded directly must not leak into this list.
        fixtures.seedReportForAdmin(LocalDate.of(2026, 7, 1), "admin's report");
        fixtures.seedReport(LocalDate.of(2026, 7, 2), "mine");

        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("mine").doesNotContain("admin's report");
    }

    @Test
    void newReportButtonNavigatesToDetailView(UnifiedPage page) {
        page.navigate(MyReportsView.class);

        page.find(UnifiedButton.class).withText("New report").single().click();

        assertThat(page).isAt(ReportDetailView.class);
    }

    // ---- Metric band (docs/design/components/metric-card.md) ----

    @Test
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
    void metricFiguresComeFromTheServiceIncludingTheReimbursedYear(UnifiedPage page) {
        fixtures.seedReport(LocalDate.of(2026, 7, 1), "a draft");
        fixtures.seedSubmittedReport(LocalDate.of(2026, 7, 2), "50.00");
        var metrics = service.myMetrics();

        page.navigate(MyReportsView.class);

        // The caption's year is the service's, never LocalDate.now() in the view.
        assertThat(renderedText(page)).contains("Reimbursed " + metrics.reimbursedYear());
        assertThat(spanTexts(page, "metric-card-value"))
                .containsExactly(String.valueOf(metrics.needsYouCount()),
                        String.valueOf(metrics.inFlightCount()),
                        ReportViewSupport.formatEur(metrics.reimbursedTotal()));
    }

    @Test
    void onlyTheRejectedFragmentOfTheSubLineIsCalledOut(UnifiedPage page) {
        fixtures.seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "missing receipt");
        page.navigate(MyReportsView.class);

        // The alert span holds the fragment alone — the "·" before it stays in the
        // sub-line's own (secondary) colour.
        assertThat(spanTexts(page, "metric-card-sub-alert")).containsExactly("1 rejected");
        var subLine = spanTexts(page, "metric-card-sub").stream()
                .filter(text -> text.contains("1 rejected")).toList();
        assertThat(subLine).singleElement()
                .satisfies(text -> assertThat(text).startsWith("€").contains(" · "));
    }

    @Test
    void aSubLineWithNothingRejectedCarriesNoAlertFragment(UnifiedPage page) {
        fixtures.seedReport(LocalDate.of(2026, 7, 1), "a draft");
        page.navigate(MyReportsView.class);

        assertThat(page.findComponent(Span.class).withClassName("metric-card-sub-alert")
                .exists()).isFalse();
    }

    @Test
    void metricCardsAreNeitherFocusableNorClickable(UnifiedPage page) {
        fixtures.seedReport(LocalDate.of(2026, 7, 1), "a draft");
        page.navigate(MyReportsView.class);

        assertThat(metricCards(page)).isNotEmpty().allSatisfy(card -> {
            assertThat(card.find(UnifiedButton.class).exists()).isFalse();
            assertThat(card.findComponent(Anchor.class).exists()).isFalse();
            assertThat(card.findComponent(RouterLink.class).exists()).isFalse();
            assertThat(card.element().getAttribute("tabindex")).isNull();
            // Any descendant: UnifiedContainer matches every element ("*"), where
            // findComponent(Component.class) has no tag to look for in a browser.
            assertThat(card.find(UnifiedContainer.class)
                    .where(part -> part.getAttribute("tabindex") != null).exists())
                    .isFalse();
        });
    }

    // ---- Sections (docs/design/components/report-list-section.md) ----

    @Test
    void eachSectionCarriesItsOwnCountAndThePageHeaderCarriesNone(UnifiedPage page) {
        fixtures.seedReport(LocalDate.of(2026, 7, 1), "draft one");
        fixtures.seedReport(LocalDate.of(2026, 7, 2), "draft two");
        fixtures.seedSubmittedReport(LocalDate.of(2026, 7, 3), "50.00");

        page.navigate(MyReportsView.class);

        // Singular below two, and the total that used to sit by the page title is
        // gone — two counts and a third saying "3 reports" would contradict.
        assertThat(sectionCounts(page)).containsExactly("2 reports", "1 report");
        assertThat(renderedText(page)).doesNotContain("3 reports");
    }

    @Test
    void bothSectionsStartExpandedAndCollapsingOneKeepsItsCountVisible(UnifiedPage page) {
        fixtures.seedReport(LocalDate.of(2026, 7, 1), "a draft");
        fixtures.seedSubmittedReport(LocalDate.of(2026, 7, 2), "50.00");
        page.navigate(MyReportsView.class);

        assertThat(sections(page)).hasSize(2)
                .allSatisfy(section -> assertThat(section.isOpen()).isTrue());

        sections(page).getFirst().toggle();

        page.waitUntil(() -> !sections(page).getFirst().isOpen());
        assertThat(sections(page).get(1).isOpen()).isTrue();
        // The count is in the summary, so it survives the collapse — it is then
        // the only thing left saying what is inside.
        assertThat(sectionCounts(page)).containsExactly("1 report", "1 report");
    }

    // ---- Report card (docs/design/components/report-card.md) ----

    @Test
    void aCardListsOneRowPerTripWithItsOwnDateRange(UnifiedPage page) {
        fixtures.seedReportWithTravel(LocalDate.of(2026, 8, 25),
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 27, 19, 0));
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("Helsinki")
                .contains("25 Aug 2026 – 27 Aug 2026");
    }

    @Test
    void aSingleDayTripRepeatsTheDateRatherThanCollapsingTheRange(UnifiedPage page) {
        fixtures.seedReportWithTravel(LocalDate.of(2026, 8, 25),
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 25, 19, 0));
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("25 Aug 2026 – 25 Aug 2026");
    }

    @Test
    void aReportWithoutTripsRendersNoTripList(UnifiedPage page) {
        fixtures.seedReport(LocalDate.of(2026, 7, 1), "no travel here");
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("no travel here");
        assertThat(page.findComponent(Span.class).withClassName("report-card-trip-dates")
                .exists()).isFalse();
    }

    @Test
    void theFooterDatesTheReportsCreationRatherThanItsReportDate(UnifiedPage page) {
        // A report date deliberately far from today, so "Created on" cannot be
        // passing by coincidence.
        fixtures.seedReport(LocalDate.of(2020, 1, 2), "an old report date");
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("Created on " + LocalDate.now())
                .doesNotContain("Created on 2020-01-02");
    }

    @Test
    void aRejectedCardNamesWhoRejectedItAndWhen(UnifiedPage page) {
        fixtures.seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "missing receipt");
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page))
                .contains("Rejected by Expense Admin on 2026-07-14");
    }

    @Test
    void anUnrejectedCardCarriesNoRejectionMeta(UnifiedPage page) {
        fixtures.seedSubmittedReport(LocalDate.of(2026, 7, 1), "50.00");
        page.navigate(MyReportsView.class);

        assertThat(renderedText(page)).contains("Created on")
                .doesNotContain("Rejected by");
    }

    private static UnifiedComboBox comboBox(UnifiedPage page, String label) {
        return page.find(UnifiedComboBox.class).withLabel(label).single();
    }
}
