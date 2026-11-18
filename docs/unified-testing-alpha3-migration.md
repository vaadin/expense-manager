# Unified Test 0.1.0-alpha3 — migration log

The UI tests were migrated from Browserless Test (`browserless-test-spring`) to Unified Test **0.1.0-alpha3**,
starting fresh from `main`. The migration goes through the library's OpenRewrite recipes (`unified-test-rewrite`)
and its bundled `resolve-migration-markers` skill. This file measures how well each tool does its job. The earlier
hand port to alpha2 is on branch `unified-testing`, for comparison.

Environment: Apple M3 Pro, OpenJDK 25 (2025-09-16 LTS), Maven via `./mvnw`, Postgres through Testcontainers.

## 0. Baseline — Vaadin 25.3.5 / Spring Boot 4.1.1, Browserless Test

Commit: cherry-pick of `3bdce5e` (upgrade only, no test API change).

- `./mvnw verify`: **583 tests, 0 failures, 0 errors, 0 skipped**, 33.6 s wall.
- UI tests: **13 `*UiTest` classes, 205 tests**. 9 files use `SpringBrowserlessTest` directly or through an
  abstract base (`AbstractReportViewUiTest`, `AbstractApprovalViewUiTest`, `AbstractReferenceDataViewUiTest`).

### Browserless runtime — before

Command: `./mvnw -q -o test -Dtest='*UiTest'`, one warm-up run and 3 measured runs. Per-class time comes from
`target/surefire-reports/TEST-*.xml`.

Maven wall clock, measured runs: 22.9 s / 22.2 s / 22.0 s. The warm-up took 21.9 s.

| Class | Tests | Median (s) | Range (s) |
|---|---:|---:|---|
| AllowanceRatesViewUiTest | 18 | 6.948 | 6.570–7.203 |
| AbstractReportViewUiTest (base) | 1 | 1 | 0 | `implements Locators` | 0 | 2 | — | — | `@Transactional` became a `TableSnapshot` of `expense_report`; lines, trips, receipts and status history follow through `on delete cascade`. New helpers: `view(page)`, `assertSays(..)` (retrying `hasTextContaining`) and `assertDoesNotSay(..)` (`page.waitUntil` on the text being gone). The admin-transition seeds (`seedRejectedReport`, `seedApprovedReport`) walk a lazy association, which only worked inside the old test transaction, so they now run in a `TransactionTemplate` (the extra attempt, S10) |
| MyReportsViewUiTest | 5 | 5 | 0 | ~7 | 0 | 2 | 21/21 | **21/21** | `getCurrentView()` text became `assertSays`/`assertDoesNotSay` on the view column. The descendant walks for `MetricCard` and `SvgIcon` became `find(UnifiedVerticalLayout.class).withClassName("metric-card")` and `findComponent(SvgIcon.class)` with the `symbol`/`src` properties. "Neither focusable nor clickable" became a walk over `findDescendants("*")` checking tags and `tabindex`. `findDetails().atIndex(1).closeDetails()` became `all().get(0).close()`: the summaries are components, so `withSummaryText` has no text to match (second attempt) |
| ReportDetailViewUiTest | 88 | 88 | 0 | ~287 | 1 (`UnifiedHorizontalLayout`) | 2 test runs (≈8 compile rounds) | 104/104 | **104/104** | Done in passes. **Scripted:** `find*()` → `page.find(Unified*.class)` (≈300 calls), `withAriaLabel` → `withAccessibleName` (49), `exists()).isFalse()` → `.visible()).doesNotExist()`, view, UI and dialog text chains → `assertSays`/`assertUiSays` helpers, and `page` threaded through 10 helpers. **By hand:** `RowActionMenu` (12 markers) → `UnifiedMenuBar`, which retires `MenuBarTester` (F-082). Layout tests read `colspan` attributes and inline styles; `FormLayout` responsive steps and spacings, which have no DOM reading common to both tools, are checked through `tryUnwrap(FormLayout.class)`. The issue #85 tests now **type** bad and incomplete dates (`typeText`) and assert the summary, where they used to inspect the i18n. The travel-editor child order is read after the totals appear, since a never-visible component is not in a browser's page. `@Transactional` and the `EntityManager.clear()` workaround it needed are gone. First full run: 1 Browserless and 5 Playwright failures. Their causes: `element().click()` does not toggle a `vaadin-checkbox` in Chromium (fixed with `check()`/`uncheck()`); an absence check on text that includes hidden rows; and the date format `typeText` parses differs by tool (S11) |
| ApprovalAccessUiTest | 4 | 0.660 | 0.653–0.665 |
| ApprovalQueueViewUiTest | 14 | 14 | 0 | ~19 | 0 | 2 | 8/8 | **8/8** | `getCurrentView().getElement().getTextRecursively()` became `assertThat(view(page)).hasTextContaining(..)`, which retries. The `RouterLink` "no Wrapper" markers became `findComponent(RouterLink.class).withAttribute("href", "review/"+id)`. `UI.getCurrent()` text in the reject dialog became `dialog.hasTextContaining(..)`. Repository checks moved after a UI assertion, so on a browser they run once the server has answered. Second attempt: `.visible()` on every "the action is gone" check (S9) |
| AllowanceRatesViewUiTest | 16 | 16 | 0 | ~37 | 0 | 2 | 18/18 | **18/18** | `@Transactional` became a `TableSnapshot` of the four rate tables. All 8 `RowActionMenu` markers ("no Wrapper") were resolved with the Core Set `UnifiedMenuBar` (`RowActionMenu extends MenuBar`; its icon-only trigger is named by its `aria-label`), using `withItem("Actions for X")` and `clickItem("Actions for X", "Edit")`. That also works in a grid cell, which retires the `MenuBarTester` workaround for F-082. `ReferenceTabs` uses `UnifiedTabs`, the rate card `UnifiedDiv`/`UnifiedSpan` with class names, and the year selector `withAccessibleName("Year")`. Server-side getters (`getWidth()`, `getHeight()`, `getThemeNames()`, `isAllRowsVisible()`) became inline-style, theme and property reads. Edits now also assert the rendered value before the service read. The extra attempt was my call to a filter that does not exist |
| ApprovalQueueViewUiTest | 8 | 0.400 | 0.385–0.443 |
| DashboardViewUiTest | 3 | 0.641 | 0.546–0.677 |
| ErrorDialogFlowUiTest | 3 | 0.604 | 0.580–0.617 |
| AbstractReferenceDataViewUiTest (base) | 2 | 2 | 0 | ~4 + `implements Locators` | 0 | 1 | — | — | `@Transactional` became a `TableSnapshot` of `vat_rate` and `expense_type` (new helper `base/TableSnapshot`, ~60 lines, generic: deletes added rows and updates changed ones back by id). `rowActionButton(page, GridLocator, rowIndex, colIndex, aria)` became `rowAction(UnifiedGrid, column, rowText, aria)`, so rows are found by their text instead of their position |
| VatRateViewUiTest | 1 | 1 | 0 | 1 | 0 | 1 | 4/4 | **4/4** | Column scan became `assertThat(grid.column("Rate")).hasCellTexts(..)`, a retrying, exact-order check. Denied route handled as in UserManagement |
| ReferenceConfigViewUiTest | 4 | 4 | 0 | ~21 | 0 | 1 | 6/6 | **6/6** | `ErrorSummary` found as `UnifiedDiv.withClassName("error-summary")`. `UI.getCurrent().getElement().getTextRecursively()` became `summary.hasTextContaining(..)` (R3 again, unmarked). Disabled boundary buttons use `assertThat(button).isDisabled()` |
| ExpenseTypeViewUiTest | 7 | 7 | 0 | ~29 | 0 | 1 | 11/11 | **11/11** | The glyph (`SvgIcon.getSymbol()` on the server) became `row.cell("Icon").findComponent(SvgIcon.class)` with `hasProperty("symbol", ..)`. `ReferenceTabs` uses `UnifiedTabs.withClassName("reference-tabs").hasSelectedTab(..)`, so no Wrapper was needed (S5). Rows are found by name. The four "Component Reference" markers with swallowed comments (R2) were resolved and their comments restored by hand. The whole family also passes together with `UserManagementViewUiTest` in one run on both tools, which shows the snapshot restore isolates the tests |
| NavigationShellUiTest | 12 | 12 | 0 | 0 | 0 | 1 | 10/10 | **10/10** | All 6 "no Wrapper" markers (`AppHeader` ×3, `AppLayout`, `SideNav`, `ContextMenu`, `MenuBar`) were resolved without writing a Wrapper. `AppHeader extends Header` uses `UnifiedHeader.withClassName("app-header")`, its pills are the `UnifiedNav`'s `element().children()`, and the absent `AppLayout`/`SideNav` use `findComponent(..).doesNotExist()`. "No `ContextMenu` targets the Reference pill" (a server-side `getTarget()` comparison) became behaviour: the pill is an `<a href="vat-rates">`, and clicking it lands on `VatRateView`. `getCurrentView()` became `assertThat(page).isAt(..)`, except for the error view, which has no route, so it asserts the "Page not found" heading instead. The pill reads (`currentNavLabel`, `navLabels`) are immediate getters; they passed on Playwright straight after a navigation, but they do not retry |
| AbstractApprovalViewUiTest (base) | 0 | — | — | 1 (`implements Locators`) | 0 | 1 | — | — | Already non-transactional with an explicit `@AfterEach` cleanup, so nothing about test data had to change. Added a `view(page)` helper, `UnifiedMain.withClassName("app-shell__content")`, the column `MainLayout` renders a view into, for "the page says X" checks |
| ApprovalAccessUiTest | 5 | 5 | 0 | ~5 | 0 | 2 | 4/4 | **4/4** | Denied routes show the not-found page. `navigate("review/"+id, DashboardView.class)` became `navigate(..)` plus `isAt(DashboardView.class)`. The status `Badge` (a Vaadin 25.3 component with no Core Set Wrapper) uses `findComponent(Badge.class).withText(..)`. The second attempt added `.visible()` (S9). The R2 comment swallowed into a marker was restored |
| ExpenseTypeViewUiTest | 11 | 0.388 | 0.373–0.402 |
| MyReportsViewUiTest | 21 | 0.685 | 0.679–0.764 |
| NavigationShellUiTest | 10 | 0.719 | 0.702–0.727 |
| PageHeaderUiTest | 3 | 0.612 | 0.608–0.620 |
| ErrorDialogFlowUiTest | 5 | 5 | 0 | 1 (R3: `VaadinSession.getCurrent()`) | 0 | 2 | 3/3 | **3/3** | The direct calls on the test thread (`errorHandler.error(..)`, `new EditorDialog(..).open()`) moved into a test-only route, `ErrorFlowTestView`, which alpha3 finds in the test package without configuration. Its failing button hands the exception to the session's handler, so "the handler is installed" is now observed (a dialog opens) instead of inspected. Both "no Wrapper" markers were unnecessary: `ErrorDialog` is a `Dialog` (`UnifiedDialog.withHeaderTitle`), and `ErrorSummary` is a `Div` (`UnifiedDiv.withClassName("error-summary")`). The extra attempt was my own call to a method that does not exist |
| ReferenceConfigViewUiTest | 6 | 0.189 | 0.183–0.212 |
| ReportDetailViewUiTest | 104 | 6.746 | 6.581–6.793 |
| UserManagementViewUiTest | 10 | 1.132 | 1.109–1.139 |
| VatRateViewUiTest | 4 | 0.596 | 0.582–0.602 |
| **Sum of class times** | **205** | **20.060** | 19.969–20.706 |

## 1. Recipe efficiency

_Steps 3–4. Measured on the raw recipe output, before any hand edit._

### 1a. Build recipe — `MigrateBrowserlessBuild` + `AddPlaywrightAdapter`

```
./mvnw -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha3 \
  -Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.MigrateBrowserlessBuild,org.vaadin.addons.unified.rewrite.AddPlaywrightAdapter
```

The run took 22 s and succeeded. OpenRewrite estimates the time it saved at 20 min.

| Measure | Result |
|---|---|
| Files changed | `pom.xml` only, +22 / −0 |
| Added | `unified-test-bom` 0.1.0-alpha3 (import, after `vaadin-bom`); test deps `unified-test-browserless`, `unified-test-playwright`, `unified-test-spring`, all versionless and managed by the BOM |
| Removed | nothing. The project has no direct `browserless-test`, `-junit*`, `-shared` or `vaadin-testbench-unit*` dependency. `browserless-test-spring` is kept, as the recipe documents |
| Spring detection | `AddSpringIntegrationToSpringBootProjects` added `unified-test-spring` correctly |
| Resolved versions | `browserless-test-*` is 25.3.2 everywhere, with no conflict between `vaadin-bom` 25.3.5 and `unified-test-bom`. Playwright 1.63.0 comes through Dramafinder 2.2.0 |
| Hand edits needed | **none** |
| Old tests still green on the new build | 205 / 205 UI tests pass, 0 fail |

Notes:
- The recipe places the new dependencies after `testcontainers-postgresql`, between two Testcontainers entries,
  instead of next to `browserless-test-spring`. This is cosmetic; it was left as the recipe wrote it.
- The ADR-0012 comment above `spring-security-test` still says "browserless view-security tests". It is still
  accurate while the tests run on Browserless.

### 1b. Code recipe — `MigrateBrowserlessTestCode`

Same command with `-Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.MigrateBrowserlessTestCode`. The run
took 22 s and succeeded. OpenRewrite estimates the time it saved at **4 h 35 min**. The raw output is commit
`cec397b`, left unedited.

**Coverage**

| Measure | Result |
|---|---|
| Files rewritten | **16 / 16**: all 13 `*UiTest` classes and the 3 abstract bases |
| Diff | +1204 / −707 lines (`ReportDetailViewUiTest` alone: +557 / −336) |
| Classes left "not migrated" | **0**. No class uses the context API or passes `this` to a helper |
| Test-class shape | `@UnifiedSpringBootTest` (9), test methods take `UnifiedPage page`, Browserless calls go through `BrowserlessEscapeHatch.of(page)` |
| Files still importing `com.vaadin.browserless` | 7, through the Escape Hatches |

**Migration Markers: 170**

| Kind | Count | Breakdown |
|---|---:|---|
| `Browserless Escape Hatch` | 156 | `getCurrentView()` 61, `$(..)` 60, `find..()` 14, `navigate(..)` 13+, misc. **57** of them also say `no Wrapper for X`: `RowActionMenu` 15, `Div` 5, `ReferenceTabs` 5, `HorizontalLayout` 4, `Image` 4, `AppHeader` 3, `ErrorSummary` 3, `MenuBar` 2, `ErrorDialog` 2, `FormLayout` 2, `RouterLink` 2, `Span` 2, `Upload` 2, one each for `Anchor`, `AppLayout`, `ContextMenu`, `PageHeader`, `SideNav`, `VerticalLayout` |
| `Component Reference` | 10 | `test(save)`, `test(chip)`, `rowActionButton(..)` helpers |
| `Browserless-only pattern: @Transactional` | 4 | one per class-level `@Transactional`: `AllowanceRatesViewUiTest`, `UserManagementViewUiTest`, `AbstractReportViewUiTest`, `AbstractReferenceDataViewUiTest` |

Per file: `ReportDetailViewUiTest` 88, `AllowanceRatesViewUiTest` 16, `ApprovalQueueViewUiTest` 14,
`NavigationShellUiTest` 12, `ExpenseTypeViewUiTest` 7, `MyReportsViewUiTest` / `ErrorDialogFlowUiTest` /
`ApprovalAccessUiTest` 5 each, `ReferenceConfigViewUiTest` / `PageHeaderUiTest` 4, `UserManagementViewUiTest` /
`DashboardViewUiTest` 3, `AbstractReferenceDataViewUiTest` 2, `VatRateViewUiTest` / `AbstractReportViewUiTest` 1.

**`@RequiresTestingTool(BROWSERLESS)`**: 136 on methods and 4 on classes. **204 of 205 tests are tagged
Browserless-only.** The remaining one should be too (defect R3). The recipe output makes no test portable yet.
That is by design: it pins whatever it can't express to Browserless and leaves the portability work to the
markers.

**Compiles: no.** There was 1 error, then 2 more after the first fix: `page` not in scope in
`ReportDetailViewUiTest` (defect R1). The minimal fix is commit `ce5bbe4`: 3 helpers get a `UnifiedPage page`
parameter, and 43 call sites pass it (46 lines, one file).

**Runs (with `ce5bbe4`, and before the step 5 config)**

| Tool | Tests | Pass | Fail | Error | Skipped |
|---|---:|---:|---:|---:|---:|
| Browserless | 205 | **205** | 0 | 0 | 0 |
| Playwright | 64 reported | 0 | 0 | **6** | 58 |

Browserless needed **no** `vaadin.testing.browserless.view-packages` setting. In alpha2 it was required for
`DashboardView`. Step 5 confirmed that alpha3 no longer needs it (see 1c).

On Playwright, the 5 classes under the abstract bases fail as a whole (reported as 1 each), and one test errors
in `ErrorDialogFlowUiTest`. The 58 skips are expected.

**Recipe and library defects found**

- **R1, recipe: `page` threaded one call deep only.** The recipe gave the helper `rowMenu(..)` a
  `UnifiedPage page` parameter but did not give it to the helpers that call it: `clickRowAction(String,String)`,
  then `overrideCount(..)` and `editTripReturn(..)`. Result: a compile error. The parameter has to be passed up
  through every helper between the test method and the call that needs it.
- **R2, recipe: a comment above a marked statement is copied into the marker, line breaks removed.** In 6
  places (`ApprovalAccessUiTest` 1, `ExpenseTypeViewUiTest` 3, `ReportDetailViewUiTest` 2), the statement's
  leading comment is repeated inside the `// TODO unified:` line as one flattened line, e.g.
  `// TODO unified: Browserless Escape Hatch:// The review alias is @PermitAll but gated in-view: a USER is forwarded to// the dashboard, ...navigate(..)`.
  *Corrected during step 6:* the original comment stays in place above the marker, so nothing is lost when the
  marker is deleted. It is noise in the marker text only. (Step 4 first reported it as a loss.)
- **R3, recipe: direct `VaadinSession.getCurrent()` not detected.**
  `ErrorDialogFlowUiTest.theGlobalHandlerIsInstalledAsTheSessionErrorHandler` reads server state directly. It
  gets neither a marker nor `@RequiresTestingTool`, so it fails with an NPE on Playwright.
- **L1, library: `@RequiresTestingTool` is not `@Inherited`** (checked with `javap`: only `@Target` and
  `@Retention`). The recipe puts it, next to `@Transactional`, on the abstract bases `AbstractReportViewUiTest`
  and `AbstractReferenceDataViewUiTest`. Spring's `@Transactional` *is* inherited, so `TransactionalCheck` sees it
  on the 5 subclasses (`MyReports`, `ReportDetail`, `ExpenseType`, `ReferenceConfig`, `VatRate`). It then
  **errors** with "@Transactional … doesn't work on PLAYWRIGHT" instead of the class being skipped. The same
  annotations on concrete classes (`AllowanceRatesViewUiTest`, `UserManagementViewUiTest`) skip correctly.
  The fix belongs either in the library (make the annotation `@Inherited`, or look it up in superclasses) or in
  the recipe (put it on the concrete subclasses).

_Wrong rewrites found later, during step 6, are added here._

- **R4, recipe: "no Wrapper for X" for components alpha3 does wrap.** `unified-test-api` 0.1.0-alpha3 ships
  `UnifiedMenuBar`, `UnifiedDiv`, `UnifiedSpan` and `UnifiedUpload` (`@UnifiedComponent(MenuBar.class)`, ...).
  The recipe still marks those components `no Wrapper for MenuBar/Div/Span/Upload: write one (ADR 0003)`.
  That is **11 of the 57** "no Wrapper" markers. Following them literally means writing a Wrapper that already
  exists. The skill does say "look for an existing Wrapper first", but it says to search the project for
  `@UnifiedComponent(`, and the library's own Wrappers are in a jar, so that search misses them.
- **R5, recipe: the Browserless `Locators` mixin is neither migrated nor marked.** The recipe converts the
  `SpringBrowserlessTest` base-class API (`navigate`, `$`, `test`, `getCurrentView`) into Escape Hatch calls plus
  markers. It leaves the fluent locator mixin `com.vaadin.browserless.locator.Locators` untouched:
  `findGrid(..)`, `findTextField()`, `findComboBox(..)`, `findButton()`, `GridLocator`, ... It also leaves direct
  `UI.getCurrent()` / `VaadinSession.getCurrent()` calls (see R3). 4 types still `implements Locators`
  (`AbstractReportViewUiTest`, `AbstractApprovalViewUiTest`, `AbstractReferenceDataViewUiTest`,
  `UserManagementViewUiTest`, and through the bases every subclass), which leaves **about 440 Browserless-only
  lines with no marker**: `ReportDetailViewUiTest` 287, `AllowanceRatesViewUiTest` 37,
  `UserManagementViewUiTest` 31, `ExpenseTypeViewUiTest` 29, `ReferenceConfigViewUiTest` 21,
  `ApprovalQueueViewUiTest` 19, others under 10. (Counted by grepping lines that call a `Locators` method,
  `GridLocator`, `UI.getCurrent` or `VaadinSession.getCurrent`, so it is approximate.)
  **The 170 markers therefore understate the remaining work by roughly 3x.** `UserManagementViewUiTest` has
  3 markers and about 31 unmarked lines. The class-level `@RequiresTestingTool(BROWSERLESS)` keeps all of this
  green, so nothing in a test run reveals it.

### 1c. Test configuration — none needed

The alpha2 port needed `src/test/resources/junit-platform.properties` with
`vaadin.testing.browserless.view-packages=com.vaadin.expensemanager`. alpha2 scanned only the test class's own
package, so it never found `DashboardView` (in `base.ui`) from a test in another package.

alpha3 changes the default. In a Spring test, it scans the packages of the `@SpringBootApplication` plus the
test's own package, for test-only views (`SpringSupport.applicationPackages`, documented on `BrowserlessAdapter`).
The property only overrides that. Evidence: `ApprovalAccessUiTest` (`approval.ui`) navigates to `DashboardView`
(`base.ui`) and passes on Browserless with no configuration, along with the other 204 tests.

So **no file was added**: one migration step fewer than alpha2. The tool is still chosen per run with
`-Dvaadin.testing.tool=playwright|selenium`; Browserless is the default.

## 2. Skill efficiency

_Step 6, `resolve-migration-markers`, followed class by class._

**Scope (user decision):** full portability. Besides the markers, step 6 also converts the unmarked `Locators`
code (R5) and, where feasible, replaces `@Transactional` with committed data plus cleanup. The skill only asks
for the markers, so for each class the table separates **markers** from **unmarked lines converted**.

| Class | Markers | Resolved | Kept | Unmarked lines converted | Wrappers written | Attempts until green | Browserless | Playwright | Notes |
|---|---:|---:|---:|---:|---:|---:|---|---|---|
| DashboardViewUiTest | 3 | 3 | 0 | 0 | 0 | 1 | 3/3 | **3/3** | `@RequiresTestingTool` removed from all 3 tests. `UnifiedMenuBar` used despite the marker (R4). `findComponent(H2.class)` for the greeting, since there is no heading Wrapper |
| UserManagementViewUiTest | 3 | 3 | 0 | ~31 | 0 | 1 | 10/10 | **10/10** | Class-level `@Transactional` and `@RequiresTestingTool` removed, along with `implements Locators`. An `@AfterEach` restores the seeded users. `GridLocator` column scans became `row().withCellText("Email", ..)` with retrying `hasCellText`. The denied route is now "see H2 'Page not found', see no grid" instead of "navigate throws". The two nav-entry tests needed nothing: they read the nav model on the test thread, which works on any tool |
| PageHeaderUiTest | 4 | 4 | 0 | 1 (`implements Locators`) | 2 | 1 | 3/3 | **3/3** | Wrote `UnifiedPageHeader` (`tag = "vaadin-vertical-layout", selector = ".page-header"`) and `UnifiedVerticalLayout`, in `base/ui`. The `PageHeader` component reference became a Wrapper. `getSpacing()` became `getInlineStyle("gap")`, which is portable because `setSpacing(String)` writes the inline `gap`. The `instanceof` child-order check became child class names. The view's section rhythm is found with `where(..)` on a vertical layout whose first children are the tabs and the header |

**Step 6 totals**

| Measure | Result |
|---|---|
| Migration Markers | **170 / 170 resolved, 0 kept** as Escape Hatches |
| Unmarked Browserless lines converted (R5) | ≈ 440 |
| `@RequiresTestingTool` left | **0** (was 140) |
| Tests running on Playwright | **205 / 205** (was 0 passing: 58 skipped, 6 errors) |
| Wrappers written (ADR 0003) | 3: `UnifiedPageHeader`, `UnifiedVerticalLayout`, `UnifiedHorizontalLayout` (`base/ui`). they cover 6 "no Wrapper" markers; the other 51 of 57 were resolved with a Core Set Wrapper plus a filter, or `findComponent(..)` (R4, S5) |
| Test support added | `base/TableSnapshot` (replaces `@Transactional` in 3 bases and 2 classes); `ErrorFlowTestView` (a test-only route) |
| Checks a tool can't make | 3 extra checks through `tryUnwrap(..)` (FormLayout steps and spacings ×2, DateTimePicker min/max wording); they run where the component is at hand and are skipped elsewhere, without skipping the test |
| Tool-aware branches | 1 (`page.hasRealBrowser()` to pick the date format, S11) |
| Files green on the first run | 8 of 16; the rest on the second run (causes: S9, S10, S12, S13, my own API typos) |
| Final run | `./mvnw verify` **583 / 583** (Browserless), `*UiTest` on Playwright **205 / 205**, in one JVM each; the reused Testcontainers database is back at its seed afterwards |

**Cross-class leak caught by the final full run.** Each class passed on its own, but in the whole suite
`ExpenseTypeViewUiTest` saw 15 expense types instead of 9, and a non-UI test
(`ReferenceDataPersistenceIntegrationTest`) failed for the same reason. One report test creates an expense type
through the service ("Glyphless"). Under `@Transactional` that was rolled back; afterwards it was committed, and the
report base snapshotted only `expense_report`. With `testcontainers.reuse.enable=true` the rows survived across Maven
runs: 6 rows from 6 runs. The fix: the report base also snapshots `vat_rate` and `expense_type` (restored after the
reports), plus a one-off delete of the 6 rows from the reused local container.

**Skill and API observations**

- **S1, API gap: `UnifiedMenuBar` has no assertion.** `getItems(..)` and `isItemEnabled(..)` are immediate
  getters, so the account-menu check reads once without retrying, and `user-steps.txt` shows only
  `open Dashboard`. The skill says a check that leaves no `see` line is "a test that does less than asked", but
  there is no MenuBar assertion to write instead.
- **S2, `@Transactional`: the skill's "commit and clean up" route was cheap here.** One `@AfterEach` of 6 lines
  undoes the 3 writes. All 205 UI tests still pass on Browserless after the class stopped rolling back. The skill
  frames keeping the Escape Hatch as the default; for a class with a few known writes, converting was quicker
  than deciding to keep it.
- **S3, behaviour differs by tool behind one call.** The Browserless original asserted that
  `navigate(UserManagementView.class)` *throws* for a USER. `page.navigate(..)` deliberately does not throw (it uses
  `UI.navigate`, per `BrowserlessSession`), so the portable test asserts what the user sees: the app's
  not-found page. The skill has no marker guidance for "the test expected an exception from navigation"; the
  migration had to be read from the adapter source.
- **S5, "no Wrapper for X" on a subclass of a Core Set component (recipe and skill).** The recipe names the
  subclass (`ErrorDialog`, `ErrorSummary`), and the skill says to write a Wrapper. But the Core Set Wrapper of the
  superclass, plus a filter (`UnifiedDialog.withHeaderTitle(..)`, `UnifiedDiv.withClassName(..)`), already finds
  it on every tool. The skill could say: when X extends a Core Set component, try that Wrapper with a filter first.
- **S6, an uncaught listener exception behaves differently by default.** Browserless rethrows it into the
  test, while a browser routes it to the session `ErrorHandler`. alpha3 has a setting for this,
  `vaadin.testing.browserless.route-errors-to-session`, documented only in the `BrowserlessAdapter` Javadoc. It is
  global, so it was not switched on. The test view routes its own exception instead. Neither skill mentions the
  setting.
- **S7, `@Transactional` across a family: one generic helper.** The skill offers "commit test data and clean
  up" only per test. For a base class whose subclasses add, rename, reorder and deactivate rows, a per-test
  cleanup would be long and fragile. A snapshot of the two tables, taken in `@BeforeEach` and restored by id in
  `@AfterEach`, covered all 21 tests and needed no per-test code. The skill could point at this pattern; it is
  what makes the "run everywhere" outcome affordable.
- **S8, the skill's preference for visible names over positions improved these tests.** The Browserless
  originals addressed rows by index ("Restaurant/meals is the fourth seeded row (index 3)"). The Unified forms find
  the row by its text and keep an index only where position is the claim being tested (reorder, "kept its
  position").
- **S9, lookups include hidden components; the old Browserless locators did not.** `findButton().withText("Save").exists()`
  was false for a button hidden with `setVisible(false)`. `page.find(UnifiedButton.class).withText("Save")` matches
  it on every tool (documented on `CommonFilters`: "Add `visible()` where that matters, e.g. the action is
  gone"). A literal conversion of every unmarked `exists()` check therefore flips meaning wherever the view hides
  rather than removes. That is 3 failures in the approval family, on both tools. Neither the recipe (it does not touch
  this code, R5) nor the skill mentions it. A rule worth adding: an absence check on something the view toggles
  needs `.visible()`.
- **S10, removing `@Transactional` exposes lazy loading in test fixtures.** Seeds that load an aggregate and
  call a domain method on it (`reportRepository.findById(id)`, then `report.reject(..)`) touched a lazy
  collection that the old class-level test transaction kept open. Without it they fail with
  `LazyInitializationException` on *both* tools. The fix is a `TransactionTemplate` around the seed. The skill's
  "commit test data and clean up" step does not warn that fixture code may depend on the open session.
- **S11, `UnifiedDateTimePicker.typeText(date, time)`: the date format differs by tool.** Browserless parses
  ISO text only, while a browser parses the field's locale format (en-US `7/1/2026` by default). This is documented
  in the Javadoc. A test of "incomplete input" (a valid date, no time) therefore has to branch on
  `page.hasRealBrowser()`, one of only two tool-aware lines in the suite. A locale-neutral way to type a valid date
  would remove it.
- **S12, `element().click()` on a `vaadin-checkbox` does nothing in Chromium.** On Browserless it toggles. The
  same call passes on one tool and silently does nothing on the other, which surfaced as 4 Playwright failures far
  from the click. `UnifiedCheckbox` has no `click()` of its own, only `check()`/`uncheck()`/`setChecked()`.
  Converting the old `findCheckbox()...click()` mechanically to `element().click()` was the trap; the skill
  could say "a checkbox is set, never clicked".
- **S14, replacing `@Transactional` with a cleanup has to cover every table the class writes, including through
  a service.** A per-class run cannot show a leak (the class passes), and with Testcontainers reuse the leak
  outlives the run and breaks *other* classes, including non-UI integration tests that are still
  `@Transactional`. Only a full-suite run on both tools caught it. Worth a line in the skill's
  `@Transactional` guidance: list what the tests write, service calls included, and run the whole suite.
- **S13, text includes hidden content on both tools.** `element().getText()` reads hidden children (Browserless:
  the component tree; browser: `textContent`). An absence check written as "the dialog text does not contain X"
  fails when the view hides X instead of removing it. Use a lookup with `.visible()` for absence, like S9.

## 3. Browserless runtime — after

_Step 7._ Same machine, JDK, command (`./mvnw -q -o test -Dtest='*UiTest'`, Browserless being the default)
and protocol as the baseline: one warm-up run and 3 measured runs. The same Vaadin 25.3.5 / Spring Boot 4.1.1;
only the test API differs.

Maven wall clock, measured runs: 34.0 s / 34.2 s / 34.3 s. The warm-up took 36.8 s. **Before: 22.0–22.9 s, so
+12 s, +54 %.**

| Class | Tests | Before median (s) | After median (s) | After range (s) | Δ |
|---|---:|---:|---:|---|---:|
| AllowanceRatesViewUiTest | 18 | 6.948 | 8.519 | 8.238–8.678 | +23 % |
| ApprovalAccessUiTest | 4 | 0.660 | 1.781 | 1.778–1.794 | +170 % |
| ApprovalQueueViewUiTest | 8 | 0.400 | 0.364 | 0.358–0.372 | −9 % |
| DashboardViewUiTest | 3 | 0.641 | 1.662 | 1.626–1.710 | +159 % |
| ErrorDialogFlowUiTest | 3 | 0.604 | 1.710 | 1.615–1.742 | +183 % |
| ExpenseTypeViewUiTest | 11 | 0.388 | 0.538 | 0.524–0.546 | +39 % |
| MyReportsViewUiTest | 21 | 0.685 | 0.817 | 0.817–1.035 | +19 % |
| NavigationShellUiTest | 10 | 0.719 | 1.809 | 1.777–1.824 | +152 % |
| PageHeaderUiTest | 3 | 0.612 | 1.721 | 1.696–1.917 | +181 % |
| ReferenceConfigViewUiTest | 6 | 0.189 | 0.259 | 0.240–0.267 | +37 % |
| ReportDetailViewUiTest | 104 | 6.746 | 8.643 | 8.615–8.817 | +28 % |
| UserManagementViewUiTest | 10 | 1.132 | 2.256 | 2.183–2.555 | +99 % |
| VatRateViewUiTest | 4 | 0.596 | 1.722 | 1.715–1.879 | +189 % |
| **Sum of class times** | **205** | **20.060** | **32.079** | 31.897–32.143 | **+60 %** |

Spread between runs stays small (≈ 1 %), so the difference is not noise. **Every class except
`ApprovalQueueViewUiTest` is above the 10 % flag.**

### Where the time goes

The table conflates two things. The first test class on each Spring context pays for starting that context, and
surefire counts it in that class's time. Both runs start the same **9** contexts (one per distinct test
configuration), so the split below uses each run's own `Started <Class> in N seconds` log lines (medians of the
3 runs):

| | Before (s) | After (s) | Δ |
|---|---:|---:|---:|
| Spring context startup (9 contexts) | 7.06 | 17.51 | **+10.45 (+148 %)** |
| Test bodies (class time − startup) | 13.26 | 14.29 | **+1.03 (+7.8 %)** |
| Total | 20.32 | 31.80 | +11.48 |

**1. Context startup: 91 % of the regression.** `@UnifiedSpringBootTest` is meta-annotated
`@SpringBootTest(webEnvironment = RANDOM_PORT)`, unconditionally. Every context therefore starts an embedded
Tomcat and initialises the Vaadin servlet even when the run is Browserless, which needs neither. The logs show it:
`Tomcat started on port` appears 0 times before and 9 times after. Each context goes from ≈ 0.45 s to ≈ 1.55 s.
`browserless-test-spring` used the default mock web environment. This is a fixed cost per *context*, not per test:
it hits most the small classes that start their own context (Dashboard, PageHeader, ErrorDialogFlow,
NavigationShell, VatRate, ApprovalAccess: +1.0–1.1 s each), and not at all the classes that reuse their base's
context (ApprovalQueue, ExpenseType, ReferenceConfig, MyReports). Ruled out on the way: route discovery. Setting
one shared `vaadin.testing.browserless.view-packages=com.vaadin.expensemanager`, so every class scans the same
package set, changed nothing (31.2 s).

**2. Test bodies: +7.8 %, concentrated in the classes that replaced `@Transactional`.** `ReportDetailViewUiTest`
+0.83 s over 104 tests (≈ 8 ms per test), `ExpenseTypeViewUiTest` +0.15 s, `ReferenceConfigViewUiTest`
+0.07 s, `MyReportsViewUiTest` +0.13 s. These are the classes that snapshot and restore tables around every test
(`TableSnapshot`, a few `select *`/`update` round trips) where they used to roll back. Classes without it are flat
or faster (`AllowanceRatesViewUiTest` body 3.87 → 3.61 s; `ApprovalQueueViewUiTest` 0.40 → 0.36 s). So the
Unified API's own per-call overhead on Browserless is within noise. What costs is the test-data strategy that
running on a real browser forces.

**Finding L2 (library):** the web environment should follow the Testing Tool. A Browserless run needs no server;
a mock environment there would remove ≈ 10 s of the 11.5 s regression on this suite (9 contexts). As it stands,
`@UnifiedSpringBootTest` offers no way to opt out: it aliases only `properties`, `classes` and `args`, not
`webEnvironment`.

### 3b. After the follow-up: one shared UI-test base

Every UI test now extends `base/ui/AbstractUiTest`, which declares `@UnifiedSpringBootTest`,
`@ActiveProfiles("test")` and the one `@ServiceConnection` Postgres container. The container field had been
declared in each of 9 root classes, and Spring's context cache treats each declaration as a different
configuration. Now there is **1 context and 1 Tomcat start for the whole UI suite** (was 9 of each). Same
protocol as above.

| Class | Tests | Before migration (s) | After, 9 contexts (s) | **After, 1 context (s)** | Range (s) |
|---|---:|---:|---:|---:|---|
| AllowanceRatesViewUiTest ¹ | 18 | 6.948 | 8.519 | **8.068** | 7.987–8.411 |
| ApprovalAccessUiTest | 4 | 0.660 | 1.781 | **0.155** | 0.147–0.157 |
| ApprovalQueueViewUiTest | 8 | 0.400 | 0.364 | **0.382** | 0.329–0.390 |
| DashboardViewUiTest | 3 | 0.641 | 1.662 | **0.051** | 0.051–0.054 |
| ErrorDialogFlowUiTest | 3 | 0.604 | 1.710 | **0.128** | 0.118–0.152 |
| ExpenseTypeViewUiTest | 11 | 0.388 | 0.538 | **0.528** | 0.517–0.542 |
| MyReportsViewUiTest | 21 | 0.685 | 0.817 | **0.830** | 0.807–0.844 |
| NavigationShellUiTest | 10 | 0.719 | 1.809 | **0.215** | 0.213–0.220 |
| PageHeaderUiTest | 3 | 0.612 | 1.721 | **0.119** | 0.118–0.124 |
| ReferenceConfigViewUiTest | 6 | 0.189 | 0.259 | **0.241** | 0.219–0.244 |
| ReportDetailViewUiTest | 104 | 6.746 | 8.643 | **6.904** | 6.783–7.331 |
| UserManagementViewUiTest | 10 | 1.132 | 2.256 | **0.478** | 0.477–0.511 |
| VatRateViewUiTest | 4 | 0.596 | 1.722 | **0.147** | 0.145–0.170 |
| **Sum of class times** | **205** | **20.060** | **32.079** | **18.553** | 18.068–18.686 |
| **Maven wall clock** (measured runs) | | 22.0–22.9 | 34.0–34.3 | **20.0–20.6** | |

¹ The first class to run pays for the single context start (≈ 5.0 s here, Tomcat included).

**Net result: the migrated suite is now 7.5 % faster on Browserless than the original Browserless Test
suite** (18.6 s vs 20.1 s class time; 20.5 s vs 22.2 s wall). Against the 9-context state it is −42 %. The
original suite also started 9 contexts, but without a server each start cost only ≈ 0.45 s. One context
with a server (≈ 5 s, all fixed costs included) beats nine without. The per-test cost of `TableSnapshot`
(≈ +8 ms per `ReportDetailViewUiTest` test) remains and is the only thing left above the baseline per
class.

Verification on this commit: `./mvnw verify` 583 / 583, and `*UiTest` on Playwright 205 / 205.

For scale, on Playwright `ReportDetailViewUiTest` alone took ≈ 118 s (single run, step 6) against
8.6 s here. The Playwright suite was not benchmarked with this protocol.

## 4. Compared with the alpha2 hand port (branch `unified-testing`)

The alpha2 port was done by hand, before a recipe existed (`docs/unified-testing-benchmark.md` and
`docs/adr/0027-unified-ui-tests.md` on that branch). Same app, same 205 tests, same Vaadin 25.3.5.

| | alpha2, by hand | alpha3, recipe + skill |
|---|---|---|
| Test source changed (`src/test`, from the 25.3.5 upgrade) | 30 files, +3675 / −2392 | 21 files, +2430 / −2195 |
| Project Wrappers | 9 (`base/ui/unified/`: MenuBar, Upload, FormLayout, layouts, MetricCard, ErrorHandlingButton, …) | 3 (`UnifiedPageHeader`, `UnifiedVerticalLayout`, `UnifiedHorizontalLayout`) |
| Tests restricted to one tool | 1 (date-time picker messages, Browserless only) | **0** (`typeText` for the input, `tryUnwrap` for the extra wording check) |
| Configuration needed | `junit-platform.properties` with `view-packages` | none |
| Production code touched | `my-reports-view` root class added | none |
| Data isolation | `DatabaseSnapshotExtension`: whole database copied to a schema, restored after every test | `TableSnapshot`: only the tables a test family writes, restored by id |
| Spring contexts in a Browserless run | 1 (one shared `AbstractUiTest` base) | 9 (classes kept their own setup) |
| Browserless wall clock, `*UiTest` | 27 s (single run) | 34 s (median of 3) |
| Playwright | 204 pass, 1 skipped | 205 pass |

**What alpha3 fixed from alpha2's draft upstream list** (observed in this run): `@Transactional` is now
caught loudly by `TransactionalCheck` instead of differing silently (U-1); routes outside the test's package
are found without configuration (U-2); hidden components count the same way on every tool and `visible()` is
documented (U-8); `withAccessibleName` matches a field's `setAriaLabel` (U-10, the year selector); listener
exceptions can be routed to the session `ErrorHandler` (U-12, setting only); the Core Set now covers
`MenuBar` with icon-only, `aria-label`-named items and enabled state (#108), `Upload` on Playwright (#122),
`Tabs` named by a wrapped `RouterLink` (#107), `Div`, `Span`, `Nav`, `Header`, `Main`, … (U-13). That is
why 6 of alpha2's 9 project Wrappers were not needed.

**What the recipe and skill added over doing it by hand:** the mechanical half (≈ 400 lines rewritten into
Escape Hatch form, `@Transactional` and session use flagged, a marker per decision), plus a checklist that
was accurate for every marker kind it names. **What they did not:** the `Locators` mixin, about 3× the
marked work (F-083), and the per-tool traps that cost the debugging rounds (F-087, F-088). In practice the
hand-written part of this migration was roughly the size of the alpha2 port's, but it started from a
compiling-in-one-fix, all-green-on-Browserless baseline and ended with fewer Wrappers and no tool-specific
tests.

The one place alpha2 did better was Browserless speed: its single shared base class gave one Spring
context (27 s against 34 s). That is now carried over (§ 3b): 20.5 s.

## 5. Verdict

- **Recipe efficiency:** good at what it covers. The build recipe was flawless; the code recipe needed a
  46-line compile fix. It covers only about a quarter of the real work, and its marker count is misleading
  as a progress measure (F-083). Defects: R1–R5, L1.
- **Skill efficiency:** 170 / 170 markers resolved with its guidance, none kept. Its marker reference was
  right every time it applied. Its gaps (F-089) are the patterns that made 8 of 16 files need a second
  run.
- **Runtime:** right after the migration, Browserless was +60 %, ~91 % of it from `@UnifiedSpringBootTest`'s
  `RANDOM_PORT` times 9 contexts (F-086). With one shared base (§ 3b) it is **7.5 % faster than before the
  migration**. The Unified API's own per-test overhead is within noise.
- **Outcome:** every UI test now also runs in a real browser, the thing ADR-0012 ruled out for V1.
  ADR-0027 (Proposed) records the decision. It cannot be accepted or reach CI until alpha3 is published.
