# Unified testing tool: draft upstream issues

These are drafts for [parttio/unified-testing-tool](https://github.com/parttio/unified-testing-tool),
found while porting this app's UI tests to `0.1.0-alpha1` (ADR-0027, proposed). They
are written to be pasted as issues and filed by a person. Each one links back to its
finding in `docs/findings.md`.

The tool's README links its tracker as `jcgueriaud1/unified-testing-tool`, so file
them wherever its issues actually live.

Context for every issue: Spring Boot 4.1.1, Vaadin 25.3.5, Java 25, Postgres
(Testcontainers), `@UnifiedSpringBootTest`, and the Browserless and Playwright adapters.

| # | Title | Finding | Status |
|---|---|---|---|
| U-1 | `@Transactional` rollback silently differs between Browserless and browser tools | F-083 | draft |
| U-2 | Browserless under Spring only finds routes in the test class's package | F-084 | draft |
| U-3 | Page-wide find misses Grid cell components on Browserless | F-085 | draft |
| U-4 | `getText()` means different text per tool (`innerText` vs server string) | F-086 | draft |
| U-5 | Playwright: class/attribute/theme filters are evaluated per element in Java | F-087 | draft |
| U-6 | Custom wrappers can't carry a default filter; tag-less `findComponent` fails only in a browser | F-088 | draft |

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

## Not issues: what worked on the first try

- `@WithUserDetails` with a JPA-backed `UserDetailsService`: the browser was logged in
  through the test-login endpoint with no configuration.
- `UserManagementViewUiTest` (grid, combo boxes, radio group, checkbox, dialog,
  service-side seeding) passed on Playwright on its first run.
- The per-tool override pattern (`@ToolImplementation`) for a `Details` wrapper:
  clicking the summary in a browser, `DetailsTester.toggleDetails()` on Browserless.

Suggestion: `Details` is a strong candidate for the Core Set. It is a common layout
toggle, and it is the one component we had to wrap with a per-tool override.
