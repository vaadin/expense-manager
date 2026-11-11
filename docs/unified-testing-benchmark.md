# Unified UI tests on three Testing Tools: timing, cost and gaps

The same 205 UI tests (every `*UiTest`, ADR-0027) were run on each Testing Tool the
Unified API supports, one tool after another on the same machine, against the same
commit (`71c4747`, branch `unified-testing`).

```bash
./mvnw test -Dtest='*UiTest' -Dvaadin.testing.tool=browserless   # default
./mvnw test -Dtest='*UiTest' -Dvaadin.testing.tool=playwright
./mvnw test -Dtest='*UiTest' -Dvaadin.testing.tool=selenium      # local only, needs a license
```

Machine: macOS, 11 cores, 36 GB, Java 25, Chrome 157, Postgres 17 in Testcontainers
(reused container). Headless browsers. Times are from the surefire XML reports and
Maven's "Total time"; a single run per tool, so treat differences under ~10 % as noise.

## Summary

| | Browserless | Playwright | Selenium |
|---|--:|--:|--:|
| **Wall clock (Maven total)** | **20 s** | **3 min 58 s** | **5 min 11 s** |
| Relative to Browserless | 1× | 12× | 15× |
| Passed | 205 | 204 | 180 |
| Failed or errored | 0 | 0 | **24** |
| Skipped (Browserless-only test, F-099) | 0 | 1 | 1 |
| Sum of test times | 13.7 s | 231.3 s | 304.8 s |
| Median test | 0.06 s | 0.79 s | 1.26 s |
| 90th percentile | 0.10 s | 2.11 s | 2.05 s |
| Slowest test | 1.09 s (first test, warm-up) | 12.87 s | 9.34 s |
| Fixed cost per test (mean of the 20 fastest) | ~0.03 s | ~0.36 s | ~0.96 s |
| Spring context start (once per run) | 4.6 s | 4.5 s | 4.5 s |

**Takeaways**

1. **Browserless is the developer loop.** 20 s for all 205 tests, a quarter of it Spring
   starting. Nothing to optimise.
2. **Playwright is green and costs about 4 minutes.** About a third of that is the fixed
   per-test setup, and about a fifth is just 9 tests that take over 3 s each. Most of those
   9 have causes that can be fixed (see [Optimisation](#optimisation-opportunities)).
3. **Selenium is slower and not green.** It pays for a new Chrome for every test (+0.6 s
   per test against Playwright), and 24 tests fail. Most failures are in this project's
   wrappers, which were written for Browserless and Playwright only. Four are a bug in the
   alpha Selenium adapter.
4. **Selenium is *faster* than Playwright on 16 tests.** All 16 scan for elements by class
   name. Selenium's CSS lookup doesn't descend into shadow roots, so it checks far fewer
   candidates than Playwright's, which does. One of them takes 1.8 s on Selenium and 7.7 s
   on Playwright.

## Where the time goes

### Browserless: 20 s

| Part | Time | Notes |
|---|--:|---|
| Maven, JVM, compile check | ~2 s | |
| Spring context (once) | 4.6 s | One shared context for all 13 classes. |
| First test (JVM/Vaadin warm-up) | 1.1 s | `AllowanceRatesViewUiTest`, whichever class runs first. |
| The other 204 tests | 12.6 s | 0.06 s median. The view runs on the test thread, no browser, no HTTP. |

**Could it be faster?** Not meaningfully: Spring startup is the largest single part.

### Playwright: 3 min 58 s

| Part | Time | Share | Notes |
|---|--:|--:|---|
| Maven, JVM, Spring context, Chromium launch | ~7 s | 3 % | Chromium starts once per run and is shared (`SharedBrowser`). |
| Fixed per-test setup, 204 × ~0.36 s | ~74 s | 31 % | A new browser context per test, a sign-in through the test-only login endpoint (`@WithUserDetails`), and the first navigation with Vaadin's client bootstrap. |
| The 9 tests over 3 s | 52 s | 22 % | See the table below. |
| Everything else the tests do | ~105 s | 44 % | Clicks, typing, and queries, each waiting for Vaadin to go idle (Auto-Wait) plus one or more browser round trips. |

**Every class-name, attribute or `where(..)` filter costs one browser round trip per
candidate element** (F-087). Only id and test-id filters go into the CSS selector. Two
things multiply that cost:

- **Lazy wrappers re-run their whole lookup on every read** (F-096). For example,
  `grid.row(i).cell(..).find(..)` repeats the grid → row → cell lookup each time it is read.
- **Playwright's CSS descends into shadow roots,** so a tag like `div` also matches every
  `div` inside every component's shadow DOM (F-089).

**The slowest Playwright tests, and why:**

| Test | Playwright | Selenium | Browserless | Cause | Fixable here? |
|---|--:|--:|--:|---|---|
| `ReportDetailViewUiTest#eachSectionIsOneCardHoldingItsLinesAsRows` | 12.87 | ❌ | 0.07 | A page-wide `div` scan by class right after a dialog closes. One candidate has gone by the time its class is read, and that read waits out the full **10 s timeout** before `settled(..)` retries (F-096). | **Yes:** wait until the dialog element is gone before scanning (about −10 s). |
| `AllowanceRatesViewUiTest#everyRowActionSitsBehindANamedEllipsisMenu` | 8.77 | ❌ | 1.09 | Opens and reads 4 ⋮ sub-menus. Each one: click, poll every 50 ms until the overlay renders, read the items, press Escape. | Partly: read only the menus the assertion needs. |
| `ExpenseTypeViewUiTest#everySeededTypeRendersItsGlyph` | 7.73 | 1.83 | 0.05 | `glyphOf(page, row)` re-resolves grid → row → cell → icon for each of ~13 rows, plus 4 extra calls, and it calls `getRowCount()` on every loop iteration. | **Yes:** read the icon column once (about −6 s). |
| `AllowanceRatesViewUiTest#theThreeRatesRenderAsOneCardWithTheDomesticRowCarryingTwoValues` | 5.82 | 3.45 | 0.07 | `rateCard(page).find(UnifiedDiv.class).withClassName(..)` scans every page `div` (including shadow DOM) for each read (F-096). | Partly: resolve the card once. A test id would make it one selector, but ADR-0027 §6 allows a test id only when there is nothing else to find the element by. |
| `UserManagementViewUiTest#guardViolationShowsErrorSummaryAndLeavesStateUnchanged` | 3.80 | 2.28 | 0.09 | `UnifiedContainer.withClassName("error-summary")`. `UnifiedContainer` matches every element (`*`), so it checks every element on the page (F-087). | **Yes:** use the `ErrorSummary` component's own tag. |
| `ReportDetailViewUiTest#clearingIsPerKindSoAnUnaffectedOverrideStands` | 3.43 | 3.72 | 0.19 | A real flow: two editor dialogs, a confirmation, saves. | No; this is the work itself. |
| `ReportDetailViewUiTest#aTripEditThatMovesTwoOverriddenCountsReportsBothInOneConfirm` | 3.30 | 3.67 | 0.16 | Same as above. | No. |
| `AllowanceRatesViewUiTest#editingARateThroughItsMenuPersists` | 3.25 | 2.30 | 0.10 | Menu open/poll and the rate-card scan. | Partly. |
| `ExpenseTypeViewUiTest#theGlyphIsOptionalAndAdminChosen` | 3.15 | 2.56 | 0.06 | `glyphOf` again. | **Yes**, same fix. |

### Selenium: 5 min 11 s

| Part | Time | Share | Notes |
|---|--:|--:|---|
| Maven, JVM, Spring context | ~7 s | 2 % | |
| Fixed per-test setup, 204 × ~0.96 s | ~196 s | 63 % | **A new Chrome and ChromeDriver for every test** (`SeleniumAdapter.open`), then the sign-in and first navigation. Compared on the 180 tests that pass on both, Selenium is +0.59 s per test (median), about 2 min in total. |
| Everything else the tests do | ~109 s | 35 % | About the same as Playwright, except that class-name scans are cheaper (see below). |
| *of which: the 24 failing tests* | *47 s* | | They fail after real work, so they still cost time. |

**Why Selenium beats Playwright on class scans:** Selenium's `findElements(By.cssSelector)`
does not descend into shadow roots, so `div.withClassName(..)` checks only the page's own
`div`s. Playwright also checks every component's internal shadow `div`s. Each check is still
one round trip (`classList.contains` through JavaScript), but there are far fewer of them.

## Optimisation opportunities

Ranked by expected saving on the Playwright run, which is the one CI runs.

| # | Change | Where | Expected saving | Effort |
|---|---|---|--:|---|
| 1 | Run the UI test classes in parallel forks, **each with its own database**. `DatabaseSnapshotExtension` restores the whole database after every test, so forks can't share one Postgres. Today all JVMs share a single reused container. | `pom.xml` surefire `forkCount`; a per-fork container (e.g. turn reuse off when forking) | 238 s → about 80–100 s with 3–4 forks (each fork pays ~7 s of startup) | Medium |
| 2 | `eachSectionIsOneCard…`: wait until the closed dialog's element is gone before scanning | test | −10 s | Small |
| 3 | `glyphOf`: read the icon column in one pass instead of re-resolving per row | test helper | −8 s across 2 tests | Small |
| 4 | Use component tags instead of `UnifiedContainer`/`UnifiedDiv` class scans wherever a component class exists (e.g. `ErrorSummary`) | tests | −5 to −10 s | Small |
| 5 | Upstream: put class, attribute and theme filters into the CSS selector (U-5). This removes the per-candidate round trips across the whole suite. | Unified API | Likely the largest share of the ~105 s spent on interactions | Upstream |
| 6 | Upstream: an option to reuse one browser per run in the Selenium adapter, as Playwright does | Unified API | Selenium −2 min | Upstream |
| 7 | Upstream: retry a candidate that disappeared instead of waiting out the timeout, and cache a resolved element for the length of one assertion (U-5 addendum) | Unified API | Removes the 10 s stalls; speeds up chained wrappers | Upstream |

The fixed per-test cost on Playwright (~0.36 s: new context, sign-in, first navigation) is
the price of test isolation, and the snapshot restore depends on that isolation. Signing in
once per class and reusing the browser context would save time, but it would break the
fresh-session-per-test guarantee, so it is not recommended.

## Failures and missing features

### Selenium: 24 tests fail

None fails on Browserless or Playwright. In **19 of the 24, the fault is in this project's
wrappers** (code paths missing for Selenium, or code that relies on how Playwright behaves).
**4 are an adapter bug**, and **1 needs investigation**.

| # | Count | Error | Cause | Owner | Fix |
|---|--:|---|---|---|---|
| S1 | 9 | `UnifiedUpload.upload has no implementation for this Testing Tool` | `UnifiedUpload` has Browserless and Playwright implementations only. | Project wrapper | Add a Selenium override that calls `sendKeys(path)` on the `<input type=file>` inside `<vaadin-upload>` and waits for the upload response. |
| S2 | 3 | `ElementSnapshot supports the Browserless and Playwright Testing Tools` | `ElementSnapshot` (used by `PageHeaderUiTest`) reads `cssText` through Playwright's `Locator.evaluate`. | Project helper | Add a Selenium branch using `executeScript` through the `WebDriver` escape hatch. |
| S3 | 1 | `UnifiedDatePickerInput.typeText has no implementation for this Testing Tool` | Raw date typing exists for Browserless and Playwright only. | Project wrapper | Add a Selenium override: `sendKeys` the text, then Enter. |
| S4 | 3 | `invalid selector: An invalid or illegal selector was specified` | `UnifiedElements.children` passes `findChildren("xpath=./*")`. That is Playwright's selector syntax; Selenium's `findChildren` treats it as CSS. | Project helper | Use `:scope > *` for Selenium. Better, the portable ordered `children()` asked for in U-7. |
| S5 | 2 | `Sub-menu of root item 0 did not open` | `UnifiedMenuBar` looks for the open `vaadin-menu-bar-list-box` with `findChildren`. On Selenium that is CSS **without shadow piercing**, a third meaning next to the two in F-089. The sub-menu is likely rendered where only a shadow-piercing search reaches it. | Project wrapper; root cause upstream (U-7) | Look the sub-menu up from the page in a Selenium override, or wait for U-7. |
| S6 | 2 | `Expecting actual … could not find the following elements` (form layout order) | `UnifiedFormLayout` reads the layout's ordered children with the same Playwright-only assumptions (S4/S5). A stale element appears in the same trace. | Project wrapper | Same as S4. |
| S7 | 4 | `StaleElementReferenceException … css selector: vaadin-button` | The adapter's text filter (`SeleniumElement.matches` → `getText`) reads an element that Vaadin re-rendered between `findElements` and the filter. The adapter doesn't retry. Affected: rejecting a report, submitting, resubmitting, inserting a trip. | **Selenium adapter (alpha)** | Upstream: treat a stale candidate as "not yet" and query again inside Auto-Wait. New issue, the Selenium counterpart of the F-096 race. |

The 24 tests break down as 9 + 3 + 1 + 3 + 2 + 2 + 4. One test in each of S5 and S6 also
shows a stale-element error in its trace, so S7 may cover more than 4 once S4–S6 are fixed.

### Missing features (all tools)

| Gap | Effect | Reference |
|---|---|---|
| No Core Set wrapper for `MenuBar`, `Upload`, `TextArea`, `BigDecimalField`, `IntegerField`, `DateTimePicker`, `Tabs`, `FormLayout`, or tag-bound scopes | About 18 project wrappers. Each one with per-tool code has to be written once per tool: S1–S3 are what happens when the third tool is missing. | F-097, U-13 |
| `findChildren` has three meanings: direct children (Browserless), all descendants including shadow DOM (Playwright), descendants without shadow DOM (Selenium) | Any wrapper built on it behaves differently on each tool (S4–S6). | F-089, U-7 |
| No visibility rule shared by all tools | "The button is gone" assertions need `.where(isVisible)`. | F-090, U-8 |
| Class, attribute and theme filters evaluated one element at a time in the browser | The main cost on Playwright. | F-087, F-096, U-5 |
| A date-time picker can't be typed into one half at a time on Browserless | 1 test is Browserless-only; it is the 1 skip on both browser tools. | F-099 |
| The Selenium adapter starts a new browser for every test | +0.6 s per test. | This report |
| The Selenium adapter doesn't retry stale elements | 4 failures. | This report (S7) |

## Reproduce

The raw data for this report (surefire XML and logs for each tool) was not committed.
To regenerate it, run the three commands at the top. Each tool's per-test times are in
`target/surefire-reports/TEST-*.xml` (`<testcase time=…>`), and the Spring start time is
the `Started … in N seconds` log line.

## Appendix A — Per class (seconds, from surefire, includes the class's share of setup)

| Class | Tests | Browserless | Playwright | Selenium | Selenium failing |
|---|--:|--:|--:|--:|--:|
| `ReportDetailViewUiTest` | 104 | 7.89 | 129.73 | 167.66 | 18 |
| `AllowanceRatesViewUiTest` | 18 | 7.74 | 36.96 | 44.64 | 2 |
| `ExpenseTypeViewUiTest` | 11 | 0.48 | 20.81 | 15.76 |  |
| `UserManagementViewUiTest` | 10 | 0.53 | 14.41 | 12.67 |  |
| `MyReportsViewUiTest` | 21 | 1.01 | 10.49 | 23.65 |  |
| `ReferenceConfigViewUiTest` | 6 | 0.25 | 7.41 | 7.81 |  |
| `NavigationShellUiTest` | 10 | 0.33 | 4.60 | 10.48 |  |
| `ApprovalQueueViewUiTest` | 8 | 0.42 | 4.24 | 9.54 | 1 |
| `ErrorDialogFlowUiTest` | 3 | 0.12 | 1.80 | 3.19 |  |
| `ApprovalAccessUiTest` | 4 | 0.16 | 1.79 | 4.53 |  |
| `VatRateViewUiTest` | 4 | 0.13 | 1.73 | 3.93 |  |
| `PageHeaderUiTest` | 3 | 0.13 | 1.59 | 3.38 | 3 |
| `DashboardViewUiTest` | 3 | 0.09 | 1.13 | 2.97 |  |
| **Total** | **205** | **19.3** | **236.7** | **310.2** | **24** |

## Appendix B — Per test (seconds), slowest on Playwright first

❌ = failed or errored on that tool. The first test of the run also pays for warming up the JVM and Vaadin (about 1 s on Browserless).

| Test | Browserless | Playwright | Selenium | Playwright ÷ Browserless |
|---|--:|--:|--:|--:|
| `ReportDetailViewUiTest#eachSectionIsOneCardHoldingItsLinesAsRows` | 0.07 | 12.87 | 2.19 ❌ | 192× |
| `AllowanceRatesViewUiTest#everyRowActionSitsBehindANamedEllipsisMenu` | 1.09 | 8.77 | 9.34 ❌ | 8× |
| `ExpenseTypeViewUiTest#everySeededTypeRendersItsGlyph` | 0.04 | 7.73 | 1.83 | 172× |
| `AllowanceRatesViewUiTest#theThreeRatesRenderAsOneCardWithTheDomesticRowCarryingTwoValues` | 0.07 | 5.82 | 3.45 | 79× |
| `UserManagementViewUiTest#guardViolationShowsErrorSummaryAndLeavesStateUnchanged` | 0.09 | 3.80 | 2.28 | 41× |
| `ReportDetailViewUiTest#clearingIsPerKindSoAnUnaffectedOverrideStands` | 0.19 | 3.43 | 3.72 | 18× |
| `ReportDetailViewUiTest#aTripEditThatMovesTwoOverriddenCountsReportsBothInOneConfirm` | 0.16 | 3.30 | 3.67 | 21× |
| `AllowanceRatesViewUiTest#editingARateThroughItsMenuPersists` | 0.10 | 3.25 | 2.30 | 32× |
| `ExpenseTypeViewUiTest#theGlyphIsOptionalAndAdminChosen` | 0.06 | 3.15 | 2.56 | 52× |
| `ReportDetailViewUiTest#suppressingALineCarryingAReceiptConfirmsFirstAndNamesTheFile` | 0.15 | 2.86 | 1.40 ❌ | 19× |
| `ReportDetailViewUiTest#cancellingTheClearingConfirmAbandonsTheTripSaveAndKeepsTheOverride` | 0.13 | 2.76 | 3.13 | 21× |
| `ReportDetailViewUiTest#cancellingTheReceiptWarningLeavesBothTheLineAndItsReceiptAlone` | 0.08 | 2.74 | 1.38 ❌ | 34× |
| `ReportDetailViewUiTest#suppressingALineDropsItsBufferedReceiptSoNoStaleReferenceIsSaved` | 0.09 | 2.52 | 1.39 ❌ | 27× |
| `ReportDetailViewUiTest#aTripEditThatMovesTheCalculatedCountConfirmsAndClearsTheOverride` | 0.12 | 2.39 | 2.93 | 20× |
| `ReportDetailViewUiTest#editingAnOverrideNeverChangesTheCalculationAndNeverPrompts` | 0.10 | 2.28 | 2.59 | 23× |
| `ReportDetailViewUiTest#aTripListsItsGeneratedLinesAndAReceiptCanBeAttachedToOne` | 0.10 | 2.21 | 2.05 ❌ | 23× |
| `ReportDetailViewUiTest#aTimeChangeTheDayCountAbsorbsNeverPromptsAndNeverClears` | 0.14 | 2.20 | 2.69 | 16× |
| `ReportDetailViewUiTest#onlyTheVatBearingGeneratedLineShowsANetVatSplit` | 0.10 | 2.18 | 2.98 | 22× |
| `ReportDetailViewUiTest#editingOnlyThePurposeOrDestinationsNeverPromptsAndNeverClears` | 0.11 | 2.16 | 2.70 | 19× |
| `UserManagementViewUiTest#adminReachesViewWithSeededUsersRendered` | 0.04 | 2.13 | 1.17 | 51× |
| `AllowanceRatesViewUiTest#editingAForeignPerDiemThroughItsGridMenuPersists` | 0.09 | 2.11 | 6.23 ❌ | 24× |
| `ReportDetailViewUiTest#resetToCalculatedRestoresASuppressedLineAtItsStatutoryCount` | 0.13 | 1.99 | 2.59 | 15× |
| `ExpenseTypeViewUiTest#reorderTypeSwapsRowsAndBoundaryButtonsAreDisabled` | 0.05 | 1.95 | 1.22 | 42× |
| `ExpenseTypeViewUiTest#editTypeThroughEditorRenamesAndRepointsDefaultRate` | 0.05 | 1.91 | 1.63 | 38× |
| `UserManagementViewUiTest#adminPromotesUserToAdminAndGridReflectsIt` | 0.07 | 1.89 | 1.29 | 27× |
| `UserManagementViewUiTest#adminRevokesUserAndGridReflectsIt` | 0.05 | 1.86 | 1.25 | 35× |
| `AllowanceRatesViewUiTest#copyYearRefusesAnExistingTargetInTheDialogsErrorSummary` | 0.14 | 1.85 | 1.86 | 14× |
| `ReportDetailViewUiTest#resetToCalculatedRemovesTheOverrideWithoutTouchingTheTrip` | 0.09 | 1.76 | 2.13 | 20× |
| `ReportDetailViewUiTest#aSuppressedLineIsShownReadOnlyOnASubmittedReport` | 0.25 | 1.74 | 2.14 | 7× |
| `ExpenseTypeViewUiTest#addTypeThroughEditorAppendsActiveRow` | 0.05 | 1.71 | 1.87 | 34× |
| `ReportDetailViewUiTest#theTripPreviewShowsCalculatedFiguresAnnotatedWhereAnOverrideIsInForce` | 0.09 | 1.71 | 2.25 | 18× |
| `ReportDetailViewUiTest#anUnsavedImageAttachmentPreviewsOnTheCardBeforeSave` | 0.05 | 1.67 | 1.40 ❌ | 36× |
| `ReportDetailViewUiTest#aSuppressedKindIsAnnotatedInThePreviewThatNoLongerListsIt` | 0.09 | 1.67 | 2.05 | 19× |
| `ReportDetailViewUiTest#submittingPersistsALineAddedButNotSavedFirst` | 0.06 | 1.66 | 1.83 | 27× |
| `AllowanceRatesViewUiTest#searchFiltersTheForeignPerDiemsByCountry` | 0.06 | 1.65 | 2.02 | 27× |
| `ReportDetailViewUiTest#aSubmittedReportShowsAnOverrideReadOnlyWithNoWayToChangeIt` | 0.12 | 1.62 | 2.02 | 14× |
| `ReportDetailViewUiTest#aMealCountChangeLeavesAPerDiemOverrideInPlace` | 0.19 | 1.62 | 2.17 | 8× |
| `ReportDetailViewUiTest#anOverrideSurvivesSavingAndReloadingWithItsReason` | 0.08 | 1.61 | 1.98 | 20× |
| `ReportDetailViewUiTest#insertingATripWithKmMealParkingShowsEachSubtotalAndFoldsParkingIntoNetVat` | 0.11 | 1.61 | 1.92 ❌ | 15× |
| `ReportDetailViewUiTest#editingATripReadsItsFlagsBackIntoTheGroup` | 0.10 | 1.59 | 2.02 | 16× |
| `ReferenceConfigViewUiTest#reorderSwapsRowsAndBoundaryButtonsAreDisabled` | 0.04 | 1.45 | 1.19 | 35× |
| `ReportDetailViewUiTest#editingATripWithFreeLunchRegeneratesAHalvedPerDiem` | 0.08 | 1.44 | 1.92 | 18× |
| `UserManagementViewUiTest#statusFilterNarrowsRowsAndCombinesWithRole` | 0.05 | 1.43 | 1.30 | 29× |
| `ReportDetailViewUiTest#overridingTheFullDayCountRescalesTheLineTheSubtotalAndTheTotal` | 0.08 | 1.43 | 1.79 | 19× |
| `ReportDetailViewUiTest#aRejectedReportCanStillBeCorrectedWithAnOverride` | 0.11 | 1.40 | 1.85 | 13× |
| `ReportDetailViewUiTest#aZeroCountRemovesTheLineWithoutPromptingWhenItCarriesNoReceipt` | 0.09 | 1.39 | 2.02 | 16× |
| `ReportDetailViewUiTest#aMislabeledUploadIsRejectedAndNothingIsBuffered` | 0.06 | 1.38 | 1.44 ❌ | 23× |
| `ReferenceConfigViewUiTest#editThroughEditorChangesTheRow` | 0.04 | 1.38 | 1.32 | 33× |
| `ExpenseTypeViewUiTest#deactivateRetainsRowAsInactiveAndExcludesFromActiveOptions` | 0.05 | 1.35 | 1.14 | 28× |
| `ReportDetailViewUiTest#theMealAllowanceCountIsOverridableToo` | 0.10 | 1.35 | 1.69 | 13× |
| `ReportDetailViewUiTest#aQuantityLinePersistsItsUnitPriceAndTotalsTheProduct` | 0.06 | 1.33 | 1.89 | 23× |
| `ReportDetailViewUiTest#uploadingAReceiptOnAnUnsavedReportPersistsItOnFirstSave` | 0.08 | 1.33 | 1.44 ❌ | 17× |
| `ReportDetailViewUiTest#reCostingATripDropsThePerDiemCardItNoLongerEarns` | 0.09 | 1.30 | 2.04 | 15× |
| `ReferenceConfigViewUiTest#deactivateRetainsRowAsInactive` | 0.04 | 1.30 | 1.18 | 33× |
| `ReportDetailViewUiTest#addingALineViaTheDialogUpdatesLiveTotalsAndPersistsOnSave` | 0.06 | 1.30 | 1.73 | 21× |
| `ReportDetailViewUiTest#choosingHalvedSavesEligibleWithAFreeLunch` | 0.07 | 1.29 | 1.98 | 17× |
| `ReportDetailViewUiTest#choosingNotEligibleSavesNotEligibleWithoutAFreeLunch` | 0.07 | 1.29 | 1.99 | 18× |
| `ReferenceConfigViewUiTest#addThroughEditorAppendsActiveRow` | 0.04 | 1.28 | 1.41 | 28× |
| `ReportDetailViewUiTest#reCostingATripUpdatesItsKilometreCardWithoutLeavingAStaleOne` | 0.17 | 1.27 | 1.89 | 8× |
| `ReportDetailViewUiTest#theDailyAllowanceGroupOpensOnFullByDefaultAndSavesBothFlagsFalse` | 0.07 | 1.24 | 1.88 | 17× |
| `ReportDetailViewUiTest#insertingAForeignTripCostsThePerDiemAgainstTheDestinationCountry` | 0.07 | 1.23 | 1.87 | 17× |
| `ReportDetailViewUiTest#insertingADomesticTripPreviewsGeneratesAndPersistsThePerDiem` | 0.08 | 1.21 | 1.83 | 16× |
| `ReportDetailViewUiTest#travelEditorLaysItsFieldsOutAsTheDesignsTwoSectionGrid` | 0.07 | 1.21 | 1.23 ❌ | 18× |
| `ExpenseTypeViewUiTest#adminReachesViewWithSeededTypesRendered` | 0.04 | 1.18 | 1.10 | 31× |
| `ReportDetailViewUiTest#aBlankReasonIsRejectedInTheDialogsErrorSummaryAndChangesNothing` | 0.06 | 1.16 | 1.83 | 20× |
| `ReportDetailViewUiTest#editingALineLoadsAndUpdatesItsQuantity` | 0.07 | 1.14 | 1.67 | 17× |
| `UserManagementViewUiTest#roleFilterNarrowsRows` | 0.06 | 1.13 | 1.28 | 18× |
| `AllowanceRatesViewUiTest#adminReachesViewWithSeeded2026Rendered` | 0.07 | 1.12 | 1.16 | 17× |
| `AllowanceRatesViewUiTest#copyYearSeedsANamedTargetFromANamedSourceAndSelectsIt` | 0.10 | 1.09 | 1.43 | 11× |
| `ReportDetailViewUiTest#anAttachmentRendersAsAPaperclipChipThatStillOpensThePreview` | 0.06 | 1.07 | 1.42 | 18× |
| `ReferenceConfigViewUiTest#invalidSaveShowsErrorSummaryAndPersistsNothing` | 0.04 | 1.06 | 1.62 | 26× |
| `ReportDetailViewUiTest#aReturnEqualToTheDepartureIsRefusedAndSaysWhy` | 0.07 | 1.05 | 1.68 | 16× |
| `AllowanceRatesViewUiTest#addCountryThroughEditorAppendsRow` | 0.11 | 1.04 | 1.42 | 10× |
| `ReportDetailViewUiTest#thePartialDayCountIsCappedAtOneWithTheReasonInTheErrorSummary` | 0.07 | 1.02 | 1.69 | 16× |
| `ReportDetailViewUiTest#aRowMenusNameFollowsTheTypeTheEditorChanges` | 0.06 | 1.01 | 1.46 | 16× |
| `AllowanceRatesViewUiTest#addYearSeedsFromTheLatestYearAndSelectsIt` | 0.09 | 1.01 | 1.32 | 11× |
| `ReportDetailViewUiTest#anIncompleteTripShowsTheErrorSummaryAndGeneratesNothing` | 0.05 | 0.97 | 1.35 | 20× |
| `UserManagementViewUiTest#searchNarrowsByNameOrEmail` | 0.04 | 0.96 | 1.15 | 23× |
| `ReferenceConfigViewUiTest#gridRendersRowsWithTextStatusAndActions` | 0.04 | 0.93 | 1.09 | 25× |
| `ReportDetailViewUiTest#anUnsavedImageAttachmentPreviewsFromBufferedBytes` | 0.04 | 0.93 | 1.43 ❌ | 21× |
| `ReportDetailViewUiTest#aNonPositiveQuantityIsBlockedWithAnErrorSummary` | 0.05 | 0.91 | 1.41 | 18× |
| `ReportDetailViewUiTest#theStatusHistoryIsOneBoxWithItsSharedSectionHeading` | 0.06 | 0.91 | 1.19 ❌ | 16× |
| `ReportDetailViewUiTest#aTripCannotBeSavedWithNoDestinationCountryChosen` | 0.05 | 0.89 | 1.43 | 19× |
| `ReportDetailViewUiTest#selectingHalvedClearsPayMealAllowance` | 0.05 | 0.88 | 1.20 | 19× |
| `ReportDetailViewUiTest#checkingPayMealAllowanceSelectsNotEligible` | 0.04 | 0.88 | 1.22 | 20× |
| `ReportDetailViewUiTest#selectingFullClearsPayMealAllowance` | 0.04 | 0.88 | 1.22 | 21× |
| `ReportDetailViewUiTest#resubmittingPersistsAnEditThatWasNotSavedFirst` | 0.09 | 0.87 | 1.28 | 9× |
| `ApprovalQueueViewUiTest#rejectingWithAReasonRecordsItAndMovesTheReportToRejected` | 0.06 | 0.86 | 1.46 ❌ | 15× |
| `ReportDetailViewUiTest#submittingPersistsAReportLevelEditThatWasNotSavedFirst` | 0.06 | 0.85 | 1.27 | 14× |
| `ApprovalQueueViewUiTest#rejectingWithAnEmptyCommentShowsTheReasonRequiredErrorAndDoesNotSubmit` | 0.07 | 0.85 | 1.22 | 13× |
| `ErrorDialogFlowUiTest#aDomainRuleLandsInTheSummaryAndOpensNoDialog` | 0.04 | 0.85 | 1.13 | 24× |
| `ReportDetailViewUiTest#kilometreAndParkingLinesOfferNoOverride` | 0.06 | 0.85 | 1.79 | 13× |
| `ReportDetailViewUiTest#lineEditorKeepsSaveEnabledAndBlocksAnEmptyLine` | 0.05 | 0.85 | 1.30 | 16× |
| `ReportDetailViewUiTest#everyRowMenuIsNamedAfterItsRowAndEveryActionCarriesText` | 0.06 | 0.84 | 1.69 | 14× |
| `ReportDetailViewUiTest#resubmitIsNotOfferedOnDraftSubmittedOrApproved` | 0.08 | 0.84 | 1.80 | 10× |
| `ReportDetailViewUiTest#submittingADraftWithALineLocksItReadOnly` | 0.07 | 0.83 | 1.51 ❌ | 13× |
| `ReportDetailViewUiTest#resubmittingARejectedReportSendsItBackToSubmittedAndLocksItReadOnly` | 0.07 | 0.83 | 1.51 ❌ | 12× |
| `ReportDetailViewUiTest#submittingAnEmptyDraftShowsTheReasonAndStaysEnabled` | 0.05 | 0.82 | 1.30 | 18× |
| `ReportDetailViewUiTest#resubmittingAnEmptyRejectedReportShowsTheReasonAndStaysEnabled` | 0.07 | 0.82 | 1.35 | 11× |
| `ReportDetailViewUiTest#deletingADraftRemovesItAndReturnsToTheList` | 0.07 | 0.82 | 1.25 | 12× |
| `ReportDetailViewUiTest#aSavedImageReceiptRendersAnEnlargeablePreview` | 0.06 | 0.81 | 1.26 | 15× |
| `ReportDetailViewUiTest#cancellingTheSubmitConfirmationLeavesItAnEditableDraft` | 0.06 | 0.79 | 1.36 | 14× |
| `ReportDetailViewUiTest#removingATripRemovesItsPerDiemOnSave` | 0.06 | 0.77 | 1.44 | 12× |
| `MyReportsViewUiTest#onlyTheRejectedFragmentOfTheSubLineIsCalledOut` | 0.06 | 0.76 | 1.41 | 14× |
| `ReportDetailViewUiTest#travelEditorTitlesTheEditStateEditTravelInfo` | 0.06 | 0.76 | 1.34 | 12× |
| `ReportDetailViewUiTest#aTripWithoutKmOrMealHidesThoseSubtotalRows` | 0.05 | 0.75 | 1.42 | 15× |
| `ReportDetailViewUiTest#theCountFieldTakesWholeNumbersAtOrAboveTheFloor` | 0.05 | 0.72 | 1.40 | 13× |
| `MyReportsViewUiTest#bothSectionsStartExpandedAndCollapsingOneKeepsItsCountVisible` | 0.06 | 0.71 | 1.30 | 12× |
| `ReportDetailViewUiTest#anExpenseRowDrawsItsTypesOwnGlyphAndNoColourSwatch` | 0.06 | 0.69 | 1.15 | 12× |
| `ReportDetailViewUiTest#aSectionWithNoRowsRendersNoCard` | 0.04 | 0.69 | 1.35 | 16× |
| `AllowanceRatesViewUiTest#theOtherTwoReferenceViewsHostTheSameBarWithTheirOwnTabSelected` | 0.08 | 0.69 | 1.26 | 8× |
| `MyReportsViewUiTest#statusFilterSplitsDraftFromSubmitted` | 0.06 | 0.68 | 1.34 | 12× |
| `ReportDetailViewUiTest#removingALinePersistsOnSave` | 0.07 | 0.67 | 1.25 | 10× |
| `MyReportsViewUiTest#metricFiguresComeFromTheServiceIncludingTheReimbursedYear` | 0.06 | 0.67 | 1.35 | 12× |
| `MyReportsViewUiTest#monthAndYearFiltersNarrowTheList` | 0.04 | 0.66 | 1.25 | 15× |
| `MyReportsViewUiTest#eachSectionCarriesItsOwnCountAndThePageHeaderCarriesNone` | 0.06 | 0.63 | 1.30 | 12× |
| `ReportDetailViewUiTest#lineEditorLaysItsFieldsOutAsTheDesignsTwoColumnGrid` | 0.06 | 0.63 | 1.23 ❌ | 11× |
| `ReportDetailViewUiTest#choosingADepartureConstrainsTheReturnPickerRangeAndViceVersa` | 0.05 | 0.62 | 1.20 | 14× |
| `ReportDetailViewUiTest#lineEditorDefaultsQuantityToOneAndShowsTheLineTotalLive` | 0.07 | 0.61 | 1.25 | 9× |
| `NavigationShellUiTest#theReferenceTablesPillIsAPlainLinkWithNoMenuBehindIt` | 0.04 | 0.60 | 1.19 | 15× |
| `ReportDetailViewUiTest#lineEditorShowsNoReceiptStatusUntilOneIsAttached` | 0.04 | 0.59 | 1.27 ❌ | 13× |
| `ReportDetailViewUiTest#aTypeWithNoGlyphChosenRendersNoneAndStillNamesItself` | 0.05 | 0.59 | 1.20 | 11× |
| `ReportDetailViewUiTest#reportDateCarriesABadInputErrorMessage` | 0.04 | 0.59 | 1.02 ❌ | 14× |
| `ReportDetailViewUiTest#lineEditorUsesTheDesignsFieldTypesAndCopy` | 0.04 | 0.57 | 1.25 | 13× |
| `ExpenseTypeViewUiTest#editorOffersOnlyActiveVatRatesAsDefault` | 0.04 | 0.57 | 1.38 | 13× |
| `ReportDetailViewUiTest#submittingWithNoReportDateBlocksBeforeConfirming` | 0.06 | 0.56 | 1.21 | 9× |
| `PageHeaderUiTest#expenseTypesOpensWithTheSharedHeaderAndACreateButton` | 0.04 | 0.56 | 1.17 ❌ | 13× |
| `NavigationShellUiTest#currentGroupFollowsTheGroupNotTheRoute` | 0.04 | 0.56 | 1.20 | 12× |
| `VatRateViewUiTest#adminReachesViewWithSeededRatesRendered` | 0.04 | 0.55 | 0.98 | 15× |
| `MyReportsViewUiTest#metricCardsAreNeitherFocusableNorClickable` | 0.05 | 0.55 | 1.46 | 11× |
| `ReportDetailViewUiTest#aReadOnlyReportWithNothingInASectionRendersNoSectionAtAll` | 0.06 | 0.55 | 1.30 | 9× |
| `ReportDetailViewUiTest#missingRequiredDateShowsErrorSummaryAndPersistsNothing` | 0.04 | 0.55 | 1.13 | 14× |
| `ReportDetailViewUiTest#aRowIsNotClickableAndTheMenuIsTheOnlyRouteToEditing` | 0.06 | 0.55 | 1.19 | 9× |
| `NavigationShellUiTest#theViewChoosesItsHeaderVariant` | 0.04 | 0.53 | 1.11 | 13× |
| `ReportDetailViewUiTest#editingExistingReportUpdatesItsFields` | 0.05 | 0.53 | 1.12 | 11× |
| `PageHeaderUiTest#vatRatesOpensWithTheSharedHeaderAndACreateButton` | 0.04 | 0.53 | 1.06 ❌ | 14× |
| `AllowanceRatesViewUiTest#userCannotReachRouteByUrl` | 0.04 | 0.53 | 1.09 | 14× |
| `ReportDetailViewUiTest#aTripEarningAPartialDayListsTwoPerDiemCardsUnderOneSubtotal` | 0.06 | 0.52 | 1.22 | 9× |
| `ReportDetailViewUiTest#aSubmittedReportShowsTheTripButHidesEveryTripAction` | 0.06 | 0.52 | 1.30 | 8× |
| `ReportDetailViewUiTest#lineEditorPutsTheUploadGlyphInTheDropLabelNotOnTheButton` | 0.04 | 0.52 | 1.21 ❌ | 12× |
| `ApprovalQueueViewUiTest#adminOpensAReportReadOnlyAndApprovesIt` | 0.06 | 0.52 | 1.52 | 9× |
| `UserManagementViewUiTest#userCannotReachRouteByUrl` | 0.03 | 0.51 | 1.08 | 15× |
| `PageHeaderUiTest#allowanceRatesOpensWithTheSameBlockAndNoHeaderAction` | 0.04 | 0.50 | 1.14 ❌ | 11× |
| `ApprovalAccessUiTest#aNonAdminCannotReachTheQueueRoute` | 0.03 | 0.50 | 1.09 | 17× |
| `NavigationShellUiTest#unknownRouteResolvesToCustomNotFoundView` | 0.03 | 0.49 | 0.99 | 15× |
| `AllowanceRatesViewUiTest#theCurrentRoutesTabIsSelectedAndItsLinkIsAriaCurrent` | 0.06 | 0.49 | 1.16 | 9× |
| `ReportDetailViewUiTest#newReportSaveWithDefaultsPersistsTodayDatedDraftAndRoutesToId` | 0.05 | 0.49 | 1.11 | 9× |
| `ApprovalAccessUiTest#aNonAdminCannotReachTheReviewHistoryRoute` | 0.03 | 0.49 | 1.09 | 18× |
| `AllowanceRatesViewUiTest#theGridIsStripedAndFixedHeightRatherThanShowingEveryRow` | 0.07 | 0.49 | 1.10 | 7× |
| `ExpenseTypeViewUiTest#userCannotReachRouteByUrl` | 0.03 | 0.49 | 1.06 | 18× |
| `VatRateViewUiTest#userCannotReachRouteByUrl` | 0.03 | 0.49 | 1.08 | 17× |
| `AllowanceRatesViewUiTest#theTabBarLinksToAllThreeReferenceRoutes` | 0.06 | 0.49 | 1.13 | 9× |
| `NavigationShellUiTest#landingViewRendersInsideMainLayout` | 0.03 | 0.48 | 0.97 | 17× |
| `ErrorDialogFlowUiTest#anUncaughtActionFailureReachesTheGlobalHandler` | 0.04 | 0.48 | 1.02 | 11× |
| `AllowanceRatesViewUiTest#theToolbarSelectorIsUnlabelledButNamed` | 0.06 | 0.47 | 1.07 | 8× |
| `MyReportsViewUiTest#newReportButtonNavigatesToDetailView` | 0.04 | 0.47 | 1.07 | 11× |
| `AllowanceRatesViewUiTest#bothYearActionsAreTertiary` | 0.06 | 0.47 | 1.10 | 8× |
| `MyReportsViewUiTest#aReportWithoutTripsRendersNoTripList` | 0.05 | 0.47 | 1.07 | 10× |
| `ErrorDialogFlowUiTest#aTechnicalErrorOpensTheGenericDialogWithoutTheCause` | 0.03 | 0.45 | 1.02 | 14× |
| `NavigationShellUiTest#theAvatarMenuHoldsIdentityThemeAndSignOut` | 0.03 | 0.45 | 1.01 | 16× |
| `MyReportsViewUiTest#aSubLineWithNothingRejectedCarriesNoAlertFragment` | 0.04 | 0.44 | 1.01 | 11× |
| `ApprovalQueueViewUiTest#reviewHistoryShowsTerminalReportsAcrossOwnersWithTheDecisionAndReviewLinks` | 0.06 | 0.44 | 1.11 | 7× |
| `ReportDetailViewUiTest#aRejectedReportShowsTheRealReasonRejecterAndDateToTheOwner` | 0.06 | 0.44 | 1.13 | 7× |
| `ReportDetailViewUiTest#aSubmittedReportShowsTheReceiptSummaryButOffersNoUpload` | 0.06 | 0.43 | 1.18 | 7× |
| `MyReportsViewUiTest#aRejectedCardNamesWhoRejectedItAndWhen` | 0.05 | 0.43 | 1.04 | 8× |
| `MyReportsViewUiTest#aCardListsOneRowPerTripWithItsOwnDateRange` | 0.05 | 0.43 | 1.01 | 9× |
| `DashboardViewUiTest#headerShowsIdentityAndLogout` | 0.03 | 0.43 | 1.02 | 14× |
| `NavigationShellUiTest#adminSeesTheThreeDesignedGroups` | 0.03 | 0.43 | 1.06 | 15× |
| `ExpenseTypeViewUiTest#adminReachesThisRouteThroughTheTabBarRatherThanTheShellMenu` | 0.04 | 0.42 | 1.01 | 11× |
| `ApprovalAccessUiTest#ownerSeesTheirApprovedReportReadOnly` | 0.05 | 0.42 | 1.36 | 9× |
| `ReportDetailViewUiTest#aQuantityOneCardShowsNoQuantityBreakdown` | 0.05 | 0.42 | 1.03 | 8× |
| `ReportDetailViewUiTest#theKilometreLineCardShowsTheKmTimesRateBreakdown` | 0.05 | 0.42 | 1.04 | 8× |
| `ReportDetailViewUiTest#aReadOnlyReportRendersNoRowMenusAndNoAddActionsButKeepsItsReceipts` | 0.06 | 0.42 | 1.25 | 7× |
| `ReportDetailViewUiTest#anAlreadySubmittedReportOpensReadOnly` | 0.05 | 0.42 | 1.23 | 8× |
| `ApprovalQueueViewUiTest#anApproverSeesAnOverriddenLineWithItsReasonAndBaselineReadOnly` | 0.05 | 0.42 | 1.20 | 8× |
| `ReportDetailViewUiTest#aRejectedReportOffersResubmitNotSubmit` | 0.07 | 0.42 | 1.14 | 6× |
| `ApprovalQueueViewUiTest#adminSeesSubmittedReportsAcrossOwnersWithReviewLinks` | 0.05 | 0.42 | 1.10 | 9× |
| `ReportDetailViewUiTest#aMultiDayPerDiemCardShowsTheDaysTimesRateBreakdown` | 0.09 | 0.42 | 1.05 | 5× |
| `MyReportsViewUiTest#anUnrejectedCardCarriesNoRejectionMeta` | 0.05 | 0.41 | 1.04 | 8× |
| `MyReportsViewUiTest#listShowsOnlyTheCurrentUsersReports` | 0.06 | 0.41 | 1.05 | 7× |
| `ReportDetailViewUiTest#bothAddActionsReadAddAndAreToldApartByTheirAccessibleNames` | 0.04 | 0.41 | 1.24 | 9× |
| `MyReportsViewUiTest#theTripRowDrawsTheDesignsAnnotatedPlaneGlyph` | 0.05 | 0.41 | 0.99 | 8× |
| `ReportDetailViewUiTest#aSavedPdfReceiptOffersAnOpenAffordance` | 0.05 | 0.41 | 1.02 | 8× |
| `MyReportsViewUiTest#aSingleDayTripRepeatsTheDateRatherThanCollapsingTheRange` | 0.04 | 0.40 | 1.02 | 9× |
| `MyReportsViewUiTest#theEmptyStateGlyphIsALucideSymbolFromTheSprite` | 0.04 | 0.40 | 0.97 | 10× |
| `MyReportsViewUiTest#theFooterDatesTheReportsCreationRatherThanItsReportDate` | 0.04 | 0.39 | 1.01 | 10× |
| `MyReportsViewUiTest#listsOwnReportsNewestReportDateFirstWithStatusAndTotal` | 0.04 | 0.39 | 0.94 | 9× |
| `MyReportsViewUiTest#metricBandShowsZeroesRatherThanBlanksForAUserWithNoReports` | 0.04 | 0.38 | 1.00 | 11× |
| `ReportDetailViewUiTest#newReportDoesNotOfferDelete` | 0.04 | 0.38 | 1.02 | 11× |
| `MyReportsViewUiTest#emptyStateRendersWhenOwnerHasNoReports` | 0.03 | 0.37 | 1.00 | 11× |
| `ApprovalAccessUiTest#aNonAdminCannotEnterReviewMode` | 0.04 | 0.37 | 0.98 | 8× |
| `ApprovalQueueViewUiTest#anEmptyQueueShowsTheEmptyState` | 0.04 | 0.36 | 0.96 | 9× |
| `ApprovalQueueViewUiTest#anEmptyReviewHistoryShowsTheEmptyState` | 0.04 | 0.36 | 0.95 | 10× |
| `AllowanceRatesViewUiTest#aUserWhoCannotReachARouteGetsNoTabForIt` | 0.04 | 0.36 | 0.96 | 10× |
| `UserManagementViewUiTest#adminSeesMenuEntry` | 0.03 | 0.36 | 0.94 | 11× |
| `NavigationShellUiTest#theDrawerShellIsGone` | 0.03 | 0.35 | 0.94 | 13× |
| `NavigationShellUiTest#plainUserSeesOnlyTheGroupTheyCanUse` | 0.03 | 0.35 | 1.01 | 13× |
| `NavigationShellUiTest#theDashboardLeavesEveryGroupIdle` | 0.03 | 0.35 | 0.97 | 13× |
| `DashboardViewUiTest#adminSeesAdminGreetingAndContent` | 0.03 | 0.35 | 0.97 | 13× |
| `DashboardViewUiTest#userSeesUserGreetingAndContent` | 0.03 | 0.34 | 0.98 | 13× |
| `VatRateViewUiTest#userSeesNoMenuEntry` | 0.03 | 0.34 | 0.94 | 12× |
| `ExpenseTypeViewUiTest#userSeesNoTabForARouteTheyCannotReach` | 0.03 | 0.34 | 0.95 | 13× |
| `UserManagementViewUiTest#userSeesNoMenuEntry` | 0.03 | 0.34 | 0.92 | 11× |
| `VatRateViewUiTest#adminSeesMenuEntry` | 0.03 | 0.34 | 0.93 | 12× |
| `ReportDetailViewUiTest#travelPickersCarryIncompleteAndBadInputErrorMessages` | 0.04 | skipped | skipped |  |
