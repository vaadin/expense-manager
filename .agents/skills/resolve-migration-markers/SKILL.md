---
name: resolve-migration-markers
description: Work through the Migration Markers (// TODO unified:) the TestBench, Browserless or Dramafinder Migration Recipe left, until each is resolved into Unified API code or kept as an explained Escape Hatch.
disable-model-invocation: true
---

# Resolve Migration Markers

A Migration Recipe rewrote this project's TestBench (Selenium) tests, Browserless tests, Dramafinder (Playwright) tests, or several of them, into Unified UI Tests. Where it could not, or where a person has to check its choice, it left a **Migration Marker**: a `// TODO unified: ...` comment above the statement, method or class. Your job is to give every marker one of two outcomes:

- **Resolved**: the code now says it with the Unified API, the test passes, and the marker is gone.
- **Kept**: the code stays as it is, and the marker becomes `// Escape Hatch kept: <reason>`, one line saying what the Unified API lacks.

A test is better the less it depends on an Escape Hatch: it then runs on every Testing Tool (Browserless, Playwright, Selenium) instead of the one its Escape Hatch needs.

## Steps

1. **List the markers.** Search the test sources for `TODO unified:`. Write the list down: file, line, marker text. Done when the search and your list agree.
2. **Resolve or keep each marker,** using the reference below for its kind. Work one test class at a time, and run that class's tests (step 3) before the next. Done when no `TODO unified:` is left.
3. **Prove it.** For every test class you changed, run it on Browserless and on Playwright:
   `mvn test -Dtest=<Class>` and `mvn test -Dtest=<Class> -Dvaadin.testing.tool=playwright`.
   A test passes, or is skipped because it still declares `@RequiresTestingTool`. When one fails, fix it or put the Escape Hatch back with an explanation. Remove a `@RequiresTestingTool(TestingTool.PLAYWRIGHT)` only once the test passes on both. Done when every changed class passes on both.
4. **Report** a table: marker, outcome (resolved or kept), what you changed.

## Reference: the Unified API

Write the Unified code the way the `write-unified-ui-tests` skill (`.claude/skills/write-unified-ui-tests/SKILL.md`)
says: its canonical forms for lookups, grid rows and retrying assertions, and how to read a failure. Read it before
the first marker. Read a Wrapper's methods in the `unified-test-api` sources before writing a call.

## Reference: the Escape Hatches

Each gives a Testing Tool's own test base API on the test's page, and needs `@RequiresTestingTool` for its tool:

| Escape Hatch | Testing Tool | Annotation |
|---|---|---|
| `TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);` | Selenium | `@RequiresTestingTool(TestingTool.SELENIUM)` |
| `BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);` | Browserless | `@RequiresTestingTool(TestingTool.BROWSERLESS)` |
| `BrowserlessUIContext window = page.unwrap(BrowserlessUIContext.class);` (a context-API test's window, under its old name) | Browserless | `@RequiresTestingTool(TestingTool.BROWSERLESS)` |
| `Page playwright = page.unwrap(Page.class);` (Dramafinder elements are built on it) | Playwright | `@RequiresTestingTool(TestingTool.PLAYWRIGHT)` |

When a method no longer uses its Escape Hatch variable, delete that line. When a test, and the helpers it calls, no longer use one, delete its `@RequiresTestingTool` too: the test then runs everywhere.

## Reference: marker kinds of all recipes

**`was first(); the lookup fails when several components match`** (also `waitForFirst()`, `last()`, `atIndex(n)`, `getCell(..)`). Run the test. When it passes, delete the marker. When it fails with "several components match", add the filter that picks the intended one (`withLabel`, `withText`, `withId`, ...), then delete the marker.

**`was withCaption(..)` / `was withLabel(..)` on a button.** Read the view: when the button shows that text, delete the marker. When it is an icon-only button, the text is its accessible name: use `withAccessibleName(..)`, then delete the marker.

**`..., no Wrapper for X: write one (ADR 0003)` / `..., a custom element: write a Wrapper for it (ADR 0003)`.** Look for an existing Wrapper first: search for `@UnifiedComponent(` naming the same Flow component (a TestBench element's `@Element` tag is the component's `@Tag`). Use it when found. Otherwise write one next to the tests: an interface with `@UnifiedComponent(TheComponent.class)`, extending `UnifiedWrapper` and the filter mixins it needs with itself as the type argument (`CommonFilters<UnifiedX>`, and `LabelFilter<UnifiedX>`, `TextFilter<UnifiedX>` or `PlaceholderFilter<UnifiedX>`), with `default` methods written on `element()` (`getProperty`, `getText`, `findChildren(..)`, `click()`). Translate each call the test makes, then replace the lookup and delete the Escape Hatch. Keep it when the component's state lives only on the server (Browserless) or needs real browser rendering (Selenium).

## Reference: marker kinds of the Dramafinder recipe

**`Dramafinder Escape Hatch: <call>`.** Try to say it with the Unified API, then run the test on Browserless and Playwright:
- a Dramafinder lookup, `XElement.getByLabel(playwright, ..)` or `find(playwright, XElement.class)...`, becomes `page.find(UnifiedX.class).withLabel(..)`; an element call becomes the Wrapper's method, an `assert...` a retrying `assertThat(wrapper)...`;
- a raw Playwright locator names a component: `getByRole(AriaRole.BUTTON, ..setName("Save"))` is `page.find(UnifiedButton.class).withText("Save")` (or `withAccessibleName` for an icon button), `getByRole(AriaRole.TEXTBOX, ..setName("Name"))` and `getByLabel("Name")` are `page.find(UnifiedTextField.class).withLabel("Name")` (read the view for the field's type), `locator("vaadin-x")` is the Wrapper for that tag; `fill(..)` is `setValue(..)`, `click()` is `click()`, `PlaywrightAssertions.assertThat(locator).hasText(..)` is `assertThat(wrapper).hasText(..)`;
- a row of a grid, `findRow`, `getCell`, sorting: `UnifiedGrid`'s `row()` with its filters, `row(i)`, `column(..)`.

Keep it, `// Escape Hatch kept: <reason>`, only for what has no Unified meaning: `route(..)` and network mocking, tracing, `evaluate(..)` and other JavaScript, CSS selectors into a component's shadow DOM. When no statement uses `playwright` any more, delete its line.

**`Dramafinder Escape Hatch: new X(..), a custom element: write a Wrapper for it (ADR 0003)`.** `X` extends Dramafinder's `VaadinElement`. Turn it into a Wrapper (see the custom element kind above): each method of `X` the tests call becomes a `default` method on `element()`, written from what `X` does with its `Locator` (a `locator("span").nth(i).click()` is `element().findChildren(..)`, a `hasJSProperty("value", ..)` check reads `getProperty("value")` in a retrying assertion or `page.waitUntil(..)`). Replace the construction with `page.find(..)`, the calls with the Wrapper's, and delete `X` when nothing uses it.

**`was 1.x getByLabel(..), a case-insensitive substring and first match; tighten it to withLabel(..) once the test passes`** (also `getByText`, `withText`). Dramafinder 1.x matched a substring of the label, `aria-label` or placeholder. Replace `withAccessibleNameContaining(s)` with `withLabel(s)` (or `withTextContaining` with `withText`) and run the test: keep the exact filter when it passes, else use the filter that names the component exactly (read the view). Then delete the marker.

**`was findRowIndexesWithColumnText(n, ..).get(0): any column matches now; ...`.** Dramafinder matched the text in column `n` only. Read the view for that column's header and write `row().withCellText("<header>", text)`; run the test, then delete the marker.

**`was getCell(..).click(); a row's click() clicks the row, not that cell`.** Read the view: when the cell holds nothing clickable of its own, the row click does the same; delete the marker. When it holds a component (a button, a checkbox), find it in the cell: `row.cell("<header>").find(UnifiedButton.class)...`.

**`was getByText(..), a substring and first match; ...`** (a notification or badge). As `was first()`: run the test, add a filter when it finds several, then delete the marker.

## Reference: marker kinds of the TestBench recipe

**`TestBench Escape Hatch: <call>`.** Say the same thing with the Unified API when it can: a lookup becomes `page.find(..)`, a read becomes a Wrapper method or a retrying assertion, a script that checks the URL becomes `assertThat(page).hasPath(..)`. Keep it when it needs what only a real browser driver gives (arbitrary JavaScript, raw WebDriver, Selenium-only APIs).

**`TestBench page object: new X(this)`.** Convert the page object when every call in it has a Unified form: its TestBench field and constructor parameter become `UnifiedPage`, its `$(XElement.class)` lookups become `find(UnifiedX.class)`, its element return types become Wrappers. Then pass `page` instead of `testBench`. Keep it otherwise.

**`browser setup removed (...)`.** The Unified API's Selenium adapter chooses the browser. Move what the removed setup did to the `vaadin.testing.*` settings (`junit-platform.properties`, or system properties) when one covers it; otherwise there is nothing to carry over. Delete the marker either way and name the removed setup in your report.

## Reference: marker kinds of the Browserless recipe

**`Browserless Escape Hatch: <call>`** (also on a window, `window.findGrid(..)`). Say the same thing with the Unified API when it can: a locator such as `window.findTextField().withLabel(..)` becomes `page.find(UnifiedTextField.class).withLabel(..)`, `navigate(View.class)` becomes `page.navigate(View.class)` (when the test reads the view it returns, find what it reads with a locator instead), a tester call becomes the Wrapper's method, a component getter becomes a Wrapper getter or a retrying assertion. Keep it when it needs the server-side Java objects only Browserless has (a view's fields, beans, `UI` or `VaadinSession` state).

**`Component Reference: <call>: replace it with a locator that finds the same component`.** The test holds a component's Java object (a view field, `view.getChildren()...`) instead of finding it. Read the view to see how it builds that component, write the locator that finds it and nothing else (`withLabel`, `withText`, `withTestId`, ...), and use the Wrapper. Prove the locator is unique by running the test. Delete the local variable or view lookup that only served the reference.

**`filter runs Java on the component: <call>: replace withCondition(..) with a locator`** (also `withPropertyValue(..)`). The filter calls a Java method on each component. Read the view to find what sets that state, and pick a locator that matches the same component (its label, text or test id); run the test to prove it.

**`was fireShortcut(..); pressKey also reaches the focused component and an open dialog`.** `page.pressKey(..)` dispatches the key from the focused component, or from an open dialog, where `fireShortcut` only fired the shortcut on the UI. Run the test on Browserless and Playwright: when it passes, delete the marker. When a dialog now reacts to the key (Escape closes it), check what the test meant and adjust it.

**`was a scan of column <col>; withCellText(text) matches any column: name the column, withCellText(header, text), if another column can hold the text`.** The test scanned one column's cells for a text; `row().withCellText(text)` looks in every column. Read the view: when no other column can hold that text, delete the marker. Otherwise find column `<col>`'s header (the view's `addColumn(..).setHeader(..)`, or `grid.getColumnHeaders()`) and write `row().withCellText("<header>", text)`, then delete the marker. Run the test to prove the row is found.

**`Browserless-only pattern: @Transactional ...`.** The test's transaction reaches the views only on Browserless. Keep it, with `// Escape Hatch kept: test data rolled back by @Transactional`, unless the test can commit its data and clean up after itself; then remove `@Transactional` and `@RequiresTestingTool(TestingTool.BROWSERLESS)`.

**`Browserless-only pattern: a Spring context without @SpringBootTest ...`.** A narrow `@ContextConfiguration` has no web server for the browser Testing Tools. To run everywhere: replace `@UnifiedUITest`, `@ContextConfiguration` and `@ExtendWith(SpringExtension.class)` with `@UnifiedSpringBootTest`, mock the beans the narrow context left out with `@MockitoBean`, and remove `@RequiresTestingTool(TestingTool.BROWSERLESS)`. Keep it when the full application context can't start in the test.

**`not migrated: this test uses the Browserless application or user context ...`.** The recipe left the whole class as it is: a test opens a second user or window from the context API. Use `page.openWindow()` for another window of the same user and `page.openAsUser(name, roles...)` for another user, remove the context fields and their `@BeforeEach`/`@AfterEach`, then convert the rest by hand the way the recipe converts the others in this project.

**`not migrated: this class passes itself (this) to a page object or helper ...`.** The recipe left the whole class as Browserless Test. Make the page object or helper take a `UnifiedPage` and use the Unified API inside it, then convert the test class by hand the way the recipe converts the others in this project (compare a migrated class), passing `page` instead of `this`.

## Provenance

- **Upstream:** Unified Test (`org.vaadin.addons:unified-test-rewrite`), [https://github.com/parttio/unified-testing-tool](https://github.com/parttio/unified-testing-tool)
- **Source path:** `META-INF/unified/skills/resolve-migration-markers/SKILL.md` inside `unified-test-rewrite-0.1.0-alpha3.jar`
- **Version:** 0.1.0-alpha3 (jar sha256 `a983271722634661d8c5d68acd566ec645e67eefe4036549cdda92aaecffafb3`; upstream SKILL.md sha256 `3f61c2b62b7287125e5f67e5677457442d93c9a40e88c5a1d4ea3c92aa7b2308`)
- **Copied:** 2026-11-18, by hand, as a project-owned file. **Not** managed by `skills.sh` / `skills-lock.json`.
- **Locally modified:** no. Only this Provenance section was added, so the migration log
  (`docs/unified-testing-alpha3-migration.md`) measures the skill as shipped.
