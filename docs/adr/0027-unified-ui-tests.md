# ADR-0027 — UI tests on the Unified API: written once, run on Browserless and Playwright

**Status:** Proposed. It moves to *Accepted* once Unified Test is published to a repository CI can
reach (see Consequences). On acceptance it supersedes **layer 3** of ADR-0012; layers 1 and 2 are
unaffected.

## Context

ADR-0012 put the Vaadin UI tests (layer 3) on `browserless-test-spring`. They exercise views on the
server, without a browser. That is fast, but nothing that happens only in a browser can be seen there
(F-080: a page-wide horizontal scroll a green Browserless suite never caught). ADR-0012 also ruled out
automated browser tests for V1.

[Unified Test](https://github.com/parttio/unified-testing-tool) (`org.vaadin.addons:unified-test-*`)
lets a test be written once against `UnifiedPage` and `Unified*` Wrappers, then run on Browserless,
Playwright or Selenium. The `vaadin.testing.tool` setting chooses the tool for each run. An alpha2 spike
on branch `unified-testing` ported the suite by hand. With **0.1.0-alpha3** the suite was migrated again
from `main`, through the library's OpenRewrite recipe and its `resolve-migration-markers` skill, and every
step was measured: `docs/unified-testing-alpha3-migration.md`.

Result: **all 205 UI tests pass on Browserless and on Playwright**, with no test restricted to one tool.

## Decision

1. **Layer 3 is written on the Unified API, 0.1.0-alpha3.** Every `*UiTest` is `@UnifiedSpringBootTest`
   and takes a `UnifiedPage` parameter. Test code never names a Testing Tool. The one exception is a
   `page.hasRealBrowser()` choice of date format, because `typeText` parses differently per tool (F-087).
2. **Browserless stays the default and the developer loop.** Playwright is the browser tool:
   `./mvnw test -Dtest='*UiTest' -Dvaadin.testing.tool=playwright`. Selenium is not used (it needs a
   license and was not part of this migration).
3. **Isolation is snapshot-and-restore, not `@Transactional` rollback.** A browser's request threads
   never see the test's transaction. `base/TableSnapshot` takes the tables a test family writes before
   each test and restores them by id afterwards. A family covers everything its tests write, service calls
   included (F-088): the reference tables, the rate tables, and the reports with the reference tables.
   Fixtures that walk a lazy association run in a `TransactionTemplate`. Layer-2 integration tests keep
   `@Transactional` rollback (ADR-0012).
4. **Assertions check what a user can see,** through Wrappers and their retrying assertions. An absence
   check uses `.visible()`, since lookups include hidden components on every tool (F-087). Checkboxes are
   set with `check()`/`uncheck()`, never clicked. Persisted state is checked through the services, which
   run in the same JVM on every tool, after a UI assertion has waited for the server.
5. **Escape Hatches:** none is kept. Where a check needs a server-side object that has no DOM reading
   common to both tools (`FormLayout` responsive steps and spacings, `DateTimePicker` min/max wording),
   it is an extra check through `tryUnwrap(..)`. The test still runs everywhere and makes that one check
   where the component is at hand.
6. **Wrappers:** the Core Set first, then the Core Set Wrapper of a superclass plus a filter
   (`UnifiedMenuBar` for `RowActionMenu`, `UnifiedDiv.withClassName("error-summary")` for
   `ErrorSummary`), then `findComponent(..)`. Only three project Wrappers exist, in `base/ui`:
   `UnifiedPageHeader` (`selector = ".page-header"`), `UnifiedVerticalLayout` and
   `UnifiedHorizontalLayout`.
7. **Test-only routes are allowed** under `src/test` when a scenario needs a trigger the app does not
   have (`ErrorFlowTestView`). alpha3 finds routes in the test class's package without configuration.

## Consequences

- **CI cannot build this yet.** The alpha3 artifacts are only in a local `~/.m2` (installed from source),
  and `tests.yml` runs `./mvnw test` on every PR to `main`. Until Unified Test is published (Maven
  Central or the Vaadin add-ons repository), a PR from this branch fails at dependency resolution. A
  Playwright leg can be added to `tests.yml` then, `continue-on-error` at first.
- **Vaadin follows the library.** The alpha BOM pins Browserless Test 25.3.2, so the app moved to Vaadin
  25.3.5 first (F-082).
- **The Browserless loop is 60 % slower** (sum of class times 20 s → 32 s). Almost all of it is
  `@UnifiedSpringBootTest` starting Tomcat for each of this suite's 9 Spring contexts (F-086). Follow-up:
  one shared UI-test base, so one context. Upstream: a mock web environment on Browserless.
- **A browser run is much slower:** `ReportDetailViewUiTest` takes ≈ 118 s on Playwright against ≈ 9 s on
  Browserless. That is fine for a CI leg, not for the inner loop.
- **The recipe gets a migration started; it does not finish it.** It left 170 markers and ≈ 440 unmarked
  Browserless-only lines (F-083, F-084). A future migration of the same kind should budget for the
  unmarked part and end with a full-suite run on both tools.
- Upstream issues to file: F-083 to F-089.
