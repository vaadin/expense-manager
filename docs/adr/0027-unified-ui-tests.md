# ADR-0027 — UI tests on the Unified API: written once, run on Browserless and Playwright

**Status:** Proposed. This is a spike on branch `try/unified-testing-tool`. It becomes
*Accepted* only if the spike holds up; if it does, it supersedes **layer 3** of
ADR-0012. Layers 1 and 2 of ADR-0012 are unaffected.

## Context

ADR-0012 put the Vaadin UI tests (layer 3) on `browserless-test-spring`. They
exercise views on the server, without a browser. That is fast, but it cannot see
anything that happens only in the browser. F-080 is the standing example: a
page-wide horizontal scroll that a green Browserless suite could never catch.
ADR-0012 also ruled out automated browser tests for V1.

The [Unified API for Vaadin UI tests](https://github.com/parttio/unified-testing-tool)
(`org.vaadin.addons:unified-test-*`, `0.1.0-alpha1`) lets a test be written once
against `UnifiedPage` and wrappers, then run on Browserless, on Playwright through
Dramafinder, or on Selenium through TestBench. A system property chooses the tool for
each run: `vaadin.testing.tool`. In principle, the same tests could give us a browser
run without writing a second suite.

## Decision

1. **Layer 3 is written on the Unified API.** Tests extend `AbstractUiTest`
   (`@UnifiedSpringBootTest`) and take a `UnifiedPage` parameter. Every `*UiTest` is
   one; the name is the selector.
   Test code never names a Testing Tool.
2. **Every test is portable, and both tools run in CI.** In the `tests.yml` matrix,
   the `browserless` leg runs the whole suite and the `playwright` leg runs
   `-Dtest='*UiTest'` in Chromium. The Playwright leg is `continue-on-error` while
   this ADR is Proposed. Running only part of the suite on Playwright later is a
   tuning decision, not a change to this ADR.
3. **Data isolation is snapshot-and-restore, not `@Transactional` rollback.** On
   Playwright the view runs on a server request thread, outside the test's
   transaction. So `@Transactional` would mean one thing on Browserless and another on
   Playwright, and nothing would warn about it (F-083).
   `DatabaseSnapshotExtension` works like this:
   - Once per JVM, after the Spring context has booted, it copies every table into a
     `test_snapshot` schema.
   - After every test, it truncates the tables and copies the rows back.
   - Both tools get the same behaviour: every write commits, and the database is put
     back afterwards.
   - Layer-2 integration tests keep `@Transactional` rollback.
4. **Assertions check what a user can see**, through wrappers and their retrying
   assertions. Saved state is checked through the services, which are in the same JVM
   on every tool. Use of `@RequiresTestingTool(BROWSERLESS)` has to be justified in a
   finding. One test uses it: the date-time picker's error messages exist only on the
   server (F-099). Wrappers may use the escape hatch inside a per-tool override; test
   bodies do not.
5. **Components missing from the Core Set get project wrappers** under
   `src/test/java/.../base/ui/unified/`. Each wrapper defines its methods on the
   portable Unified Element and uses per-tool overrides only where the browser and
   the server differ. Porting the suite took about a dozen (F-097). From
   0.1.0-alpha2 the Core Set covers most of them (TextArea, IntegerField,
   BigDecimalField, DateTimePicker, Tabs, Details, Div, Span, Nav, and
   `UnifiedDatePicker.typeText`), so those project wrappers were removed. Three
   stay:
   - `UnifiedMenuBar`: the Core Set's addresses items only by text, this app's
     menu triggers are icon-only (an `aria-label` and no text), and it cannot read
     an item's enabled state (parttio/unified-testing-tool#108).
   - `UnifiedUpload`: the Core upload on Playwright waits for the file list to
     complete, and this app clears the list once the file is handled. The project
     wrapper uses the Core adapter SPI on Browserless and Selenium and waits for
     the upload's POST on Playwright (parttio/unified-testing-tool#122).
   - The layout, FormLayout, metric-card and error-handling-button wrappers, which
     the Core Set has no equivalent for.
   The Core `UnifiedTabs` names a tab by `Tab#getLabel()` on Browserless, empty for
   a tab wrapping a `RouterLink`; `UnifiedElements.selectedTabText` reads it
   portably meanwhile (parttio/unified-testing-tool#107).
6. **Production code may gain role class names or test ids** where a test has no
   label or accessible name to find an element by. Accessible names come first,
   because ADR-0020 wants them anyway. So far the only addition is the
   `my-reports-view` root class.

## Consequences

- **Vaadin follows the tool.** The alpha supports one Vaadin minor version, so the
  app moved from 25.2 to 25.3 first. Two Browserless idioms broke in that upgrade
  (F-082).
- **Browser runs are several times slower.** All 205 UI tests: the whole 583-test
  suite takes about 29s on Browserless, the 205 UI tests about 4 min on Playwright,
  wall clock including boot. `ReportDetailViewUiTest` is 137s of that (104 tests).
  That is after narrowing queries by tag, which F-087 forced; before that,
  `MyReportsViewUiTest` alone took 180s on Playwright.
- **`browserless-test-spring` stays, though nothing imports it.** The Browserless
  adapter wires Spring in only when it is on the runtime classpath; without it every
  view fails to instantiate, and the build still compiles (F-100).
- **Some reads differ by tool, and tests have to be written around the difference.**
  Text uses `innerText` in a browser (CSS `text-transform` applies) but the
  server-side string on Browserless (F-086, F-095). Page-wide lookups reach Grid cell
  components only in a browser (F-085), hidden components only in a browser (F-090),
  and dev-tools dialogs only in a browser (F-091).
- **The whole layer is migrated.** All 14 UI test classes run on the Unified API;
  `SpringBrowserlessTest` and the direct `browserless-test-spring` dependency are gone
  (the Browserless adapter brings Browserless Test itself). Porting surfaced more
  per-tool differences (F-089 to F-096) and three assertions that had been vacuous all
  along (F-098).
- **It's an alpha.** The API can change between alphas, and gaps are tracked in
  `docs/unified-testing-upstream-issues.md`.
- **Reversible.** If the spike is dropped, the migrated tests can return to
  `SpringBrowserlessTest` mechanically. Git keeps the originals.
