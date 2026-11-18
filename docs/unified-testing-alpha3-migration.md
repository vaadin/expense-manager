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
| ApprovalAccessUiTest | 4 | 0.660 | 0.653–0.665 |
| ApprovalQueueViewUiTest | 8 | 0.400 | 0.385–0.443 |
| DashboardViewUiTest | 3 | 0.641 | 0.546–0.677 |
| ErrorDialogFlowUiTest | 3 | 0.604 | 0.580–0.617 |
| AbstractReferenceDataViewUiTest (base) | 2 | 2 | 0 | ~4 + `implements Locators` | 0 | 1 | — | — | `@Transactional` became a `TableSnapshot` of `vat_rate` and `expense_type` (new helper `base/TableSnapshot`, ~60 lines, generic: deletes added rows and updates changed ones back by id). `rowActionButton(page, GridLocator, rowIndex, colIndex, aria)` became `rowAction(UnifiedGrid, column, rowText, aria)`, so rows are found by their text instead of their position |
| VatRateViewUiTest | 1 | 1 | 0 | 1 | 0 | 1 | 4/4 | **4/4** | Column scan became `assertThat(grid.column("Rate")).hasCellTexts(..)`, a retrying, exact-order check. Denied route handled as in UserManagement |
| ReferenceConfigViewUiTest | 4 | 4 | 0 | ~21 | 0 | 1 | 6/6 | **6/6** | `ErrorSummary` found as `UnifiedDiv.withClassName("error-summary")`. `UI.getCurrent().getElement().getTextRecursively()` became `summary.hasTextContaining(..)` (R3 again, unmarked). Disabled boundary buttons use `assertThat(button).isDisabled()` |
| ExpenseTypeViewUiTest | 7 | 7 | 0 | ~29 | 0 | 1 | 11/11 | **11/11** | The glyph (`SvgIcon.getSymbol()` on the server) became `row.cell("Icon").findComponent(SvgIcon.class)` with `hasProperty("symbol", ..)`. `ReferenceTabs` uses `UnifiedTabs.withClassName("reference-tabs").hasSelectedTab(..)`, so no Wrapper was needed (S5). Rows are found by name. The four "Component Reference" markers with swallowed comments (R2) were resolved and their comments restored by hand. The whole family also passes together with `UserManagementViewUiTest` in one run on both tools, which shows the snapshot restore isolates the tests |
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
- **R2, recipe: a comment above a marked statement is swallowed into the marker.** In 6 places
  (`ApprovalAccessUiTest` 1, `ExpenseTypeViewUiTest` 3, `ReportDetailViewUiTest` 2), the statement's leading
  comment is joined into the `// TODO unified:` line with its line breaks removed, e.g.
  `// TODO unified: Browserless Escape Hatch:// The review alias is @PermitAll but gated in-view: a USER is forwarded to// the dashboard, ...navigate(..)`.
  The original comment no longer exists as a comment, so it is **lost when the marker is resolved and
  deleted**.
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

## 3. Browserless runtime — after

_Step 7. Same command and protocol as the baseline._
