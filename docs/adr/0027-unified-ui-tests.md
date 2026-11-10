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
   (`@UnifiedSpringBootTest`, `@Tag("unified")`) and take a `UnifiedPage` parameter.
   Test code never names a Testing Tool.
2. **Every test is portable, and both tools run in CI.** In the `tests.yml` matrix,
   the `browserless` leg runs the whole suite and the `playwright` leg runs
   `-Dgroups=unified` in Chromium. The Playwright leg is `continue-on-error` while
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
   on every tool. Use of the escape hatch (`@RequiresTestingTool(BROWSERLESS)`) has to
   be justified in a finding. So far no test uses it.
5. **Components missing from the Core Set get project wrappers** under
   `src/test/java/.../base/ui/unified/`. Each wrapper defines its methods on the
   portable Unified Element and uses per-tool overrides only where the browser and
   the server differ (`UnifiedDetails` uses `DetailsTester` on Browserless).
6. **Production code may gain role class names or test ids** where a test has no
   label or accessible name to find an element by. Accessible names come first,
   because ADR-0020 wants them anyway. So far the only addition is the
   `my-reports-view` root class.

## Consequences

- **Vaadin follows the tool.** The alpha supports one Vaadin minor version, so the
  app moved from 25.2 to 25.3 first. Two Browserless idioms broke in that upgrade
  (F-082).
- **Browser runs are 3–5× slower.** For the 31 migrated tests: about 27s on
  Browserless versus about 97s on Playwright, wall clock including boot. That is
  after narrowing queries by tag, which F-087 forced. Before that, `MyReportsViewUiTest`
  alone took 180s on Playwright.
- **Some reads differ by tool, and tests have to be written around the difference.**
  Text uses `innerText` in a browser (CSS `text-transform` applies) but the
  server-side string on Browserless (F-086). Page-wide lookups reach Grid cell
  components only in a browser (F-085).
- **The suite is mixed during migration.** 11 test classes are still on
  `SpringBrowserlessTest`. `ReportFixtures` holds the report seeding for both bases.
  `browserless-test-spring` stays until the last class moves.
- **It's an alpha.** The API can change between alphas, and gaps are tracked in
  `docs/unified-testing-upstream-issues.md`.
- **Reversible.** If the spike is dropped, the migrated tests can return to
  `SpringBrowserlessTest` mechanically. Git keeps the originals.
