# Unified testing tool: draft upstream issues

These were drafted for [parttio/unified-testing-tool](https://github.com/parttio/unified-testing-tool) and are now filed there (2026-11-10; the Status column links each issue),
found while porting this app's UI tests to `0.1.0-alpha1` (ADR-0027, proposed). They
are written to be pasted as issues and filed by a person. Each one links back to its
finding in `docs/findings.md`.

`jcgueriaud1/unified-testing-tool` redirects to `parttio/unified-testing-tool`.

Context for every issue: Spring Boot 4.1.1, Vaadin 25.3.5, Java 25, Postgres
(Testcontainers), `@UnifiedSpringBootTest`, and the Browserless and Playwright adapters.

| # | Title | Finding | Status |
|---|---|---|---|
| U-1 | `@Transactional` rollback silently differs between Browserless and browser tools | F-083 | [#81](https://github.com/parttio/unified-testing-tool/issues/81) |
| U-2 | Browserless under Spring only finds routes in the test class's package | F-084 | [#82](https://github.com/parttio/unified-testing-tool/issues/82) |
| U-3 | Page-wide find misses Grid cell components on Browserless | F-085 | [#83](https://github.com/parttio/unified-testing-tool/issues/83) |
| U-4 | `getText()` means different text per tool (`innerText` vs server string) | F-086 | [#84](https://github.com/parttio/unified-testing-tool/issues/84) |
| U-5 | Playwright: class/attribute/theme filters are evaluated per element in Java | F-087 | [#85](https://github.com/parttio/unified-testing-tool/issues/85) |
| U-6 | Custom wrappers can't carry a default filter; tag-less `findComponent` fails only in a browser | F-088 | [#86](https://github.com/parttio/unified-testing-tool/issues/86) |
| U-7 | `findChildren(tag)` is direct children on Browserless, all descendants (shadow DOM too) on Playwright | F-089 | [#87](https://github.com/parttio/unified-testing-tool/issues/87) |
| U-8 | Hidden elements: Browserless excludes them, a browser query includes them | F-090 | [#88](https://github.com/parttio/unified-testing-tool/issues/88) |
| U-9 | Closed dialogs (incl. dev tools') match `UnifiedDialog` in a browser | F-091 | [#89](https://github.com/parttio/unified-testing-tool/issues/89) |
| U-10 | Browserless `withAccessibleName` ignores the `accessibleName` property (`setAriaLabel`) | F-092 | [#90](https://github.com/parttio/unified-testing-tool/issues/90) |
| U-11 | Playwright `isAt` misses client-side navigation; can't assert route-less error views | F-093 | [#91](https://github.com/parttio/unified-testing-tool/issues/91) |
| U-12 | Browserless doesn't route uncaught listener exceptions to the session `ErrorHandler` | F-094 | [#92](https://github.com/parttio/unified-testing-tool/issues/92) |
| U-13 | Core Set gaps: MenuBar, Upload, TextArea, BigDecimal/IntegerField, DateTimePicker, Tabs, tag Scopes | F-097, F-099 | [#93](https://github.com/parttio/unified-testing-tool/issues/93) |
| U-14 | `@UnifiedSpringBootTest` on Browserless silently needs `browserless-test-spring` at runtime | F-100 | [#94](https://github.com/parttio/unified-testing-tool/issues/94) |
| U-15 | Selenium adapter: `StaleElementReferenceException` when Vaadin re-renders during a filtered find | benchmark S7 | [#95](https://github.com/parttio/unified-testing-tool/issues/95) |
| U-16 | Selenium adapter: a new browser for every test (+0.6 s per test vs Playwright) | benchmark | [#96](https://github.com/parttio/unified-testing-tool/issues/96) |
| U-17 | Core `UnifiedTabs` names a tab by `getLabel()` only on Browserless (empty for a `RouterLink` tab) | alpha2 Core Set | [#107](https://github.com/parttio/unified-testing-tool/issues/107) |
| U-18 | Core `UnifiedMenuBar`: no aria-label item lookup, no enabled state | alpha2 Core Set | [#108](https://github.com/parttio/unified-testing-tool/issues/108) |
| U-19 | Core Playwright `upload()` times out when the app clears the file list | alpha2 Core Set | [#122](https://github.com/parttio/unified-testing-tool/issues/122) |

---

## U-1 — `@Transactional` test rollback silently differs between Browserless and browser Testing Tools

**Problem.** On Spring Boot, a common pattern is `@Transactional` on a UI test so data
the test creates is rolled back afterwards. Under `@UnifiedSpringBootTest`:

- **Browserless:** the view code runs on the test thread, inside the test's
  transaction. It sees uncommitted data, and everything rolls back afterwards. The
  test passes.
- **Playwright / Selenium:** the view code runs on a server request thread outside
  that transaction. It cannot see the data the test created and hasn't committed, and
  anything the UI commits is never rolled back. The test fails, or it leaves data
  behind that breaks later tests.

So the same test means different things depending on which tool runs it. Nothing
warns you, and you only find out when the browser run fails. That breaks the API's
promise that you write a test once and run it on any tool.

**Reproduce.** Mark a `@UnifiedSpringBootTest` `@Transactional`, create a row through a
service in the test, then `page.navigate(ListView.class)` and assert the row is in the
grid. Browserless: green. `-Dvaadin.testing.tool=playwright`: the row isn't found.

**Ask.**
1. The spec (02-test-setup) should say how to isolate test data on every tool.
2. Fail fast, or at least warn, when a `@UnifiedSpringBootTest` is `@Transactional`
   and runs on a browser tool.
3. Possibly provide a portable way to reset data after each test. Our snapshot/restore
   JUnit extension (about 100 lines, Postgres) is available to contribute: copy every
   table to a side schema once after boot, then truncate and copy back after each test
   with `session_replication_role = replica`.

---

## U-2 — Browserless under `@UnifiedSpringBootTest` only finds routes in the test class's package

**Problem.** A test in `com.example.user.ui` that navigates to
`com.example.base.ui.DashboardView` fails on Browserless with
`NotFound: No route found for the given navigation target 'DashboardView'`. The same
test passes on Playwright. `vaadin.testing.browserless.view-packages` defaults to the
test class's package even when the app is built from the Spring context. The spec
says Spring apps are "built in the test JVM from the Spring context", and
`SpringBrowserlessTest` found these routes.

**Ask.** Under Spring, default to the `@SpringBootApplication` package (or every
`@Route` the Spring context knows about). Failing that, have the error mention
`view-packages`.

**Workaround.** `vaadin.testing.browserless.view-packages=<app base package>` in
`junit-platform.properties`.

---

## U-3 — A page-wide `find` misses components rendered in a Grid cell on Browserless

**Problem.** A `Button` rendered by a Grid component column:
`page.find(UnifiedButton.class).withAccessibleName("Edit user a@b.c").single()`.
Playwright finds it. Browserless times out with "No component matches". Scoped as
`grid.rows().withCellText("Email", "a@b.c").single().cell("").find(UnifiedButton.class)`
it is found on both tools. Underneath, Browserless Test 25.3's UI-rooted queries do
not walk Grid cell components; `use(component)` on such an instance fails the same
way.

**Ask.** Include Grid cell components in Browserless page-wide queries, or fail with a
message that names the cause ("inside a Grid cell, scope through `row.cell(..)`").

---

## U-4 — `getText()` returns different text per Testing Tool

**Problem.** Playwright's `getText()` is `locator.innerText()`, which applies CSS:
`text-transform: uppercase` turns "Needs your attention" into "NEEDS YOUR ATTENTION",
and hidden elements drop out. Browserless returns `getTextRecursively()`, the
server-side string. An exact text assertion passes on one tool and fails on the other.

**Ask.** Specify what `getText()` means, and make both adapters return it. If it is
meant to be "what the user sees", document the Browserless difference. If it is meant
to be "the text in the DOM", use `textContent` in the browser adapters.

---

## U-5 — Playwright adapter: class-name, attribute and theme filters are evaluated per element in Java

**Problem.** `PlaywrightSession#find` puts only id and test-id into the CSS selector.
Every other filter is checked per candidate element, with one browser round trip each.
With `UnifiedContainer` (tag `*`),
`page.find(UnifiedContainer.class).withClassName("x")` checks every element on the
page. On our report list view that is around 4 s per lookup. One test class went from
**180 s to 54 s** once we replaced those lookups with tag-specific ones.

**Ask.** Put `withClassName` (`[class~="x"]` or `.x`), `withAttribute` (`[a="v"]`) and
`withTheme` (`[theme~="v"]`) into the selector. Only filters that really need the
element (`where`, accessible name) should be checked one by one.

---

> **Addendum to U-4 (F-095).** Beyond `text-transform`: a dialog's `getText()` is
> `textContent` on Playwright while other components use `innerText`; innerText adds
> line breaks between flex children; `getAttribute("style")` is formatted differently
> per tool, and a `var()` shorthand (`gap: var(--x)`) shows only as empty longhands in
> a browser. Ask: also a portable `getInlineStyle(name)`.
>
> **Addendum to U-5 (F-096).** Lazy wrappers re-resolve their whole chain on every
> read, so the per-element cost multiplies (5.9 s vs 0.5 s for one rate-card test);
> and a scan that lists ~170 candidates right after a dialog closes waits the full
> timeout on the one that detached. Pushing the filter into the selector fixes both.

---

## U-6 — Custom wrappers can't carry a default filter; a tag-less `findComponent` fails only in a browser

**Problem.**
1. `@UnifiedComponent(MetricCard.class)`, where `MetricCard extends VerticalLayout`,
   matches by class on Browserless but by tag in a browser, which means every
   `<vaadin-vertical-layout>`. A wrapper cannot declare the filter that makes the two
   match (`.metric-card`), so every call site has to remember to add it.
2. `card.findComponent(Component.class)` works on Browserless and throws
   "`Component` has no @Tag; set @UnifiedComponent(tag = "...")" on Playwright.

Both pass the local Browserless run and break the browser run, which is the costlier
direction.

**Ask.**
1. Add a `selector` (or default-filter) attribute to `@UnifiedComponent`.
2. Validate tag-less queries up front on every tool, so Browserless fails the same way
   a browser does.

---

## U-7 — `UnifiedElement.findChildren(tag)` means different things per tool

**Problem.** Browserless returns direct children (`element().getChildren()`);
Playwright returns every descendant (`locator.locator(tag)`), piercing shadow roots —
`:scope > *` even returns a form layout's internal `<div id="layout">`. Browserless
also throws `UnsupportedOperationException` (`Element.getTag()`) on a parent that mixes
text nodes and components, such as a Button with an icon. There is no portable "direct
children in order" and no wildcard tag.

**Ask.** Direct light-DOM children on both tools (`:scope > tag` in a browser); skip
text nodes on Browserless; an ordered `children()`.

---

## U-8 — Hidden elements count in a browser query but not on Browserless

**Problem.** After `button.setVisible(false)` on a rendered button,
`page.find(UnifiedButton.class).withText("Approve").exists()` is false on Browserless
and true on Playwright: the element stays in the DOM with `hidden`. (A component that
was never visible is absent on both.) Related: Flow defers a hidden component's
attributes, so a class check on a hidden component passes on Browserless only.

**Ask.** One rule on every tool — exclude `hidden` by default, or a `.visible()` filter
— and document it.

---

## U-9 — Closed dialogs match `UnifiedDialog` in a browser

**Problem.** In dev mode the page holds closed `<vaadin-dialog>`s from Vaadin's tooling
(one has class `ai-dialog`). `page.find(UnifiedDialog.class).single()` finds the app's
one open dialog on Browserless and four in a browser.

**Ask.** Match open dialogs by default (or an `opened()` filter), and ignore dev-tools
elements.

---

## U-10 — Browserless `withAccessibleName` ignores `setAriaLabel` on fields

**Problem.** Flow's `setAriaLabel` on a field sets the `accessibleName` property, which
the web component forwards to its inner `<input>`. Playwright finds the field by that
name; `BrowserlessElement.accessibleName()` reads only a host `aria-label` attribute,
then `label`, then text, and times out.

**Ask.** Consult the `accessibleName` property on Browserless.

---

## U-11 — Playwright `isAt` and client-side navigation; route-less views

**Problem.** (1) After clicking a `RouterLink`, `assertThat(page).isAt(View.class)`
timed out with path `""` although the browser was at the target: the assertion polls
`page.url()`, which the Java client refreshes only on the next browser call. (2)
`isAt(view)` needs a route path, so a `RouteNotFoundError` view can't be asserted in a
browser; Browserless uses `instanceof`.

**Ask.** Read `location` via `evaluate` in `isAt`/`hasPath`; match error views by
class.

---

## U-12 — Browserless doesn't reach the session `ErrorHandler`

**Problem.** In a browser an uncaught exception in a click listener goes to
`VaadinSession`'s `ErrorHandler` (the app shows its error dialog). On Browserless it is
rethrown into the test (`MockVaadin.runUIQueue`), so a portable test of the app's error
handling needs a per-tool wrapper.

**Ask.** An opt-in Browserless setting that routes uncaught exceptions to the session
error handler.

---

## U-13 — Core Set gaps a typical Flow app hits

**Problem.** Porting one app's 14 UI test classes needed project wrappers for
`MenuBar` and `Upload` (both with per-tool overrides: sub-menus exist only while open,
and there is no portable upload), `TextArea`, `BigDecimalField`, `IntegerField`,
`DateTimePicker`, a bare `Tabs` bar, `FormLayout`, and Scope wrappers bound to a real
tag (`Div`, `Span`, `Nav`, the layouts). The Playwright DatePicker support casts the
value to `LocalDate`, so raw or unparseable typing needs its own wrapper, and
`DateTimePickerTester` can't type into one half of the picker.

**Ask.** Add these to the Core Set (plus `Details`, see below), with a raw `typeText`
on date fields. Our generic wrappers are available to contribute
(`src/test/java/.../base/ui/unified/`).

---

## U-14 — Browserless under `@UnifiedSpringBootTest` silently needs `browserless-test-spring`

**Problem.** With `unified-test-spring` and `unified-test-browserless` but without
`browserless-test-spring`, everything compiles and every Browserless navigation fails
with `Unable to create an instance of '<MainLayout>'`: views are built without Spring.
The browser adapters are unaffected, so it looks like a Browserless flake.

**Ask.** Bring the Spring module in transitively for Spring users, or fail fast under
`@UnifiedSpringBootTest` with "add browserless-test-spring".

---

## Not issues: what worked on the first try

- `@WithUserDetails` with a JPA-backed `UserDetailsService`: the browser was logged in
  through the test-login endpoint with no configuration.
- `UserManagementViewUiTest` (grid, combo boxes, radio group, checkbox, dialog,
  service-side seeding) passed on Playwright on its first run.
- The per-tool override pattern (`@ToolImplementation`) for a `Details` wrapper:
  clicking the summary in a browser, `DetailsTester.toggleDetails()` on Browserless.

Suggestion: `Details` is a strong candidate for the Core Set. It is a common layout
toggle, and it is the one component we had to wrap with a per-tool override.
