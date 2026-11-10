# Findings Log

Living backlog of friction and gaps, per the brief's taxonomy. This is the main
output of the project — every non-trivial work item should leave findings here.

**Areas:** Spec · AI · Vaadin · Tooling/Template · Docs · Verification ·
Deployment/Observability · UX-spec
**Severity:** Low · Medium · High

## Template

```
### F-NNN — <title>
- Date:
- Area:
- Severity:
- Task being attempted:
- Expected vs actual:
- Workaround used:
- Evidence: (prompt / screenshot / code pointer / log / failing test / URL)
- Impact:
- Suggested Vaadin/product improvement:
- Owner / next step:
```

---

### F-001 — Skeleton Java version mismatch (pom 25 vs Dockerfile 21)
- Date: 2026-07-08
- Area: Tooling/Template
- Severity: Medium
- Task being attempted: Reviewing the generated start.vaadin.com skeleton before
  building.
- Expected vs actual: Expected a consistent JDK target. Actual: `pom.xml` sets
  `java.version=25` while `Dockerfile` builds on `eclipse-temurin:21-jdk` and
  runs on `eclipse-temurin:21-jre-alpine`.
- Workaround used: Standardize on Java 25; bump Dockerfile base images (ADR-0014).
- Evidence: `pom.xml:11`, `Dockerfile` (build + runtime stages).
- Impact: "Works locally, breaks in container" class of surprise; silent until
  a Java 25 feature is used.
- Suggested Vaadin/product improvement: start.vaadin.com should keep the
  Dockerfile base image in sync with the pom's `java.version`.
- Owner / next step: Resolved in Phase 0.1 (#4) — Dockerfile build/runtime stages
  bumped to `eclipse-temurin:25-jdk` / `eclipse-temurin:25-jre-alpine` to match the
  pom's Java 25 target.

### F-002 — StreamResource deprecated in Vaadin 25 (streaming API for receipts)
- Date: 2026-07-08
- Area: Docs
- Severity: Low
- Task being attempted: Deciding how to stream receipt bytes to/from the UI.
- Expected vs actual: `StreamResource` is the widely-documented approach but is
  deprecated in Vaadin 25; the current API is `DownloadHandler`/`UploadHandler`
  (`com.vaadin.flow.server.streams`).
- Workaround used: Use the current streaming API; avoid `StreamResource`
  (ADR-0009).
- Evidence: user guidance; Vaadin 25.2 streaming docs.
- Impact: AI agents and older docs/examples will reach for the deprecated class.
- Suggested Vaadin/product improvement: ensure MCP/docs surface the replacement
  prominently on any `StreamResource` reference.
- Owner / next step: confirm exact API usage when implementing receipts.

### F-003 — Finnish VAT rates are statutory and change most years
- Date: 2026-07-09
- Area: Spec
- Severity: Medium
- Task being attempted: Deciding how expense lines capture VAT (Phase 2, ADR-0018).
- Expected vs actual: Assumed a stable reduced rate of 14%; actual 2026 reduced
  rate is **13.5%** (14% → 13.5% on 1 Jan 2026; general 24% → 25.5% on 1 Sept
  2024; several 10% supplies → 14% on 1 Jan 2025). Rates move roughly yearly.
- Workaround used: Model `VatRate`/`ExpenseType` as admin-editable config with an
  `active` flag rather than enums; lines store an FK to the rate they were filed
  under, so past reports never re-compute when the law changes (ADR-0018).
- Evidence: user (authoritative); Verohallinto VAT-rate change history.
- Impact: Hard-coded rates would be a money bug (ADR-0010) and a redeploy-per-law-
  change maintenance burden; per-year versioning would be over-engineering given
  the FK-preserves-history approach.
- Suggested Vaadin/product improvement: n/a (domain/spec finding).
- Owner / next step: **Resolved (2026-07-10, Phase 2.1 #22).** V3 migration seeds
  the 2026 figures **25.5 / 13.5 / 10 / 0** (general 25.5 %; reduced 13.5 %, i.e.
  the 14 % → 13.5 % change of 1 Jan 2026; reduced 10 %; zero-rated), with the six
  expense types mapped to those rates. Values held authoritative per the issue
  author (Finnish tax domain). Rate changes ship as deactivate-old + add-new, never
  an in-place value edit, so filed lines keep their original rate (ADR-0018).

### F-005 — Testcontainers 2.x renamed the Maven modules (`testcontainers-*` prefix)
- Date: 2026-07-09
- Area: Tooling/Template
- Severity: Low
- Task being attempted: Adding the Testcontainers Postgres deps for the
  integration test layer (Phase 0.2, ADR-0004).
- Expected vs actual: The issue and virtually all docs/examples use the 1.x
  coordinates `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter`.
  Spring Boot 4.1.0 manages **Testcontainers 2.0.5**, which renamed the modules
  to `testcontainers-postgresql` and `testcontainers-junit-jupiter` (same
  `org.testcontainers` groupId). The 1.x artifactIds resolve to no managed
  version → `'dependencies.dependency.version' ... is missing` at POM read time.
- Workaround used: Use the `testcontainers-*`-prefixed artifactIds; versions come
  from the `testcontainers-bom` already imported by `spring-boot-dependencies`
  (no explicit BOM import needed).
- Evidence: `pom.xml` test deps; `testcontainers-bom-2.0.5.pom`.
- Impact: AI agents and copied snippets will reach for the 1.x names and hit a
  confusing build-model error rather than a normal dependency-resolution failure.
- Suggested Vaadin/product improvement: n/a (Testcontainers/Spring Boot ecosystem).
- Owner / next step: wire the actual Testcontainers harness in Phase 0.9.

### F-006 — Security starter added early activates default Spring Security
- Date: 2026-07-09
- Area: Tooling/Template
- Severity: Low
- Task being attempted: Adding `spring-boot-starter-security` +
  `-oauth2-client` in Phase 0.2 so later phases have their deps ready, before
  the security config exists (Phase 1.4).
- Expected vs actual: Expected the foundation app to boot open. Actual: the mere
  presence of the security starter (plus a Google `ClientRegistration` from the
  `local` env-var defaults) activates Spring Security's default filter chain, so
  every endpoint — including `/actuator/health` — returns `302` to a login page
  until the Phase 1.4 config lands.
- Workaround used: Accepted for this slice — Phase 0.2's acceptance is "boots and
  connects to Postgres", which holds (verified: Hikari connected to the Compose
  Postgres, Tomcat up, no errors). No `SecurityConfig` written here; that is
  Phase 1.4's job, and Actuator health is opened/wired in Phase 0.8.
- Evidence: boot log (`HikariPool-1 ... PgConnection`, `Started Application in
  35.9s`); `curl /actuator/health` → 302.
- Impact: Anyone probing the foundation app before Phase 1 sees a login wall;
  health checks won't pass until 0.8/1.4. Ordering artifact, not a defect.
- Suggested Vaadin/product improvement: n/a.
- Owner / next step: open `/actuator/health` (0.8) and wire
  `VaadinSecurityConfigurer` + route/method security (1.4).

### F-007 — Spring Boot 4 modular auto-config: Flyway silently doesn't run, actuator/test classes relocated
- Date: 2026-07-09
- Area: Tooling/Template
- Severity: Medium
- Task being attempted: Wiring the Flyway baseline, health-probe security, and
  the Testcontainers acceptance test (Phase 0.3, ADR-0005/0012/0013).
- Expected vs actual: Spring Boot 4.0 split the monolithic
  `spring-boot-autoconfigure` into per-technology modules, and the starters no
  longer pull every autoconfig transitively. Two concrete bites:
  1. **Flyway ran nowhere.** `flyway-core` + `flyway-database-postgresql` were on
     the classpath (Phase 0.2), but the JPA starter no longer brings Flyway's
     autoconfiguration, which now lives in a separate `spring-boot-flyway`
     module. The result is *silent*: no `Flyway` bean, no migration, and the
     context still boots because there are no entities for `ddl-auto=validate` to
     check — so "app starts fine" masks "schema was never migrated". Only the
     acceptance test (no `flyway_schema_history`, no `Flyway` bean to autowire)
     surfaced it.
  2. **Classes relocated across new modules.** `EndpointRequest` →
     `org.springframework.boot.security.autoconfigure.actuate.web.servlet`
     (module `spring-boot-security`); `HealthEndpoint` →
     `org.springframework.boot.health.actuate.endpoint` (module
     `spring-boot-health`); `@AutoConfigureMockMvc` moved to module
     `spring-boot-webmvc-test`, which `spring-boot-starter-test` does **not** pull.
- Workaround used: Added `spring-boot-flyway` explicitly. Used the new package
  coordinates for `EndpointRequest`/`HealthEndpoint`. Avoided `@AutoConfigureMockMvc`
  (module absent) by building `MockMvc` by hand from the `WebApplicationContext`
  plus the `springSecurityFilterChain` `FilterChainProxy` (both on the `spring-test`
  classpath) — this exercises the real security filter chain without adding a
  test dependency.
- Evidence: `NoSuchBeanDefinitionException: ... org.flywaydb.core.Flyway`;
  after the fix, boot log `Migrating schema "public" to version "1 - init"` /
  `now at version v1`; `FoundationAcceptanceTest` green (3/3).
- Impact: The most dangerous failure mode is #1 — a missing autoconfig module
  fails open (no error, no migration) rather than closed. Any Boot 4 phase that
  assumes "starter X ⇒ autoconfig X" (like pre-4 muscle memory / copied snippets)
  can ship a broken foundation that still starts. An integration test that
  actually asserts the behaviour is the only reliable guard.
- Suggested Vaadin/product improvement: n/a (Spring Boot 4 ecosystem change);
  worth a note in project onboarding that Boot 4 autoconfig is opt-in per module.
- Owner / next step: none — resolved in Phase 0.3. Watch for the same fail-open
  pattern when later phases add tech that needs its own `spring-boot-*` module.

### F-004 — Report line editor: inline Grid dropped for variant-C cards + modal dialog
- Date: 2026-07-13 (design 2026-07-09; built in #24)
- Area: Vaadin
- Severity: Low
- Task being attempted: The line editor for the report detail view (Phase 2.3,
  #24; ADR-0015/0019). Originally provisionally an inline Grid row editor with
  `ComboBox` columns, Binder validation, and Signals for live totals.
- Expected vs actual: The inline-Grid combination was **not** built. Prototyping
  four variants (see the retired `report/prototype/NOTES.md`) confirmed the
  provisional worry: the Grid editor collapsed to zero height in flex splits, its
  footer totals were imperative (Signals don't reach Grid footer cells out of the
  box), and per-row editor-component binding was the fiddliest option. Variant C
  (receipt-style cards + a focused modal `Dialog`) was chosen and built instead.
  Building it, the remaining friction was: (a) the expense-type → default-VAT-rate
  pre-fill can't be expressed declaratively in Binder — it needs an imperative
  `ComboBox` value-change listener guarded by `isFromClient()` so
  `binder.readBean` doesn't clobber a loaded rate; (b) making the whole card
  clickable *and* carrying a trash button means both fire on a trash click (DOM
  click bubbling).
- Workaround used: (a) the guarded value-change listener in `LineEditorDialog`;
  (b) the trash button lives *outside* the clickable card body (siblings, not
  nested), so no `stopPropagation` hack is needed. Live totals moved off the Grid
  footer entirely and onto plain `Span`s bound to a `Signal.computed` over a
  `ListSignal` of working lines — which worked cleanly (see F-023).
- Evidence: `report/ui/ReportDetailView`, `report/ui/LineEditorDialog`; the
  deleted `report/prototype/` package; ADR-0019.
- Impact: The cards+dialog editor is lower-density and mobile-friendly, and keeps
  totals off the Grid footer where Signals bind naturally. The type→rate default
  staying imperative is a small, well-contained exception to the Binder-declarative
  ideal.
- Suggested Vaadin/product improvement: a Binder affordance for cross-field
  defaults (set field B's value from field A on user change, without a manual
  `isFromClient` listener) would remove the one imperative seam.
- Owner / next step: none — variant C is the real view; prototype deleted.

### F-008 — Auto-menu shell was smooth; UI unit test couldn't reuse the integration base
- Date: 2026-07-09
- Area: Vaadin · Verification
- Severity: Low
- Task being attempted: Phase 0.4 base UI shell (#7) — Aura `MainLayout` with a
  `SideNav` auto-generated from `@Menu` via `MenuConfiguration`, `EmptyState`,
  and global 404 / uncaught-exception views (`HasErrorParameter`).
- Expected vs actual: The core shell was frictionless — `@Layout` + `AppLayout` +
  `MenuConfiguration.getMenuEntries()` produced the security-filterable side nav
  exactly as documented, and `RouteNotFoundError` / `HasErrorParameter` gave the
  two error surfaces with no surprises. The only snag was on the *test* side:
  `SpringBrowserlessTest` (Vaadin's browserless UI tester) must be the test's
  superclass, but so must `AbstractIntegrationTest` (our singleton-Testcontainers
  base) — Java's single inheritance forces one or the other.
- Workaround used: Duplicated the `@ServiceConnection` singleton
  `PostgreSQLContainer` static setup into `NavigationShellUiTest`. Testcontainers
  reuse means it shares the same Docker container, so the cost is a few lines of
  duplicated boilerplate, not a second database.
- Evidence: `NavigationShellUiTest` (3 green UI tests); `MainLayout`,
  `NotFoundView`, `ErrorView` in `base/ui/`.
- Impact: Low, but every future UI unit test faces the same choice. Worth a
  shared composition helper (e.g. a `@TestConfiguration` or a JUnit extension
  that contributes the container) so UI tests and integration tests can share
  the datasource wiring without an inheritance clash.
- Suggested Vaadin/product improvement: `browserless-test-spring` could offer a
  composable (extension-based) entry point rather than a mandatory base class,
  so projects with their own test base aren't forced to choose.
- Owner / next step: revisit when the second UI unit test lands — extract the
  container wiring into a shared JUnit extension if the duplication spreads.

### F-009 — `@Push` websocket threads don't inherit the request correlation id
- Date: 2026-07-09
- Area: Deployment/Observability
- Severity: Low
- Task being attempted: Phase 0.5 structured JSON logging & request correlation
  id (#8) — a lightweight `OncePerRequestFilter` (`RequestCorrelationFilter`)
  that stamps an `X-Request-Id` into the MDC so every log line for one user
  action shares an id, with no Micrometer/OTel tracing stack in V1.
- Expected vs actual: Expected the id to be present on all log lines produced
  while handling a user action. Actual: it is present for ordinary HTTP and
  Vaadin UIDL requests (which pass the servlet filter), but **not** for `@Push`
  messages. Server pushes run on Atmosphere websocket threads that never enter
  the servlet filter chain, so they inherit no MDC and their log lines print an
  empty `[]` correlation slot.
- Workaround used: Accepted and documented, not worked around (per ADR-0013 and
  the issue). Spring Boot 4's built-in structured logging
  (`logging.structured.format.console=ecs`) covers staging/prod with no
  logstash encoder or custom `logback-spring.xml`; `local` stays human-readable
  with `logging.pattern.correlation=[%X{requestId:-}]`.
- Evidence: `RequestCorrelationFilter` javadoc; `application.properties`
  (`logging.pattern.correlation`); `RequestCorrelationFilterTest`.
- Impact: Log lines emitted from push handlers can't be tied back to the
  originating user action by id alone. Low for V1 (single service, push is a
  minor share of logging); grows if push-driven flows expand.
- Suggested Vaadin/product improvement: a documented hook to propagate MDC (or
  a request-scoped context) onto the Atmosphere push thread would close the gap
  without a full tracing dependency.
- Owner / next step: upgrade path is Micrometer Tracing if/when a second service
  or richer correlation is needed; revisit then.

### F-010 — `LoginI18n.createDefault()` returns a null `Header`
- Date: 2026-07-09
- Area: Vaadin
- Severity: Low
- Task being attempted: Phase 1.1 (#9) local form-stub login — a public
  `LoginView` hosting a `LoginForm`, customising its title/description via
  `LoginI18n`.
- Expected vs actual: Expected `LoginI18n.createDefault()` to return a fully
  populated i18n object (the "default" name implies every sub-object is present,
  and `getForm()` *is* populated). Actual: `getHeader()` returns `null`, so
  `i18n.getHeader().setTitle(...)` throws `NullPointerException` in the view
  constructor. Because the view is instantiated during navigation, the failure
  surfaced only at runtime as the uncaught-exception `ErrorView` rendered inside
  the shell at `/login` — not at compile time and not in the browserless tests
  (which bypass the form POST and navigate authenticated).
- Workaround used: Construct the header explicitly —
  `var h = new LoginI18n.Header(); h.setTitle(...); i18n.setHeader(h);`.
- Evidence: `security/ui/LoginView.java`; server log
  `NullPointerException ... LoginI18n.getHeader() is null`.
- Impact: Low once known; a five-minute trap. Notable that no test layer caught
  it — the gap is that view *construction* on the real login path isn't
  exercised by the browserless view tests (which authenticate via
  `@WithUserDetails` and never render `LoginView`). Manual/Playwright run caught
  it; worth a smoke test that navigates `/login` anonymously.
- Suggested Vaadin/product improvement: `createDefault()` should populate
  `Header` like it does `Form`, or the getter should never return null for the
  "default" instance.
- Owner / next step: add an anonymous `/login` render smoke test if the login
  view grows more logic.

### F-011 — Form-stub principal must carry production-identical authorities (expected)
- Date: 2026-07-09
- Area: Vaadin/Security
- Severity: Low
- Task being attempted: Phase 1.1 (#9) — wiring the `local`/`test` form-stub
  login so its principal has the same authorities as the future Google OIDC
  path (the friction the issue explicitly flagged as expected).
- Expected vs actual: Anticipated (per the issue) that reconciling the two login
  paths onto one authority model would be fiddly. Actual: it was smooth once a
  shared `AppUserPrincipal` interface (id/email/name/roles) was introduced —
  both `AppUserDetails` (form-stub) and the eventual OIDC principal implement it,
  and `CurrentUserProvider` adapts either into the immutable `CurrentUser` record
  with no per-call DB query. Authorities derive from the *local* stored roles
  (`ROLE_USER`/`ROLE_ADMIN`), so `@RolesAllowed`/`@PreAuthorize` behave
  identically regardless of login mechanism. The only real subtlety: the
  form-stub has no per-user password (there is no password column) — every seeded
  user authenticates against one shared dev password, encoded once, which the
  `RoleHierarchy` bean then complements so an admin storing only `{ADMIN}` gains
  USER access.
- Workaround used: None needed; the `AppUserPrincipal` seam is the design, not a
  workaround.
- Evidence: `security/AppUserPrincipal.java`, `AppUserDetails.java`,
  `LocalUserDetailsService.java`, `CurrentUserProvider.java`,
  `MethodSecurityConfig.java` (RoleHierarchy); `MethodSecurityIntegrationTest`.
- Impact: Positive — the shared-principal seam means Phase 1.2 (Google OAuth)
  only has to build a second `AppUserPrincipal`; the dashboard, header, and
  `CurrentUser` accessor are unchanged.
- Suggested Vaadin/product improvement: none — this is Spring Security working as
  intended once the principal contract is explicit.
- Owner / next step: reuse `AppUserPrincipal` when the `OidcUserService` lands in
  Phase 1.2.

### F-012 — Vaadin route security and Spring method security read `@RolesAllowed` from different switches
- Date: 2026-07-10
- Area: Vaadin/Security
- Severity: Low
- Task being attempted: Phase 1.2 (#10) — wiring the two authorization layers so
  a stand-in privileged operation is guarded by
  `@RolesAllowed("ADMIN")` at both the route (view) and the service-method level
  (the friction the issue explicitly flagged as expected).
- Expected vs actual: Both layers can wear the *same*
  `jakarta.annotation.security.RolesAllowed` annotation, which reads cleanly —
  but they honour it through **independent** mechanisms, and this is a quiet
  trap. Vaadin's `VaadinSecurityConfigurer` navigation access control reads
  `@RolesAllowed` on views out of the box, so the ADMIN-only route was enforced
  immediately (a USER typing `/admin` is rejected; the auto-menu hides the
  entry). Spring **method** security, however, ignores JSR-250 by default:
  `@EnableMethodSecurity` turns on `@PreAuthorize` only. The pre-existing
  `MethodSecurityConfig` used `@EnableMethodSecurity` (bare) and the old
  placeholder test used `@PreAuthorize`, so nothing surfaced the gap — the moment
  the stand-in service switched to the issue-mandated `@RolesAllowed("ADMIN")`,
  a plain USER was **allowed** through and the method-security test failed. The
  method guard, the actual enforcement point, was silently a no-op.
- Workaround used: Set `@EnableMethodSecurity(jsr250Enabled = true)`. Now one
  annotation vocabulary spans both layers: route security for navigation UX,
  method security for real enforcement.
- Evidence: `security/MethodSecurityConfig.java`
  (`@EnableMethodSecurity(jsr250Enabled = true)`),
  `security/StandInPrivilegedService.java` (`@RolesAllowed`),
  `base/ui/AdminToolsView.java` (`@RolesAllowed` route);
  `MethodSecurityIntegrationTest` (USER-rejected / ADMIN-allowed / hierarchy
  passthrough), `AdminToolsViewUiTest` (route + auto-menu filtering). The
  initial red run showed `userMayNotCallAdminOperation` "Expecting code to raise
  a throwable".
- Impact: Two-layer authorization is real and verified. The catch generalises:
  every later phase using `@RolesAllowed` on a service method (approve/reject,
  user management, rate config) depends on `jsr250Enabled` staying on. If someone
  standardises on `@PreAuthorize` instead, JSR-250 can be turned back off — but
  the *route* views would keep working, so a flip is only caught by the method
  slice. That is exactly why the reusable method-security slice matters.
- Suggested Vaadin/product improvement: docs/starter guidance could call out that
  `@RolesAllowed` on a Vaadin view (route-enforced by default) and on a service
  method (needs `jsr250Enabled = true`) travel different paths — an easy source
  of a false sense of defense-in-depth.
- Owner / next step: real admin services in Phases 5/6 annotate their methods the
  same way and point the method-security slice at them, then delete the stand-in.

### F-013 — `--lumo-*` inline styles silently no-op under Aura, and the design-system guardrail that warned about it was deleted
- Date: 2026-07-10
- Area: Vaadin / Docs / Template
- Severity: Medium
- Task being attempted: Prototyping the Phase 2 report-detail line editor (issue
  #3, relates to F-004). Styling the card variants — borders, a selected-card
  highlight, container backgrounds, spacing — with `getStyle().set(...)`.
- Expected vs actual: Expected `getStyle().set("border", "var(--lumo-contrast-10pct)")`
  and friends to render as they always have in Vaadin. Actual: **nothing painted**
  — no border, background, padding, or radius — with no error, no warning, no
  devtools complaint. The inline `style` attribute was present in the DOM, but
  every `var(--lumo-*)` resolved to the empty string. `getComputedStyle(:root)`
  confirmed **all `--lumo-*` tokens are undefined**: this app runs **Aura**
  (Vaadin 25's default, `@StyleSheet(Aura.STYLESHEET)`), which defines
  `--vaadin-*` / `--aura-*` tokens instead. Aura and Lumo are separate,
  incompatible design systems; the Lumo names simply do not exist here.
- Why "Lumo was added" (investigation): **Lumo was never added.** There is no
  Lumo dependency, no Lumo stylesheet import, and `styles.css` is empty — the
  generated start.vaadin.com project has been Aura-only since commit `28ce50a`.
  The `--lumo-*` references are hand-written inline-style *strings*, introduced
  in `ecdba67` ("Phase 0.4 — **Aura** navigation shell & UX-state primitives")
  when `MainLayout` and `EmptyState` were authored. Root cause is **stale
  convention / muscle memory**: Lumo was Vaadin's default theme for ~7 years, so
  `--lumo-*` is the reflexive idiom in old docs, examples, and LLM training data,
  and it got reached for by habit. Aggravating factor: the generated
  `spec/design-system.md` **explicitly forbade it** — *"Do not use `--lumo-*`
  CSS variables … must not be mixed with Aura. Use `--aura-*` … and `--vaadin-*`"*
  — but that spec file was later **removed from the working tree**, so the
  guardrail was no longer in context when the base UI (and later code) was
  written. A real, correct guardrail existed and was dropped.
- Workaround used: Initially, a Lumo→Aura compatibility shim on the prototype
  view root that mapped each `--lumo-*` token to its Aura equivalent via CSS
  custom-property inheritance. That crutch has since been **removed** — every
  `var(--lumo-*)` in the codebase was replaced with its `--aura-*` / `--vaadin-*`
  equivalent (see the fixed mapping below), so no shim is needed.
- Evidence: `Application.java:12` (`@StyleSheet(Aura.STYLESHEET)`, no Lumo);
  empty `src/main/resources/META-INF/resources/styles.css`; `--lumo-*` usages in
  `base/ui/MainLayout.java:63-82` and `base/ui/EmptyState.java:35-44`;
  `getComputedStyle(document.documentElement)` returns `""` for every
  `--lumo-*` probed and real values for `--vaadin-background-container` /
  `--vaadin-border-color` / `--aura-accent-surface` / `--aura-font-size-*`;
  generated `spec/design-system.md` (commit `28ce50a`) line 12 forbidding Lumo;
  the file's later deletion (`git show 28ce50a:spec/design-system.md` exists,
  working tree does not); introducing commit `ecdba67`. Correct token names via
  Vaadin docs MCP `get_theme_css_properties theme=aura vaadin_version=25.2`.
- Impact: The existing base UI already ships these no-op styles — `MainLayout`
  (app-name font size, drawer/navbar spacing) and `EmptyState` (icon colour,
  heading size, secondary text colour) render with Aura defaults, not the
  intended tokens, today. It looks "fine" only because Aura's defaults are
  reasonable, which makes the bug invisible and self-perpetuating: every new
  hand-styled view is one habit away from the same silent no-op, and there is no
  build/lint signal.
- Suggested Vaadin/product improvement: (1) a dev-mode warning when a
  `var(--lumo-*)` is used while Aura is the active theme (undefined-token
  linting) would turn a silent failure into a visible one; (2) start.vaadin.com
  should keep `spec/design-system.md` (or fold its "Aura, not Lumo" rule into
  `CLAUDE.md`) so the guardrail survives; (3) docs/MCP could offer a Lumo→Aura
  token migration table.
- Owner / next step: **Resolved (2026-07-10).** All `var(--lumo-*)` CSS tokens
  removed across the codebase — base UI (`MainLayout`, `EmptyState`,
  `LoginView`) and the Phase 2 prototype variants — mapped to `--aura-*` /
  `--vaadin-*`; the prototype shim was deleted. The "Aura, never Lumo" rule is
  now in `CLAUDE.md`. Mapping applied: `--lumo-space-m`→`--vaadin-padding`,
  `--lumo-border-radius-l`→`--vaadin-radius-l`,
  `--lumo-contrast-{5,10}pct`→`--vaadin-background-container`/`--vaadin-border-color`,
  `--lumo-contrast-{30,50,90}pct`+`--lumo-secondary-text-color`→
  `--vaadin-text-color[-secondary]`, `--lumo-base-color`→`--aura-surface-color`,
  `--lumo-primary-color[-10pct]`→`--aura-accent-color`/`--aura-accent-surface`,
  `--lumo-box-shadow-m`→`--aura-shadow-m`,
  `--lumo-font-size-*`→`--aura-font-size-*`,
  `--lumo-error-*`→`--aura-red`/`--aura-red-text`.
  (`ButtonVariant.LUMO_*` Java enum constants are unaffected — they are the API,
  not CSS tokens.)

### F-014 — Driving OIDC provisioning without a real Google
- Date: 2026-07-10
- Area: Verification
- Severity: Low
- Task being attempted: Phase 1.3 (#11) — integration-testing the domain gate +
  claim/create policy through the real `OidcUserService`, on Testcontainers
  Postgres, without a live Google exchange (the friction the issue explicitly
  flagged as expected; a mock OIDC server is out of scope).
- Expected vs actual: `OidcUserService.loadUser(OidcUserRequest)` needs a real
  token exchange, so it can't be called directly in a test. Expected to need
  some seam to inject synthesized claims. Actual: extracting the policy into a
  separate `UserProvisioningService.provision(OidcUser)` (the thin
  `ProvisioningOidcUserService` adapter calls it after `super.loadUser`) made the
  whole gate/claim/create path drivable with a hand-built `DefaultOidcUser` — no
  HTTP, no mock server. The test synthesizes an `OidcIdToken` with
  `sub`/`email`/`email_verified`/`hd`/`name` claims and calls `provision`
  directly, reusing `AbstractIntegrationTest` so provisioning runs against real
  Flyway-migrated Postgres and the seeded bootstrap admin.
- Workaround used: Public, `@Transactional` `provision(OidcUser)` on a
  profile-agnostic `UserProvisioningService` (instantiable under `test`, wired
  into the filter chain only in `staging`/`prod`) so the slice autowires it
  directly. Covers claim-preserves-ADMIN, new-USER, wrong-domain, unverified,
  disabled, and no-re-sync. Bonus: the split also dodged a CGLIB proxy over
  `OidcUserService`'s `final` setters (see below).
- Evidence: `security/UserProvisioningService.java` (`provision`),
  `security/ProvisioningOidcUserService.java` (adapter),
  `OidcProvisioningIntegrationTest`.
- Impact: Full provisioning coverage with no mock-OIDC dependency, matching the
  issue's "out of scope unless regressions slip through" stance. The gap that
  remains untested is the token exchange itself (`super.loadUser`) and the
  end-to-end redirect/failure-handler wiring — accepted for V1.
- Suggested Vaadin/product improvement: a documented pattern for testing a custom
  `OidcUserService` (drive the post-fetch policy with a synthesized `OidcUser`)
  would save every team from rediscovering this seam.

### F-015 — "Set once" name vs a JPA `updatable=false` column
- Date: 2026-07-10
- Area: Spec
- Severity: Low
- Task being attempted: Phase 1.3 (#11) — claiming the pre-seeded admin row at
  first Google login must populate `sub` **and** replace the placeholder `name`
  ('Expense Admin') with the Google display name (ADR-0007), while `name` is
  still "set once".
- Expected vs actual: Phase 1.1 mapped `User.name` with `@Column(updatable =
  false)` to encode "set once". But claim is an UPDATE of an existing row, and
  Hibernate silently drops `updatable=false` columns from UPDATE statements — so
  the claim's new name would vanish with no error, leaving the placeholder.
- Workaround used: Dropped `updatable=false` from `name` and moved the "set once"
  guarantee into the domain: `User.claim(sub, name)` runs only while `sub` is
  null and provisioning never touches an already-linked (`sub`-present) row, so
  name is written exactly once (at provision or claim) despite the mutable
  column. `email` keeps `updatable=false` (it is the match key, never rewritten).
- Evidence: `user/User.java` (`claim` + `name` column comment),
  `OidcProvisioningIntegrationTest#doesNotResyncNameOrEmailOnSecondLogin`.
- Impact: "Set once" is now a code-level invariant with a guard that fails fast
  on a second claim, rather than a column constraint that silently no-ops. The
  lesson generalises: an immutability rule that has a legitimate one-time
  write-later (seed placeholder → real value) can't be expressed as
  `updatable=false`.
- Suggested Vaadin/product improvement: none — Hibernate behaviour is correct;
  this is a modelling note for the team.

### F-016 — `@Transactional` on an `OidcUserService` subclass ⇒ CGLIB warns on final methods
- Date: 2026-07-10
- Area: Tooling/Template
- Severity: Low
- Task being attempted: Phase 1.3 (#11) — running the provisioning transaction
  inside the custom `OidcUserService`.
- Expected vs actual: The first cut put `@Transactional` directly on a class
  `extends OidcUserService`. It worked, but on a `staging` boot Spring logged
  three `CglibAopProxy` WARNINGs: `OidcUserService`'s `final` setters
  (`setOauth2UserService`, `setClaimTypeConverterFactory`, `setRetrieveUserInfo`)
  "cannot get proxied via CGLIB". Transactional advice forces a CGLIB subclass
  proxy; final methods can't be overridden, so the framework warns. Benign here
  (those setters aren't re-invoked post-construction, and `loadUser` is non-final
  so the advice still applies) — but noisy and fragile.
- Workaround used: Split the transaction out to a plain `UserProvisioningService`
  (`@Transactional`, cleanly proxyable) and left `ProvisioningOidcUserService`
  as a thin, advice-free adapter that isn't proxied at all. Warnings gone; the
  provisioning policy is also now a first-class, directly-testable service.
- Evidence: `security/ProvisioningOidcUserService.java` (adapter),
  `security/UserProvisioningService.java` (transaction); the WARN lines appeared
  in a `staging`-profile boot before the split.
- Impact: Cleaner separation and no proxy noise. Generalises: don't hang Spring
  advice (`@Transactional`, `@Cacheable`, …) on a subclass of a framework class
  that has `final` methods — delegate to a plain collaborator instead.
- Suggested Vaadin/product improvement: none — this is Spring proxying, not
  Vaadin; worth a note in any Vaadin OAuth provisioning example that shows a
  custom `OidcUserService`.

### F-017 — `ButtonVariant.LUMO_*` has theme-agnostic replacements in Vaadin 25 (supersedes the F-013 "those stay" note)
- Date: 2026-07-10
- Area: Vaadin / Docs
- Severity: Low
- Task being attempted: Removing every Lumo reference from the app so only Aura
  is used — including the `ButtonVariant.LUMO_*` enum constants that F-013 and
  `CLAUDE.md` had said should stay as "the correct Java API".
- Expected vs actual: F-013 concluded the `LUMO_*` enum constants were unavoidable
  API (unlike the `--lumo-*` CSS tokens). Actual: the Vaadin 25.2 `ButtonVariant`
  enum now carries **theme-agnostic** constants — `PRIMARY`, `TERTIARY`, `ERROR`,
  `SUCCESS`, `WARNING`, `SMALL`, `LARGE` — alongside the legacy `LUMO_*` (and a
  few `AURA_*`) ones; the current Button docs use the bare names. Each bare
  constant emits the same `theme="…"` attribute as its `LUMO_` twin (verified by
  decompiling the enum), so the swap is behaviour-preserving. Caveat: three
  variants — `tertiary-inline`, `contrast`, `icon` — are **Lumo-only** (per the
  Button styling matrix, "Supported by: Lumo") and have no Aura rendering, so
  those usages were collapsed to plain `TERTIARY`.
- Workaround used: Replaced all `ButtonVariant.LUMO_*` with the theme-agnostic
  constants across `MainLayout` and the report prototypes; `LUMO_TERTIARY_INLINE`
  / `LUMO_CONTRAST` → `TERTIARY`. Also fixed two `var(--lumo-*)` inline styles the
  F-013 sweep missed because they landed later on the Phase 1.3 branch
  (`--lumo-error-text-color` → `--aura-red-text`, `--lumo-secondary-text-color` →
  `--vaadin-text-color-secondary`). Updated `CLAUDE.md` to prescribe the bare
  constants and flag the Lumo-only variants.
- Evidence: `base/ui/MainLayout.java`, `report/prototype/*.java`,
  `security/ui/LoginView.java`; `ButtonVariant` (25.2.1) decompilation showing
  bare + `LUMO_` + `AURA_` constants; Button styling doc variant-support matrix.
- Impact: The codebase is now Lumo-free in functional code (only historical
  finding/NOTES prose still names Lumo, to explain the rationale). Design tokens
  and theme variants are consistently Aura/theme-agnostic.
- Suggested Vaadin/product improvement: mark the `LUMO_*` `ButtonVariant`
  constants `@Deprecated` in favour of the theme-agnostic names, and have the
  styling docs state the bare names are the default so teams don't reach for
  `LUMO_*` out of habit.

### F-018 — `getTextRecursively()` doesn't render grid cells; use `GridTester.getCellText`
- Date: 2026-07-10 (revised 2026-07-10 after re-checking the API)
- Area: Verification
- Severity: Low → **not a product gap** (corrected below)
- Task being attempted: Phase 2.1 (#22) — a layer-3 view test asserting the
  ADMIN-only reference-data settings screens render the Flyway seed (VAT rates
  and expense types) in their `Grid`s, and driving the add/edit/reorder/deactivate
  actions through the grid's per-row action buttons.
- Expected vs actual: Expected `view.getElement().getTextRecursively()` to
  contain the seeded cell values (e.g. "25.5 %", "Restaurant/meals"), as it does
  for plain `H2`/`Paragraph` text. Actual: grid cell content is **absent** from
  the server-side element text — `Grid` rows stream via the data provider and are
  materialised through renderers, not as child elements — so the naive text
  assertion failed even though the grids were correctly populated.
- **Correction (original entry was wrong).** The initial workaround asserted the
  grid's loaded *model* (`$(Grid.class)` → `getGenericDataView().getItems()`) and
  proposed a missing-helper product improvement. Both were mistaken: the
  browserless tier **does** render grid cells, and the helper already exists.
  `test(grid)` / the `findGrid(T.class)` locator return a `GridTester` with
  `getCellText(row, col)` (rendered text, incl. `ComponentRenderer`/value
  providers), `getCellComponent(row, col)`, `size()`, and `getRow(int)`. The
  earlier claim that grid content is "model-only in this tier" was a
  `getTextRecursively()`-by-reflex trap, not a tier limitation.
- Resolution used: Assert rendered cells with
  `findGrid(VatRateDto.class).getCellText(row, col)`. For the per-row **action
  buttons** (Edit / Move / Deactivate) — which live inside a component column and
  so are *not* reachable from a UI-wide `findButton()` — scope the search to the
  cell: `getCellComponent(row, actionsCol)` then `find(Button.class, cell)`
  (helper `rowActionButton(..)` in `AbstractReferenceDataViewUiTest`). Editor
  dialog fields/buttons and the view header's Add button are in the normal tree
  and reachable directly via `findButton()` / `findBigDecimalField()` /
  `findComboBox(..)`.
- Evidence: `reference/ui/VatRateViewUiTest` and `ExpenseTypeViewUiTest`
  (17 green) drive full CRUD + active-options filtering and read every assertion
  from `getCellText` — no Playwright fallback needed for cell content.
- Impact: The real trap is only `getTextRecursively()`; the browserless grid API
  covers rendered cells and cell components fine. Two things that ARE tier-real:
  (1) component-column contents need cell-scoped `find`, not a UI-wide query;
  (2) `getTextRecursively()` still isn't a grid-reading tool — reach for the
  `GridTester` instead.
- Suggested Vaadin/product improvement: none — the helper exists and is
  documented (`flow/testing/browserless/component-testers`). Optional: a doc note
  that component-column children aren't matched by a UI-wide `find()`.
- Owner / next step: none — corrected pattern captured; reuse `getCellText` /
  `rowActionButton` for later grid-backed views (My Reports, approval queue).

### F-019 — HEIC receipts excluded; iPhone default format will bite users
- Date: 2026-07-10
- Area: UX-spec
- Severity: Medium
- Task being attempted: Choosing the allowed receipt content types (Phase 3,
  ADR-0021).
- Expected vs actual: The natural user gesture is "photograph the receipt with my
  phone." iPhones default to **HEIC**, which browsers cannot render inline and is
  awkward to handle server-side, so V1 accepts only JPEG/PNG/PDF (magic-byte
  verified). An iPhone user uploading a straight-from-camera photo will therefore
  be rejected until they change their capture format or convert.
- Workaround used: Exclude HEIC from the allow-list for V1; reject at upload with
  a clear message. Multi-image per line and "receipt required over a threshold"
  are likewise out of scope.
- Evidence: ADR-0021; magic-byte allow-list (JPEG `FF D8 FF` / PNG `89 50 4E 47`
  / PDF `25 50 44 46`).
- Impact: A common, non-obvious rejection for the most natural input path. Server-
  side HEIC→JPEG transcoding (or client-side capture-format guidance) is the
  likely follow-up if real usage hits it.
- Suggested Vaadin/product improvement: an upload component option to transcode or
  clearly flag HEIC would save every mobile-first app this same rejection.
- Owner / next step: revisit if the demo/real usage surfaces HEIC rejections;
  candidate for a transcoding step or a documented "shoot in JPEG" note.

### F-020 — `@WithUserDetails` invisible to a service that resolves the user via `AuthenticationContext`, once a browserless context has run
- Date: 2026-07-10
- Area: Verification
- Severity: Medium
- Task being attempted: Phase 2.2 (#23) — a layer-2 integration test for
  `ExpenseReportService`, which resolves the report owner through
  `CurrentUserProvider` → Vaadin's `AuthenticationContext`. Authenticated the
  test with `@WithUserDetails("user@vaadin.com")`, as the reference-data view
  tests do.
- Expected vs actual: In isolation the test passed. In the **full suite** it
  failed — first with "No authenticated user in the current security context"
  (`CurrentUserProvider.require()`), then, after a first fix attempt, with
  `AuthenticationCredentialsNotFoundException` from the `@RolesAllowed` method
  interceptor. The authentication set by `@WithUserDetails` was simply not
  visible to the service, but only when a browserless (`SpringBrowserlessTest`)
  context had booted earlier in the same JVM.
- Root cause: Vaadin installs a `VaadinAwareSecurityContextHolderStrategy` **both
  as a bean and as the global `SecurityContextHolder` static**. In a single-app
  process these are the same instance, so everything agrees. In a multi-context
  test JVM, **each** `@SpringBootTest` context installs its *own* strategy
  instance as the global static, last-boot-wins. Meanwhile Spring Security 6
  method security injects *this context's* `SecurityContextHolderStrategy`
  **bean**. So three readers can diverge: `@WithUserDetails`/`TestSecurityContextHolder`
  writes to one instance, the method interceptor reads this-context's bean, and
  `AuthenticationContext` reads the global static — different instances → the
  principal is invisible.
- Workaround used: Authenticate in a JUnit `@BeforeEach` (same lifecycle/thread
  as the body), and **pin the static to this context's bean** before writing:
  `SecurityContextHolder.setContextHolderStrategy(strategyBean)`, then build an
  `AppUserDetails` via `LocalUserDetailsService` and set it through that same
  bean. All three readers then agree, order-independently. (Browserless view
  tests are unaffected: they store the context in the `VaadinSession`, which the
  strategy reads first.)
- Evidence: `report/service/ExpenseReportServiceIntegrationTest` (6 green in the
  full 90-test suite, previously 5 errors + 1 failure only under suite ordering).
- Impact: Any **headless** (`@SpringBootTest`, non-browserless) test that drives
  code resolving the user via `AuthenticationContext`/`SecurityContextHolder`
  must not rely on `@WithUserDetails`/`@WithMockUser` alone — they can write to a
  strategy instance the app never reads once a browserless context has run.
  Production is unaffected (single context; bean == static).
- Suggested Vaadin/product improvement: a small test utility (or doc note) for
  authenticating headless integration tests against Vaadin's session-aware
  strategy — e.g. a `@WithVaadinUser` that writes through the active bean — would
  remove this footgun.
- Owner / next step: reuse the `@BeforeEach` strategy-pinning pattern for future
  owner-scoped service tests (approval queue, export).

### F-021 — Browserless `DatePickerTester.setValue` refuses an invalid (null-on-required) value
- Date: 2026-07-10
- Area: Verification
- Severity: Low
- Task being attempted: Phase 2.2 (#23) — a layer-3 test proving the report form
  keeps Save always enabled and shows a top-of-form error summary when the
  required report date is empty (ADR-0020, no disabled-submit gating).
- Expected vs actual: Expected `findDatePicker().setValue(null)` to model a user
  clearing the field. Actual: the tester threw `IllegalArgumentException: Given
  date is not a valid value` — by design it calls the field's default validator
  and refuses to *set* a value it considers invalid (null on a required picker),
  so the "cleared → validation fails → summary shows" path can't be driven
  through the tester.
- Workaround used: clear the value straight on the component —
  `$(DatePicker.class).first().clear()` — then click Save; the Binder's
  `asRequired` fires and the error summary renders. (Setting a *valid* date is
  fine through `findDatePicker().setValue(date)`.)
- Evidence: `report/ui/ReportDetailViewUiTest.missingRequiredDateShowsErrorSummaryAndPersistsNothing`.
- Impact: To test required-field validation UX at the browserless tier, reach
  past the typed tester to the component's `HasValue.clear()`/`setValue(null)` for
  the deliberately-invalid state; the tester is for valid user input only.
- Suggested Vaadin/product improvement: a tester affordance for "user cleared a
  required field" (e.g. `clear()` on the value testers) would let validation-path
  tests stay on the tester API.
- Owner / next step: none — pattern captured for later required-field forms.

### F-022 — ComboBox won't show a now-inactive historical option unless it's in the item set
- Date: 2026-07-13
- Area: Vaadin
- Severity: Low
- Task being attempted: Phase 2.3 (#24) — the line editor offers only *active*
  expense types / VAT rates for new lines (ADR-0018), but editing a historical
  line whose type or rate has since been deactivated must still display it.
- Expected vs actual: Expected setting the `ComboBox` value to the line's stored
  type/rate to render it even if it isn't among `setItems(...)`. Actual: a
  `ComboBox` value that isn't in the item set has no matching option to display —
  the field renders empty, silently dropping the historical classification.
- Workaround used: when opening the editor for an existing line, inject the
  line's type/rate into that ComboBox's item list (labelled "(inactive)") if the
  active list doesn't already contain it, then select that instance —
  `LineEditorDialog.withHistoricalType/withHistoricalRate`. The active-only lists
  still drive *new* lines.
- Evidence: `report/ui/LineEditorDialog`; ADR-0018 (rates preserved by `active`
  flag, never deleted).
- Impact: The active/inactive split needs the editor to reconcile the item set
  per opened line, not just call the "active options" service method once. Easy to
  miss — the failure is a silently blank field, not an error.
- Suggested Vaadin/product improvement: a `ComboBox` mode that renders an
  out-of-set current value (with the item-label generator applied) would remove
  this per-open reconciliation for history-preserving reference data.
- Owner / next step: none — pattern captured for the receipt/approval editors.

### F-023 — Signals (ListSignal + computed + bindText) fit live report totals cleanly
- Date: 2026-07-13
- Area: Vaadin
- Severity: Low
- Task being attempted: Phase 2.3 (#24) — live net/VAT/gross totals that update
  as lines are added/edited/removed (ADR-0015), replacing the prototype's
  imperative `refreshTotals()`.
- Expected vs actual: This was the smooth part. A `ListSignal<ExpenseLineDto>` of
  working lines drives both the card list (`VerticalLayout.bindChildren`) and the
  total bar (`Span.bindText(Signal.computed(() -> ...))`). Adding/removing a line
  (list-structure change) and editing one (`entry.set(...)`, per-entry change)
  both re-fire the computed totals with no manual wiring — exactly the reactive
  model ADR-0015 wanted, and the reason totals moved off the Grid footer (F-004).
- Workaround used: none needed. Two API notes worth recording: (1) `get()` may
  only be called inside a reactive context (effect/computed) — use `peek()` in
  click listeners and for the save snapshot, or it throws `IllegalStateException`;
  (2) `bindChildren` manages *all* default-slot children, so the empty-state
  placeholder must be a sibling of the bound container (with `bindVisible`), not
  a child of it.
- Evidence: `report/ui/ReportDetailView` (`lines` ListSignal, `currentTotals`,
  `totalsBar`, `card`).
- Impact: Confirms ADR-0015's Binder-for-validation / Signals-for-dynamic-state
  split works in practice; the get()/peek() context rule is the main thing to
  teach.
- Suggested Vaadin/product improvement: none — the docs cover it; the get()/peek()
  distinction could be more prominent given how easy it is to hit.
- Owner / next step: none — reuse this shape for other live-total surfaces
  (allowance calculator, export preview).

### F-024 — Optimistic-lock read-only guard can't be proven at the service layer inside a `@Transactional` (rollback) test
- Date: 2026-07-13
- Area: Testing
- Severity: Low
- Task being attempted: Phase 2.4 (#25) — a layer-2 service test asserting a
  `SUBMITTED` report rejects a whole-aggregate `update()` with the aggregate's
  editable guard (`IllegalStateException`), passing the version the caller last
  saw.
- Expected vs actual: expected `update(id, dto, submittedVersion)` to reach the
  domain `reconcileLines`/`updateDetails` guard and throw `IllegalStateException`.
  Actual: it threw `ObjectOptimisticLockingFailureException` first. In a
  `@Transactional` (rollback) test every service call joins the *same*
  persistence context; the preceding `submit()` leaves the entity dirty, and the
  next `@Transactional` service method auto-flushes before its query — bumping
  `@Version` in the shared context — so the version the test captured from
  `submit()`'s returned DTO (read *before* that flush) is already stale by the
  time `update()` runs its explicit version check. The service's version guard
  fires before the domain guard is ever reached.
- Workaround used: keep the service-layer test to the *version-independent* path
  (a `SUBMITTED` report rejects `delete()` — no version arg, so it always reaches
  `assertDeletable()`), and prove the edit-once-submitted guard where it belongs:
  the domain unit test (`ExpenseReportTest`, layer 1, no persistence context to
  drift) and the view test (no Save/Add/Delete offered once `SUBMITTED`). The
  genuine optimistic-lock conflict lives in its own committed-state class
  (`ExpenseReportOptimisticLockIntegrationTest`, non-`@Transactional`, ADR-0011).
- Evidence: `report/service/ExpenseReportServiceIntegrationTest`
  (`aSubmittedReportCannotBeDeleted`), `report/domain/ExpenseReportTest`
  (`aSubmittedReportRejectsLineAndDetailEditsAndDelete`).
- Impact: reinforces ADR-0012's layering — version/flush semantics make the
  rollback slice a poor place to assert state-machine guards that sit *behind* a
  version check; push those to layer 1, and use the committed-state exception
  only for the lock behaviour itself.
- Suggested Vaadin/product improvement: none — this is a JPA/`@Transactional`
  test-isolation nuance, not a framework gap.
- Owner / next step: none — pattern captured; apply the same split to the Phase 5
  approve/reject guards.

### F-025 — Buffered receipt bytes live on the Vaadin session heap until save
- Date: 2026-07-13
- Area: Deployment/Observability
- Severity: Low
- Task being attempted: Phase 3.1 (#40) — attaching a receipt on a report at any
  time, including a brand-new never-saved one, with the bytes persisted only on
  the whole-aggregate `create`/`update` (ADR-0021, overriding ADR-0019's
  "save first").
- Expected vs actual: expected (and got) a smooth "attach then save" flow with no
  disabled control. The structural cost, as ADR-0021 called out: between attach
  and save the received `byte[]` is held in the detail view's `pendingReceipts`
  map on the Vaadin session (server heap), not the DB. Bounded by the 10 MB cap ×
  lines with a pending upload per open editor session, but it is real per-user
  heap that a page reload or session timeout silently discards (the Upload
  component keeps no server-side file state across a reload either).
- Workaround used: none needed for V1 — the cap bounds it and a single report has
  few lines. Kept the bytes off the DTOs entirely: `ExpenseLineDto` carries only
  a receipt *summary*, and buffered bytes ride a separate save-only `ReceiptUpload`
  channel keyed by working-line entry, so the heap footprint is visible and local.
- Evidence: `report/ui/ReportDetailView` (`pendingReceipts`,
  `pendingReceiptsByLineIndex`), `report/ui/LineEditorDialog` (`pendingData`),
  `report/service/ReceiptUpload`, ADR-0021 Consequences.
- Impact: at real concurrency (many users each mid-edit with a large pending
  receipt) this is heap pressure to watch; the migration path is a staging store
  (temp file / object storage) for buffered bytes, deferred with ADR-0009's
  object-storage move.
- Suggested Vaadin/product improvement: a first-class "buffer to temp file, not
  heap" `UploadHandler` (there is `TemporaryFileUploadHandler`, but wiring its
  lifecycle to an unsaved-form working copy is manual) would make the safe choice
  the easy one.
- Owner / next step: none — logged per the acceptance criteria; revisit with the
  object-storage decision if heap pressure shows up in staging.

### F-026 — Vaadin `UploadHandler` + `maxFiles(1)` leaves the upload button disabled; legacy `SucceededEvent` doesn't fire
- Date: 2026-07-13
- Area: Vaadin
- Severity: Low
- Task being attempted: Phase 3.1 (#40) — an always-enabled receipt upload that
  is replaceable (overwrite), i.e. still usable after one file (ADR-0020: never a
  disabled control).
- Expected vs actual: with `setMaxFiles(1)`, after one successful upload the
  component's built-in file list keeps the file and the upload button goes
  `disabled` (max-files reached) — a gated control, and a second display of the
  filename redundant with our own status row. First fix attempt wired
  `addSucceededListener(e -> clearFileList())` to reset the count; it never fired.
  Under the new `UploadHandler` streaming API the upload lifecycle is delivered
  through the `TransferProgressListener` model, and the legacy `SucceededEvent`
  (and `FinishedEvent`) are not raised — so a handler-based Upload can't rely on
  them for post-upload cleanup.
- Workaround used: call `upload.clearFileList()` at the end of the
  `UploadHandler.inMemory` callback itself (it runs on the UI thread on
  completion). The native list is then always empty, our status row ("📎 filename
  · Remove") is the single source of truth, and the button stays enabled for a
  replacement. Verified live via Playwright (button enabled, no redundant list).
- Evidence: `report/ui/LineEditorDialog` (`uploadRef`, `clearFileList()` inside
  the inMemory callback); Vaadin 25.2 upload docs (progress-listener note); the
  disabled-button screenshot from the first run.
- Impact: a subtle trap — the intuitive `addSucceededListener` compiles and reads
  correctly but is dead code with `UploadHandler`; and `maxFiles(1)` quietly
  fights the "never disable" rule. Cost us one build/restart/verify cycle.
- Suggested Vaadin/product improvement: either fire the legacy succeeded/finished
  events from the `UploadHandler` path too, or deprecate them loudly; and document
  that `maxFiles(1)` disables the add button (with the clear-to-replace pattern) on
  the Upload page.
- Owner / next step: none — pattern captured; reuse the callback-`clearFileList()`
  shape for any future single-file upload.

### F-027 — Vaadin 25 `Dialog` overlay is styled via `vaadin-dialog::part(overlay)`, not `vaadin-dialog-overlay`
- Date: 2026-07-13
- Area: Vaadin
- Severity: Low
- Task being attempted: Phase 3.2 (#41) — the receipt preview dialog degrades to
  a full-screen sheet on a phone-sized viewport (ADR-0020), styled from the app's
  global `styles.css`.
- Expected vs actual: Pre-25 guidance (and much of the web) says a `Dialog`'s
  overlay is a top-level `<vaadin-dialog-overlay>` element carrying the dialog's
  class, targetable as `vaadin-dialog-overlay.my-class::part(overlay)`. In Vaadin
  25 that element does not exist at the document top level: `Dialog#addClassName`
  puts the class on the `<vaadin-dialog>` host, and the overlay is rendered
  *inside that host's shadow root* (`<vaadin-dialog-overlay id="overlay"
  exportparts="backdrop, overlay, header, …">`). The old selector matched nothing
  and the media query silently did nothing.
- Workaround used: target the exported part on the host directly —
  `vaadin-dialog.receipt-preview-dialog::part(overlay)`. Confirmed live: the
  overlay fills the 360-px viewport (100vw/100vh, radius 0) under the media query
  and stays a centered `min(92vw, 52rem)` box on desktop.
- Evidence: `resources/META-INF/resources/styles.css`; DOM probe of the dialog
  shadow root (`exportparts` list) via Playwright; `report/ui/ReceiptPreview`
  (`addClassName("receipt-preview-dialog")`).
- Impact: a stale-but-plausible styling recipe that fails silently (no error, no
  visual change) — easy to lose time to. Cost one probe/fix/verify cycle.
- Suggested Vaadin/product improvement: document the `vaadin-dialog::part(...)`
  hooks (overlay/header/content/footer via `exportparts`) on the Dialog styling
  page, and call out the change from the `vaadin-dialog-overlay` top-level element.
- Owner / next step: none — reuse the `::part(overlay)` selector for any future
  responsive dialog.

### F-028 — A clickable component nested in a clickable card fires both listeners (needs explicit stopPropagation)
- Date: 2026-07-13
- Area: Vaadin
- Severity: Low
- Task being attempted: Phase 3.2 (#41) — a receipt thumbnail on a line card that
  enlarges the image on click, where the whole card is itself clickable to open
  the line editor (variant-C detail editor).
- Expected vs actual: Expected clicking the thumbnail button to only open the
  preview. Actual: the DOM `click` bubbled from the button to the card body's
  click listener, so on an editable report the thumbnail *also* opened the line
  editor (the editor won, hiding the preview). Vaadin `ClickNotifier` registers a
  DOM listener per component and relies on native bubbling — there is no
  server-side "stop the parent listener" for a nested `ClickEvent`.
- Workaround used: on the preview affordance, add a client-side listener that
  stops propagation —
  `getElement().executeJs("this.addEventListener('click', e => e.stopPropagation())")`.
  The button's own listener still runs (stopPropagation doesn't cancel the
  element's own handlers), only the ancestor card is spared. Verified live: the
  card thumbnail now opens the preview, and the rest of the card still opens the
  editor. (The existing trash button sidesteps this by living *outside* the
  clickable body — the other valid pattern.)
- Evidence: `report/ui/ReceiptPreview#forReceipt`; `report/ui/ReportDetailView`
  (card body click → `openEditor`); first-run screenshot showing the editor
  opening on a thumbnail click.
- Impact: an easy-to-miss interaction bug that automated view tests (which assert
  presence, not click routing) don't catch — found only in manual Playwright
  verification. Reinforces that nested interactive elements need either isolation
  or explicit propagation control.
- Suggested Vaadin/product improvement: a helper on `ClickNotifier` (e.g.
  `addClickListener(listener).stopPropagation()`) would make this a one-liner
  without hand-written JS.
- Owner / next step: none — pattern captured for future nested-click affordances.

### F-029 — The Lumo `badge` *theme* is unstyled under Aura — use the official Badge component (25.1+)
- Date: 2026-07-13
- Area: Vaadin
- Severity: Low
- Task being attempted: importing the "Expense Reports" Claude Design mockup and
  re-skinning the report list + detail to it (status badges, grouped card list,
  status callouts) while keeping the domain unchanged.
- Expected vs actual: Expected `Span` + `theme="badge primary/success/error"`
  (the standard Vaadin badge recipe) to render a coloured pill. Actual: under the
  Aura theme it renders as **plain, unstyled text** — no background, no radius,
  no colour — because the `badge` style module ships with Lumo, not Aura. No
  error, no console warning; caught only in live Playwright verification (the
  first list screenshot showed "Draft"/"Submitted" as bare text). This is the
  badge-level analogue of F-013/F-017 (Lumo-only *button* variants under Aura).
- Resolution: use the **official `com.vaadin.flow.component.badge.Badge`**
  component (added in Vaadin 25.1, `vaadin-badge-flow`), which *is* styled under
  Aura — not the `theme="badge …"` span, and not a hand-styled span (the initial
  workaround, since replaced). Map with `BadgeVariant`: `SUCCESS` (approved),
  `ERROR` (rejected), `FILLED` (submitted — Aura has no accent/primary variant
  and `CONTRAST` is Lumo-only, so a filled neutral reads as "handed off"), plain
  default (draft), all `SMALL`. The label text always renders, so status is never
  colour-only (ADR-0020). See `report/ui/ReportViewSupport#statusBadge`.
- Evidence: `report/ui/ReportViewSupport`; Badge Java API + styling docs
  (variant support table: `filled/success/warning/error/small/dot` under Aura,
  `contrast` Lumo-only); report UI tests green.
- Impact: another silent Lumo-vs-Aura styling trap. The design mockup's own
  `_ds` tokens (`--aura-text-xs`, `--aura-user-0`, …) are the design project's
  scheme and do **not** match the app's real Aura/`--vaadin-*` tokens either, so
  the whole mockup had to be translated token-by-token, not copied.
- Suggested Vaadin/product improvement: either provide an Aura badge style module
  (so `theme="badge …"` works across themes) or document on the Badge page that
  it is Lumo-only and give the Aura-token recipe.
- Owner / next step: none — reuse `ReportViewSupport#statusBadge` for any future
  status pill.

### F-030 — `--vaadin-padding` / `--vaadin-gap` don't exist (only sized `-xs…-xl` variants)
- Date: 2026-07-13
- Area: Vaadin
- Severity: Medium
- Task being attempted: re-skinning the report list/detail cards to the mockup —
  giving raw `Div`/`RouterLink` cards their own padding and inter-card gaps.
- Expected vs actual: Expected `var(--vaadin-padding)` and `var(--vaadin-gap)` to
  be valid base-style tokens (they read like the obvious names, and pre-existing
  code — `base/ui/MainLayout`, the original totals bar — already used them).
  Actual: the base styles define **only the sized scale** —
  `--vaadin-padding-{xs,s,m,l,xl}` and `--vaadin-gap-{xs,s,m,l,xl}` (plus
  `--vaadin-radius-{s,m,l}`); there is **no unsuffixed `--vaadin-padding` or
  `--vaadin-gap`. An undefined custom property with no fallback resolves to
  nothing, so `padding: var(--vaadin-padding)` silently applies **zero padding** —
  no error, no warning. On Vaadin components it was masked by their own built-in
  padding; on the new hand-built cards it showed as a cramped, unpadded layout
  (spotted by JC against the mockup).
- Workaround used: use the sized tokens — `--vaadin-padding-l` for the roomy card
  interiors, `--vaadin-padding-m` for standard spacing, `--vaadin-gap-m` between
  cards. Fixed every occurrence in `report/ui/*` and the two pre-existing ones in
  `base/ui/MainLayout`. Confirmed via `get_theme_css_properties theme=base`.
- Evidence: `MainLayout` (2), `MyReportsView`, `ReportDetailView`,
  `LineEditorDialog`; base-theme MCP token listing; cramped-card screenshot.
- Impact: a silent no-op that reads like working code and copy-propagates (the
  bad token was already in the template/earlier phases). Always prefer a fallback
  (`var(--token, 1rem)`) or the documented sized token.
- Suggested Vaadin/product improvement: either alias unsuffixed
  `--vaadin-padding`/`--vaadin-gap` to the medium step, or have the dev-mode
  theme linter warn on references to undefined `--vaadin-*` custom properties.
- Owner / next step: none — tokens corrected app-wide.

### F-031 — Theme switcher hand-rolled raw `executeJs`/`localStorage` instead of the idiomatic Flow APIs
- Date: 2026-07-14
- Area: AI
- Severity: Medium
- Task being attempted: the original theme switcher (PR #46) — apply a light/dark/
  system colour scheme live and persist it per-browser.
- Expected vs actual: Expected the first cut to reach for the framework APIs that
  cover exactly this. Actual: it hand-wrote JavaScript strings —
  `getElement().executeJs("document.documentElement.style.colorScheme=$0; …")` to
  flip the scheme and `executeJs("localStorage.setItem/getItem…")` to persist —
  when Flow 25 already ships **`Page.setColorScheme(ColorScheme.Value)`** (since
  25.0; does the same `<html>` `color-scheme` manipulation, plus a `theme`
  attribute) and **`WebStorage`** (typed `setItem`/`removeItem`/`getItem`) for
  localStorage. The raw-JS version worked, so nothing flagged it; it just
  shouldn't have been written that way. Simplified in PR #47.
- Workaround used: rewrote `ThemeSwitcher` on `Page.setColorScheme` + `WebStorage`,
  keying the choice map on `ColorScheme.Value` (`NORMAL`/`LIGHT`/`DARK`). The one
  irreducible bit of JS — the pre-paint bootstrap in `Application#configurePage` —
  legitimately stays: it must run synchronously before first paint and the server
  can't read client `localStorage` before the initial HTML is sent, so no Java API
  can express it.
- Evidence: `base/ui/ThemeSwitcher` (before/after in PRs #46 → #47);
  `Page#setColorScheme` (flow-server 25.2.1 source); `WebStorage` docs.
- Impact: `executeJs` string-slinging is the class of code that bypasses the
  framework, dodges type-checking, and copy-propagates as a bad example. When a
  task is "set a browser/DOM property" or "read/write web storage", check for a
  first-party Flow API (`Page.*`, `WebStorage`, `getThemeList`, `getStyle`) before
  writing JS. Prefer the idiomatic API from the first cut, not as a follow-up.
- Suggested Vaadin/product improvement: cross-link the colour-scheme docs and the
  `Page.setColorScheme`/`WebStorage` APIs from the Aura light/dark page, so the
  idiomatic path is discoverable at the point of need.
- Owner / next step: none — `ThemeSwitcher` now uses the idiomatic APIs.

### F-032 — Allowance rate seed values are provisional (verify against Verohallinto)
- Date: 2026-07-14
- Area: Spec
- Severity: Medium
- Task being attempted: standing up the Phase 4.1/4.4 allowance rate config
  (issue #48) — the per-year reference data the Travel Calculator will cost
  against — seeded from the "Verohallinto 2026 decision".
- Expected vs actual: Expected an authoritative, confirmed 2026 rate table to
  seed from. Actual: the PRD carries **provisional** domestic/km/meal figures
  (full €54 / partial €25 / meal €13.50 / km €0.59, thresholds 10 h / 6 h) and
  no confirmed foreign per-diem table, so the V7 foreign seed is a
  representative ~12-country **starter sample** at plausible amounts, not the
  real decision. The domain memory flags Finnish statutory rates as
  trust-the-user / verify-before-shipping.
- Workaround used: seeded the provisional values, modelled history **by year**
  (not by an `active` flag — the deliberate contrast with ADR-0018) so a
  corrected year is just a new/edited row that never mutates a prior year, and
  gave the admin a runtime settings screen (`AllowanceRatesView`) to fix any
  figure and add a country/year. The caveat is repeated in the V7 migration
  header comment and the entity Javadoc.
- Evidence: `db/migration/V7__allowance_rates.sql` (header + seed),
  `allowance/AllowanceRateService`, `allowance/ui/AllowanceRatesView`;
  [[user-finnish-tax-domain]] memory.
- Impact: until an admin verifies each year's figures against the published
  Verohallinto decision, calculator output (Phase 4.2) is indicative only. The
  by-year model means verification is a data task, not a code change.
- Suggested Vaadin/product improvement: none (domain/data caveat). Product
  next step: confirm the 2026 foreign per-diem table before the calculator
  slice (#49) ships user-visible allowance amounts.
- Owner / next step: admin verifies 2026 figures; the config screen is the fix
  surface.

### F-033 — Browserless `ComboBoxLocator` has no `getValue()`; the selection getter is `getSelected()`
- Date: 2026-07-14
- Area: Verification
- Severity: Low
- Task being attempted: asserting the selected year in the allowance-rate view
  test (`findComboBox(Integer.class)` then read the current value).
- Expected vs actual: Expected the browserless `ComboBoxLocator` to expose a
  `getValue()` mirroring `HasValue#getValue()` (the field locators read as if
  they proxy the component). Actual: it exposes `getSelected()` (and
  `getSuggestions()`/`selectItem(String)`); `getValue()` does not compile. The
  locator is a *tester*, not the component, so its API is its own vocabulary.
- Workaround used: `findComboBox(Integer.class).getSelected()`.
- Evidence: `allowance/ui/AllowanceRatesViewUiTest`;
  `com.vaadin.flow.component.combobox.ComboBoxLocator` (browserless-test-shared
  sources).
- Impact: a small compile-time speed bump; the error message
  ("cannot find symbol getValue()") points the right way once you read the
  locator source.
- Suggested Vaadin/product improvement: either add a `getValue()` alias on the
  value-bearing locators or surface the locator method list in the browserless
  testing docs so the tester vocabulary is discoverable without reading jars.
- Recurrence (2026-07-27, issue #122): the same gap on `BigDecimalFieldLocator`
  and `DialogLocator` — the former has `setValue`/`clear` but no `getValue`, the
  latter no `getElement`. Read through the component instead:
  `findBigDecimalField().withLabel("Quantity").getComponent().getValue()`.
- Owner / next step: none.

### F-034 — Generated per-diem line is coupled to reference data by name/value, with no first-class marker
- Date: 2026-07-14
- Area: Spec
- Severity: Medium
- Task being attempted: generating the read-only 0 %-VAT per-diem line from a
  Travel (#49). Every `expense_line` row needs a non-null `expense_type_id` and
  `vat_rate_id` (V5), so the generated line must reference a real reference-data
  type + rate — but there is no first-class way to say "this is *the* per-diem
  type / the 0 % rate".
- Expected vs actual: expected a stable, semantic hook (e.g. a `system`/`kind`
  marker on `ExpenseType`/`VatRate`, or a dedicated allowance category). Actual:
  the service resolves the type by the literal name `"Travel allowance"`
  (`findFirstByNameIgnoreCaseAndActiveTrue…`) and the rate by value `0.00`
  (`findFirstByValueAndActiveTrue…`). Both are admin-editable (ADR-0018), so a
  rename or deactivation of that seed row breaks per-diem generation with a
  runtime `IllegalStateException` at save time.
- Workaround used: constant `PER_DIEM_EXPENSE_TYPE = "Travel allowance"` +
  `ZERO_VAT` in `ExpenseReportService`, resolved unfiltered-by-name; a clear
  `IllegalStateException` if the row is missing so the failure is legible.
- Evidence: `report/service/ExpenseReportService` (`perDiemExpenseType()`,
  `zeroVatRate()`), `reference/ExpenseTypeRepository`,
  `reference/VatRateRepository`; seed in `db/migration/V3__reference_data.sql`.
- Impact: a hidden coupling between admin-editable reference data and allowance
  generation. Low blast radius today (the seed exists and tests cover the happy
  path), but an admin action can break a core flow with no compile-time signal.
- Suggested Vaadin/product improvement: none (domain-modelling gap, not a
  framework one). Product next step: add a `system`/`purpose` marker to the
  reference tables (Phase 4.4/5) so allowance lines bind to a role, not a label.
- Owner / next step: revisit when the approval/export slices need to recognise
  allowance lines by kind rather than by expense-type name.

### F-035 — `Signal.computed` throws `MissingSignalUsageException` when a `&&` short-circuits the signal read away
- Date: 2026-07-14
- Area: Vaadin
- Severity: Low
- Task being attempted: binding an empty-state's visibility to "no manual lines
  *and* the report is editable" — `Signal.computed(() -> editable && lines.get().isEmpty())`.
- Expected vs actual: expected the computed to evaluate normally. Actual: when
  `editable` was `false`, Java short-circuited the `&&` and never called
  `lines.get()`, so the effect read *zero* signals and Vaadin threw
  `MissingSignalUsageException: Effect action must read at least one signal
  value.` — surfacing as an `ErrorView` on a read-only report, not a validation
  message.
- Workaround used: read a signal unconditionally first
  (`lines.get().isEmpty() && editableSignal.get()`), and promote the plain
  `editable` boolean to a `ValueSignal<Boolean>` so the computed is both
  side-effect-safe *and* re-runs when editability flips on (re)load (a plain
  field wouldn't trigger recomputation).
- Evidence: `report/ui/ReportDetailView` (`editableSignal`, `linesSection()`);
  failing then passing `ReportDetailViewUiTest.aSubmittedReportShowsTheTripBut…`.
- Impact: an easy trap — a perfectly reasonable boolean guard placed *before* the
  signal read turns a computed into a crash, and only on the branch that
  short-circuits (so it can pass in draft and fail read-only). The runtime
  message is good once you see it, but the failure mode (whole-view error) is
  disproportionate.
- Suggested Vaadin/product improvement: either treat "no signal read this run"
  as a stable/`false` computed rather than an exception, or document the
  short-circuit hazard in the Signals guide with this exact example.
- Owner / next step: none.

### F-036 — Browserless: dialog/overlay text is not under `getCurrentView()`, and `getTextRecursively()` includes hidden elements
- Date: 2026-07-14
- Area: Verification
- Severity: Low
- Task being attempted: asserting the trip dialog's computed per-diem preview
  and that a hidden empty-state is not shown, in `ReportDetailViewUiTest`.
- Expected vs actual: (1) `getCurrentView().getElement().getTextRecursively()`
  did **not** contain the open dialog's text — a `Dialog` overlay attaches to the
  `UI`, not the routed view — so preview/error assertions saw nothing. (2)
  `getTextRecursively()` **does** include `setVisible(false)` elements (visibility
  is a client concern; the server element tree still carries them), so a
  `doesNotContain(...)` assertion can't prove something is hidden.
- Workaround used: assert open-dialog text against
  `UI.getCurrent().getElement().getTextRecursively()`; assert a component is
  hidden via the locator instead (`findSpan().withText(...).exists()` is `false`
  for an invisible span, mirroring how the hidden-button tests use `.exists()`).
- Evidence: `report/ui/ReportDetailViewUiTest`
  (`insertingADomesticTrip…`, `aSubmittedReportShowsTheTrip…`).
- Impact: two non-obvious verification pitfalls; each cost one red run to
  diagnose. The locator's "invisible ⇒ absent" rule is the reliable primitive
  for both existence and visibility assertions.
- Suggested Vaadin/product improvement: note in the browserless docs that
  overlays live under the `UI` (not the view) and that text-tree assertions
  ignore visibility, pointing to the locator `.exists()` idiom for visibility.
- Owner / next step: none.

### F-037 — Slice 2's one-line-per-travel model didn't generalise to four routed outputs
- Date: 2026-07-14
- Area: Spec
- Severity: Medium
- Task being attempted: Phase 4.3 (#50) — extending the Travel Calculator so one
  trip generates up to four read-only lines (per-diem, kilometre, meal, parking)
  instead of the single per-diem line Slice 2 (#49) built.
- Expected vs actual: expected the Slice 2 machinery to extend by adding rules.
  Actual: two of its foundations assumed *one* generated line per travel and had
  to be reshaped. (1) A generated line was identified only by `travel != null`,
  and the aggregate matched existing lines with `Collectors.toMap(getTravel, …)`
  — a one-to-one map that throws on a second line per travel. (2) The totals split
  routed purely on `isGenerated()` (generated ⇒ tax-free per-diem subtotal), but
  parking is *generated **and** VAT-bearing*, so it must land in Net/VAT, not a
  tax-free row — the boolean couldn't express that. `TravelSpec`/`TravelDto` also
  carried flat `perDiem*` fields with no room for the other three outputs.
- Workaround used: added a first-class `GeneratedLineKind` discriminator
  (`PER_DIEM`/`KILOMETRE`/`MEAL`/`PARKING`, with `isTaxFreeAllowance()`) on
  `ExpenseLine`; reconciliation now keys on `(travel, kind)`; totals route by kind
  (`countsInNetVat()` folds parking into Net/VAT, three `allowanceTotal(kind)`
  subtotals for the tax-free ones). `TravelSpec` now carries a
  `List<GeneratedLineSpec>` and `TravelDto` a `TravelAllowances` breakdown record.
- Evidence: `report/domain/GeneratedLineKind`, `ExpenseLine.getGeneratedKind()`,
  `ExpenseReport.regenerateGeneratedLines`/`totals`/`perDiemTotal`+`kilometreTotal`
  +`mealTotal`; `report/service/TravelAllowances`, `ExpenseReportService.toTravelSpec`.
- Impact: a moderate but clean refactor confined to the aggregate + service +
  travel DTOs; the manual-line path was untouched. Directly delivers the "next
  step" F-034 flagged — allowance lines are now recognised by a semantic kind, not
  by expense-type name — so the Phase-5 approval/export slices can group them by
  role. The name-based *reference-data* coupling (F-034) still stands; the four
  types are still resolved by literal name in the service.
- Suggested Vaadin/product improvement: none (domain-modelling shape, not a
  framework gap).
- Owner / next step: when the foreign-trip slice (Slice 4) lands, the same
  `GeneratedLineSpec` list absorbs a foreign per-diem kind with no further reshape.

### F-038 — Receipts on generated lines: a shared persistence context resurrects the cascade-deleted receipt in a rollback test
- Date: 2026-07-14
- Area: Testing
- Severity: Low
- Task being attempted: letting a receipt be attached to a travel-generated line
  (per-diem/kilometre/meal/parking) and verifying that clearing an input (e.g.
  parking fee → 0) orphan-removes the line and its receipt.
- Expected vs actual: in production this is clean — the receipt table's FK is
  {@code ON DELETE CASCADE} (V6) and receipts are not part of the aggregate, so an
  {@code update()} loads the report without the receipt in its session, removes the
  line, and the DB cascades the receipt away. But the layer-2 test is
  {@code @Transactional} (rollback), so {@code create()} and {@code update()} share
  one Hibernate session: the just-created {@code Receipt} lingered, still pointing at
  the now-orphan-removed {@code ExpenseLine}, and the flush threw
  {@code TransientPropertyValueException} ("references an unsaved transient instance").
- Workaround used: {@code entityManager.clear()} between the create and the update
  in the test, modelling the separate-request reality (the same trick
  {@code deletingATripRemovesItsGeneratedLine} already uses). Production code is
  unchanged and correct.
- Evidence: {@code ExpenseReportServiceIntegrationTest.removingAGeneratedLineKindCascadesItsReceipt};
  {@code ExpenseReportService.applyTravelReceipts}; V6 {@code receipt} FK cascade.
- Impact: a test-only artifact of the shared-session pattern, but a sharp one — the
  exception points at "persist the transient instance", which misleads toward a
  production bug that isn't there. The tell is that it only fires when a
  receipt-carrying line is removed within the same transaction it was created in.
- Suggested Vaadin/product improvement: none (JPA session semantics).
- Owner / next step: none.

### F-039 — A year-dependent ComboBox and `Binder.readBean` fight over ordering
- Date: 2026-07-14
- Area: Vaadin/Flow
- Severity: Low
- Task being attempted: adding the destination-country picker to the trip dialog
  (Phase 4.2, #51). The list of countries is *year-dependent* — it must show only
  the countries with a foreign per-diem rate for the **trip's year**, and the year
  comes from the departure `DateTimePicker` in the same form.
- Expected vs actual: expected to just `setItems(...)` once and bind. Actual: a
  `ComboBox`'s `setValue` only preselects when the value is already among its
  items, so an *edited* foreign trip whose stored country must be re-selected needs
  the item list populated **before** `binder.readBean(model)` runs — but the
  departure field's value (which decides the year) isn't set until `readBean` runs.
  Chicken-and-egg: populate-from-field is too late, and `setItems` itself clears the
  current selection.
- Workaround used: drive the initial population from the *model's* departure
  (`model.getDepartureAt()`), not the field, so it works before `readBean`; refresh
  the list on every departure change; and capture/restore the current selection
  around `setItems` so a still-valid country survives a re-populate. Registered the
  refresh before `readBean` (the same ordering the existing preview listeners rely
  on).
- Evidence: `report/ui/TravelEditorDialog` (`refreshCountries`, the pre-`readBean`
  call, the departure listener); `ReportDetailViewUiTest.insertingAForeignTrip...`.
- Impact: a small but non-obvious wrinkle whenever a bound selection field's *items*
  depend on another field in the same bean — easy to ship a dialog that silently
  drops the edited value on open. The tell is "the combo opens empty only when
  editing".
- Suggested Vaadin/product improvement: a `Binder` hook (or `ComboBox` option) to
  supply items lazily from the bean at `readBean` time would remove the ordering
  dance; today the caller must sequence it by hand.
- Owner / next step: none — the pattern is contained in the dialog. A later slice
  that lets the per-diem year differ from the departure year would revisit it.

### F-040 — A `@Transactional` browserless view test with `@WithUserDetails` renders `LoginView` once several browserless classes have run
- Date: 2026-07-15
- Area: Verification
- Severity: Medium
- Task being attempted: Phase 5.1 (#61) — browserless view tests for the admin
  approval queue and the admin-review mode on `ReportDetailView`. The two new
  classes extended the report feature's `@Transactional` `AbstractReportViewUiTest`
  (for its report-seeding helpers) and authenticated with `@WithUserDetails`,
  exactly like the existing `ReportDetailViewUiTest`.
- Expected vs actual: green in isolation and in small combinations, but in the
  **full suite** every navigation in the two new classes resolved to `LoginView`
  (unauthenticated) — including `@PermitAll` targets. Reproducible by running
  ≥4-5 browserless classes together (`ReportDetailViewUiTest`,
  `MyReportsViewUiTest`, `AdminToolsViewUiTest`, + the two new ones): only the
  later-ordered `@Transactional` classes failed, while the non-transactional
  `AdminToolsViewUiTest` and the earlier-ordered transactional classes passed.
- Root cause: the F-020 strategy-divergence, seen from the browserless side. The
  browserless env installs Vaadin's session-aware `SecurityContextHolderStrategy`
  as the global static and reads the request's principal from the `VaadinSession`.
  `@WithUserDetails` writes the context via a `BeforeEachCallback`; once enough
  browserless classes (and the `SecurityContextHolder.setContextHolderStrategy(...)`
  pinning in the layer-2 service tests, F-020) have run in the JVM, the instance
  `@WithUserDetails` writes to and the one the browserless request reads diverge,
  so the session carries no authentication → `LoginView`. `@Transactional` made
  it worse (its listener/thread interplay shifted class ordering into the bad
  window); the non-transactional `AdminToolsViewUiTest` pattern never tripped it.
- Workaround used: gave the approval view tests their own base,
  `AbstractApprovalViewUiTest`, that extends `SpringBrowserlessTest` **directly and
  is not `@Transactional`** (mirroring `AdminToolsViewUiTest`), keeps
  `@WithUserDetails`, seeds reports straight through the repository + aggregate
  (also the only way to reach another owner / a pre-approved state), and cleans
  them up in an `@AfterEach` instead of by rollback. Order-independent and green in
  the full 237-test suite. Attempts to keep the transactional base and authenticate
  imperatively in `@BeforeEach` (pinning the bean, F-020 style) only fixed part of
  it — a `@BeforeEach` runs after the browserless env's `BeforeEachCallback`, so the
  timing is still fragile.
- Evidence: `approval/ui/AbstractApprovalViewUiTest`, `ApprovalQueueViewUiTest`,
  `ApprovalAccessUiTest`; contrast `report/ui/AbstractReportViewUiTest`
  (`@Transactional`, stable only because it orders early).
- Impact: a new browserless view test that both needs DB seeding and drives an
  authenticated flow should extend `SpringBrowserlessTest` directly (non-transactional,
  explicit cleanup), not the transactional report base — otherwise it is a
  suite-ordering time bomb.
- Suggested Vaadin/product improvement: the `@WithVaadinUser` utility floated in
  F-020 (write the auth through the active session-aware strategy) would fix both
  the headless and browserless sides of this footgun.
- Owner / next step: reuse `AbstractApprovalViewUiTest`'s shape for future admin
  view tests (reject/resubmit, export).

### F-041 — Manual/visual verification had no fixtures or economy guidance
- Date: 2026-07-15
- Area: Verification
- Severity: Medium
- Task being attempted: Issue #68 — making skill step 4 (drive the real app with
  the Playwright MCP) cheap to set up and run.
- Expected vs actual: Expected to land directly on the screen under test.
  Actual: the local DB started empty, so verifying the Phase 5.1 approval flow
  (#61) took ~52 Playwright calls — roughly half of them **setup** (clicking
  create → add line → save → submit through the UI just to have a `SUBMITTED`
  report), the rest inflated by full-tree `browser_snapshot` dumps used only to
  find element refs. Nothing documented the fixtures or a cheaper pattern.
- Workaround used: Added `LocalReportSeeder` (`@Profile("local")`, idempotent,
  empty-DB only) that seeds four labelled DRAFT/SUBMITTED(×2)/APPROVED fixtures
  straight through the repository + aggregate — the bypass-the-owner-scoped-service
  technique from `AbstractApprovalViewUiTest`. Documented the fixtures, logins,
  and a Playwright economy pattern (deep-link + stable selectors over snapshots,
  `browser_fill_form`, screenshot only unique visual states) in
  `docs/manual-verification.md`, and pointed `implement-use-case` step 4 at both.
- Evidence: `report/LocalReportSeeder`, `report/LocalReportSeederTest`,
  `docs/manual-verification.md`, `.claude/skills/implement-use-case/SKILL.md`;
  the #61 verification transcript.
- Impact: a login lands directly on the screen under test with zero setup clicks
  — cheaper for both an agent and a human smoke test, and a pleasant local demo.
- Suggested Vaadin/product improvement: a first-class "dev seed" story in the
  starter (profile-guarded, idempotent `ApplicationRunner` convention) so every
  app ships demo fixtures without hand-rolling one.
- Owner / next step: Resolved in #68. Extend the fixture set if a later phase
  (reject/resubmit) needs a `REJECTED` seed.

### F-042 — Running the app from a git worktree collides with the main checkout
- Date: 2026-07-15
- Area: Tooling/Template
- Severity: Low
- Task being attempted: Starting the app from the `issue-68` worktree to visually
  verify the seeder while the main checkout's stack was up.
- Expected vs actual: Expected `./mvnw` to just run. Actual: two collisions — the
  worktree's spring-boot-docker-compose tried to bind its own Postgres to host
  port 5432, already held by the main checkout's `expense-manager-postgres-1`
  ("port is already allocated"); and once that was bypassed, Tomcat failed on
  port 8080, already held by the main dev server.
- Workaround used: Ran against the shared already-running Postgres with
  `-Dspring.docker.compose.enabled=false` and on a free port with
  `-Dserver.port=8081`. The datasource default (`localhost:5432/expense_manager`)
  already points at the running container, so no other wiring was needed.
- Evidence: `application-local.properties` (compose auto-start + datasource
  default); startup logs "Bind for 0.0.0.0:5432 failed" / "Port 8080 was already
  in use".
- Impact: a second checkout/worktree can't naively `./mvnw` alongside the primary
  one; easy to misread as a broken change rather than a port clash.
- Suggested Vaadin/product improvement: none for the product; a worktree note in
  `DEVELOPMENT.md` ("reuse the running DB with compose disabled + a spare port")
  would save the rediscovery.
- Owner / next step: low priority; document the worktree recipe if multi-checkout
  dev becomes common.

### F-043 — Admin review mode silently became editable once Reject moved the report to the (owner-)editable REJECTED state
- Date: 2026-07-15
- Area: Spec
- Severity: Medium
- Task being attempted: Phase 5.2/5.4 (#62) — the admin Reject action on
  `ReportDetailView`'s review mode. `load()` derived interactivity purely from
  status: `editable = dto.status().isEditable()`, then toggled Save/Add-expense/
  Insert-travel/trash and per-card click editing off that flag.
- Expected vs actual: the approve slice (#61) was safe only by accident — the only
  review-reachable state was `SUBMITTED` (not editable), and approve moved it to
  `APPROVED` (also not editable), so review mode was always read-only in practice.
  Reject moves the report to `REJECTED`, which **is** editable (the owner resubmit
  path). After a reject the same view reloaded `REJECTED` and flipped fully
  interactive **for the admin** — Save, Add expense, Insert travel, and the line
  trash all appeared on another user's report. Manually opening `/review/{id}` for
  an already-`REJECTED` report showed the same. Caught in Playwright MCP
  verification, not by the browserless tests (which only asserted the buttons that
  existed pre-fix).
- Root cause: "editable" conflated two questions — *is this status editable?* (a
  domain fact) and *may the current viewer edit it?* (owner path only). Review mode
  is read-only regardless of status, but that constraint lived implicitly in which
  statuses happened to be reachable, not in the code.
- Workaround used: `editable = dto.status().isEditable() && !reviewMode;` — review
  mode is always read-only. Added regression assertions to the successful-reject UI
  test (`Save`/`Add expense` absent after the transition).
- Evidence: `report/ui/ReportDetailView#load`; `approval/ui/ApprovalQueueViewUiTest`
  `rejectingWithAReasonRecordsItAndMovesTheReportToRejected`.
- Impact: a reachable authority gap (an admin editing/saving someone else's report
  through the review view) that stayed latent until the first review-reachable
  state was itself editable. A reminder that "read-only" must be pinned to the
  viewer's role, not inferred from the set of currently-reachable statuses.
- Suggested Vaadin/product improvement: none — app-side. Worth a review-mode
  read-only guard test even for statuses no queue currently routes to.
- Owner / next step: none.

### F-044 — The single-report deep-link in the verification doc used a query param, not the path segment
- Date: 2026-07-15
- Area: Docs
- Severity: Low
- Task being attempted: Phase 5.5 (#63) — visually verifying the owner's edit +
  resubmit path (`REJECTED → SUBMITTED`) on `ReportDetailView` via Playwright MCP,
  deep-linking to the seeded rejected report per `docs/manual-verification.md`.
- Expected vs actual: expected the doc's `/report?reportId=<id>` recipe to open
  that report. Actual: `ReportDetailView` binds its id via `HasUrlParameter<Long>`
  (a path segment), so `?reportId=5` bound no id and silently opened a *fresh
  transient* report — the correct form is `/report/5`.
- Workaround used: corrected the deep-link to `/report/<id>` in the doc.
- Evidence: `report/ui/ReportDetailView` (`implements HasUrlParameter<Long>`);
  `docs/manual-verification.md`.
- Impact: a quiet trap — the wrong URL 200s onto a plausible-looking (but wrong)
  new-report screen rather than erroring, so a verifier can mistake an empty draft
  for the report under test.
- Suggested Vaadin/product improvement: none — doc fix.
- Owner / next step: none.

### F-045 — The always-enabled-Save + error-summary editor scaffold is now hand-copied into a fourth place
- Date: 2026-07-15
- Area: Standards
- Severity: Low
- Task being attempted: Phase 6 write path (#65) — the Users role/access editor on
  `UserManagementView`. The ticket pointed at reusing
  `ReferenceViewSupport.openEditor` "or an analogous local helper".
- Expected vs actual: expected to reuse the shared helper. Actual:
  `ReferenceViewSupport` is package-private in `reference.ui`, so it is unreachable
  from `user.ui`; the same Dialog + `role="alert"` error summary + always-enabled
  Save + `writeBeanIfValid` + `IllegalArgumentException`→summary scaffold had to be
  re-inlined. That makes **four** near-identical copies: `ReferenceViewSupport`,
  `LineEditorDialog`, `TravelEditorDialog`, and now `UserManagementView`.
- Workaround used: inlined a local `openEditor(...)` + `showErrors(...)` in
  `UserManagementView`, matching the established idiom (F-013, ADR-0020) so the
  behaviour and accessibility contract stay identical.
- Evidence: `user/ui/UserManagementView#openEditor`;
  `reference/ui/ReferenceViewSupport#openEditor`;
  `report/ui/LineEditorDialog`; `report/ui/TravelEditorDialog`.
- Impact: low functional risk (the copies agree today), but the error-summary
  contract now lives in four spots — a fix or a11y tweak has to be applied four
  times, and a new editor has no obvious shared home to reach for.
- Suggested Vaadin/product improvement: none for Vaadin — app-side. Promote the
  editor scaffold to a small cross-cutting helper (e.g. `base.ui` or a dedicated
  `EditorDialogs` utility) that any feature package can call, and migrate the four
  copies onto it.
- Owner / next step: **partly resolved (#76)** — the scaffold is now the
  `base.ui.EditorDialog` component (a `Dialog` subclass owning the always-enabled
  Save + `role="alert"` summary + `writeBeanIfValid`), and the reference/allowance
  editors use it. (An earlier take used a static `AdminEditor.openEditor` helper;
  it was replaced by the component after review — a static method wrapping the
  Dialog API was the wrong shape.) The three remaining copies
  (`report/ui/LineEditorDialog`, `report/ui/TravelEditorDialog`,
  `user/ui/UserManagementView`) can now migrate onto `EditorDialog` too — left for
  a follow-up since #76 scoped to reference + allowance.
- Update: the **error-summary half** of this is now fully resolved — the summary
  contract lives once in `base.ui.ErrorSummary` and all five call sites
  (`EditorDialog`, `LineEditorDialog`, `TravelEditorDialog`, `ReportDetailView`,
  `UserManagementView`) delegate to it (see F-050). The remaining duplication is
  only the surrounding *Dialog* scaffold in the three non-`EditorDialog` editors.

### F-046 — A `public final` method on a Vaadin route's superclass breaks Spring's CGLIB proxy of the route bean
- Date: 2026-07-16
- Area: Vaadin
- Severity: Low
- Task being attempted: #76 — extracting the shared `ReferenceConfigEditor<T>`
  base for the reference admin screens. `VatRateView`/`ExpenseTypeView` (both
  `@Route @RolesAllowed` beans) now `extends ReferenceConfigEditor<…>`, whose
  grid-reload method I first declared `public final void refresh()`.
- Expected vs actual: expected a plain inherited method. Actual: on view
  instantiation Spring logged `CglibAopProxy: Public final method ... refresh()
  cannot get proxied via CGLIB, consider removing the final marker`. Vaadin's
  Spring route targets are container-managed beans, and the security layer wants a
  CGLIB subclass proxy; a `final` public method on the superclass makes the whole
  bean non-proxyable, so the advice is silently dropped.
- Workaround used: dropped `final` from `refresh()` (kept it `public`,
  overridable). Warning gone.
- Evidence: Spring log line `o.s.aop.framework.CglibAopProxy` during
  `VatRateViewUiTest` context start. (The base was later reworked from a generic
  `ReferenceConfigEditor` into the abstract `reference/ui/ReferenceConfigView`;
  the lesson stands — none of its methods, incl. `refresh()`, are `final`.)
- Impact: low here (no advice actually targets `refresh()`), but a trap: a `final`
  method anywhere on a route's type hierarchy can quietly disable proxy-based
  cross-cutting (method security, `@Transactional`) on that view with only a WARN.
- Suggested Vaadin/product improvement: none code-wise — worth a note in the
  Vaadin+Spring guidance that route/component base classes should avoid `final`
  public methods.
- Owner / next step: none.

### F-047 — Pre-existing aria-label inconsistency across the two reference screens forced extra config surface on the extracted editor
- Date: 2026-07-16
- Area: UX-spec
- Severity: Low
- Task being attempted: #76 — expressing `VatRateView` and `ExpenseTypeView` as
  configs of one `ReferenceConfigEditor<T>` while keeping behaviour (and the exact
  aria-labels the view tests assert) unchanged.
- Expected vs actual: expected one row-subject function per kind to drive all
  three action aria-labels (edit / reorder / toggle). Actual: the two screens had
  drifted — VAT rates label the Edit button `"Edit rate 13.5 %"` while reorder /
  toggle use the same `"rate 13.5 %"` subject; expense types label Edit
  `"Edit expense type Restaurant/meals"` but reorder / toggle use the bare name
  `"Travel allowance"` / `"Publications"`. So the Edit subject and the
  reorder/toggle subject genuinely differ per kind.
- Workaround used: each view spells out its own aria-labels inline in its
  `actions(...)` cell — Edit vs reorder/toggle worded per the existing (drifted)
  convention — preserving every label verbatim rather than normalising them (which
  would change accessible text and break the assertions). (The interim
  config-object take encoded this as two separate label hooks; after the
  parent-class rework the labels are just inline strings in each view.)
- Evidence: `reference/ui/VatRateView#actions` ("Edit rate …" vs "Move rate … up"
  / "Deactivate rate …") vs `reference/ui/ExpenseTypeView#actions` ("Edit expense
  type …" vs bare-name "Move … up" / "Deactivate …").
- Impact: the real lesson is that unspec'd, hand-written aria-label wording drifts
  between sibling screens, and any later extraction must either carry the drift or
  make a deliberate normalisation call.
- Suggested Vaadin/product improvement: none — app-side. A small aria-label
  convention (e.g. always `"<verb> <entity-noun> <identifier>"`) would make the two
  screens' labels uniform.
- Owner / next step: none; consider normalising the labels in a dedicated a11y pass.

### F-048 — `field.setRequiredIndicatorVisible(true)` is redundant next to `Binder…asRequired(…)` and is copied across every editor
- Date: 2026-07-16
- Area: Standards
- Severity: Low
- Task being attempted: #76 review — reworking the reference/allowance editors.
  A reviewer flagged that each editor field does
  `field.setRequiredIndicatorVisible(true);` immediately before binding it with
  `binder.forField(field).asRequired(…)`.
- Expected vs actual: expected the manual call to be doing something. Actual: it
  is a no-op duplicate — `Binder`'s `asRequired(…)` already enables the field's
  visual required indicator when the binding is created. The Vaadin docs state
  this in three places: "calling `binder.asRequired()` on your field automatically
  enables the required indicator"; and "Using `asRequired()` has two effects:
  1. A visual required indicator appears on the field. 2. …". So the explicit
  `setRequiredIndicatorVisible(true)` adds nothing wherever `asRequired` is used.
- Origin: introduced in the very first editor (Phase 2.1 reference-data CRUD,
  commit `6319e5d`, #27) and then copy-pasted as the house pattern into every
  editor since — `git log -S 'setRequiredIndicatorVisible'` shows it spreading
  through #23/#32, #24/#37, #48/#52, #49/#55, #51/#66, #62/#70.
- Workaround used: dropped the redundant call from the in-scope editors
  (`reference/ui/*`, `allowance/ui/*`) while reworking them; behaviour and the
  rendered required indicator are unchanged (verified — the view tests still pass).
- Evidence: Vaadin 25.2 docs `flow/binding-data/components-binder-beans` and
  `building-apps/forms-data/add-form/validation`; the ~14 call sites from
  `grep -rn setRequiredIndicatorVisible src/main/java` at review time.
- Impact: cosmetic-only noise, but it is genuinely misleading — it reads as if the
  indicator needs manual enabling, which invites cargo-culting into fields that do
  NOT use `asRequired` (where it would then be load-bearing and easy to break).
- Suggested Vaadin/product improvement: none for Vaadin. App-side: leave the
  indicator to `asRequired`; only call `setRequiredIndicatorVisible` on fields
  that are required WITHOUT an `asRequired` binding.
- Owner / next step: low priority — sweep the remaining copies in `report/ui`
  (`ReportDetailView`, `LineEditorDialog`, `TravelEditorDialog`) in a follow-up;
  each is a required field already bound with `asRequired`.

### F-049 — "Submit for approval" acted on the last-saved state, silently dropping in-progress edits
- Date: 2026-07-16
- Area: Domain/Service + UI (report detail)
- Severity: Medium
- Task being attempted: Issue #81 — Submit should save the current state and ask
  for confirmation.
- Expected vs actual: Expected the report the user *sees* (with unsaved edits) to
  be what gets submitted. Actual: `onSubmit()` called `service.submit(id, version)`
  against the persisted row only — any working-copy edit (lines, additional info,
  date, buffered receipts) not yet Saved was discarded on submit, and there was no
  confirmation for the one-way lock. Worst case: a report with a line added-but-not-
  Saved failed the "≥1 line" guard because the line lived only in the working copy.
- Workaround used: added `ExpenseReportService.saveAndSubmit(...)` — one
  `@Transactional` that runs the whole-aggregate UPDATE then the `SUBMITTED`
  transition atomically (a failed guard rolls the save back), dispatching first-
  submit vs resubmit on the aggregate's own origin state so a single service method
  serves both UI actions. `onSubmit()` now validates the form, opens a confirmation
  dialog, and on confirm calls the atomic method. The old `submit`/`resubmit`
  service methods are kept (used by test seeders).
- Evidence: `ReportDetailView.onSubmit/confirmSubmit/performSubmit`,
  `ExpenseReportService.saveAndSubmit/applyUpdate`; new tests
  `submittingPersistsA{ReportLevelEdit,LineAdded}…`,
  `saveAndSubmitPersistsTheWorkingEditsThenSubmits`; verified end-to-end in the
  local app (edit-without-save → confirm submit → edit persisted + SUBMITTED).
- Impact: silent data loss on the most consequential owner action; the fix also
  makes the submit atomic and confirmed.
- Suggested Vaadin/product improvement: none — application-level design gap, not a
  framework issue.
- Owner / next step: resolved in this change (issue #81).

### F-050 — The error-summary behaviour was copy-pasted five times and none of it was actually accessible
- Date: 2026-07-16
- Area: Standards + Accessibility (all forms/editors)
- Severity: Medium
- Task being attempted: put the validation-error behaviour in one place instead of
  copy/paste, and make it navigable for keyboard/AT users (focus the summary on
  submit; link each error to its field; add the ARIA wiring).
- Expected vs actual: expected one shared error-summary with the accessible
  pattern. Actual: a bare `Div` + `role="alert"` + a local
  `showErrors(List<String>)`/`clearErrors()` pair was re-inlined in **five** places
  (`base.ui.EditorDialog`, `LineEditorDialog`, `TravelEditorDialog`,
  `ReportDetailView` — incl. its reject-dialog and optimistic-lock conflict UX — and
  `UserManagementView`). Every copy only rendered a flat list of message *strings*:
  focus never moved to the summary on an invalid submit, the entries were plain
  text (no way to jump to the offending field), and `role="alert"` + a
  focus-on-submit was never combined, so a screen-reader / keyboard user got no
  actionable path from "there are errors" to the field to fix.
- Workaround used: extracted `base.ui.ErrorSummary` (a `Div` implementing
  `Focusable`) owning the contract once — `role="group"` + `aria-labelledby`→its own
  heading + `tabindex=-1`, `focus()` on every show (so the group is announced and
  scrolled into view), and a `showValidationErrors(BinderValidationStatus)` path
  that renders each field-level error as a focusable control whose activation calls
  `field.focus()` (the GOV.UK / reindeer-plus error-summary behaviour; Vaadin
  already wires the reverse field→message `aria-describedby` once the binder has
  validated). Plain-message (`show`) and custom-body (`showCustom`, for the
  conflict/reload affordance) variants share the same styled, focused box. All five
  call sites now delegate; `styles.css` `.error-summary` gained the Aura box styling
  + `.error-summary-link`.
- Evidence: `base/ui/ErrorSummary.java`; the five migrated call sites; the ARIA
  contract is asserted in `ReferenceConfigViewUiTest`
  (`invalidSaveShowsErrorSummaryAndPersistsNothing` — role/tabindex/aria-labelledby
  + a focusable field entry). Full UI suite green (94 tests).
- Impact: the a11y contract lives in one place and a fix/tweak applies everywhere;
  more importantly, forms went from "shows a list you can't act on" to the standard
  accessible summary (announced, focusable, one activation from summary to field).
- Suggested Vaadin/product improvement: a first-party accessible error-summary
  component tied to `Binder`/`BinderValidationStatus` would save every app from
  hand-rolling this (and from getting the ARIA + focus behaviour wrong, as here).
- Owner / next step: resolved in this change.

### F-051 — Shared reused Testcontainers DB + fixed host ports make running a new migration inside a git worktree fail confusingly
- Date: 2026-07-17
- Area: Tooling/Template + Verification
- Severity: Medium
- Task being attempted: adding a new Flyway migration (V10, the "Other" expense
  type for issue #87) in a git worktree, then running the browserless suite and
  the app for visual verification while the main checkout was also running.
- Expected vs actual: expected the worktree to be self-contained. Actual, three
  separate machine-global collisions surfaced in sequence: (1) the integration
  tests failed with `Migration checksum mismatch for migration version 10`
  because `testcontainers.reuse.enable=true` keeps **one** singleton
  `postgres:17-alpine` container shared across every worktree — a differently
  numbered/authored V10 from another worktree had already been recorded in its
  `flyway_schema_history`; (2) `./mvnw spring-boot:run` failed with
  `Bind for 0.0.0.0:5432 failed: port is already allocated` because this
  worktree's `compose.yaml` hard-pins host port 5432 and the main checkout's dev
  DB already held it; (3) after pointing at that DB with
  `--spring.docker.compose.enabled=false`, startup failed with `Port 8080 was
  already in use` (the main checkout's app).
- Workaround used: (1) `docker rm -f <reused-container>` to force a fresh
  Testcontainers DB so the new V10 checksum records cleanly; (2)/(3) reused the
  already-running main-checkout app on :8080 for visual verification — my
  disabled-compose run had already applied V10 to the shared dev DB before the
  :8080 clash, so "Other" was live there. Cleaned up the orphaned
  `issue-87-*` compose container/network/volume afterward.
- Evidence: surefire `Migration checksum mismatch for migration version 10`;
  app log `Bind for 0.0.0.0:5432 failed`, `Port 8080 was already in use`;
  `~/.testcontainers.properties` (`testcontainers.reuse.enable=true`),
  `compose.yaml` (`ports: '5432:5432'`).
- Impact: developing DB migrations concurrently across worktrees is a foot-gun —
  the reused container silently couples their Flyway histories, and fixed host
  ports mean only one worktree can run its app/compose at a time. The failure
  messages point at the symptom, not the shared-resource root cause.
- Suggested Vaadin/product improvement: for multi-worktree work, either drop the
  fixed host-port mapping in `compose.yaml` (let Docker assign an ephemeral port,
  which Spring Boot's compose support discovers) and/or scope the Testcontainers
  reuse per checkout; document the `docker rm` reset for the checksum-mismatch
  case.
- Owner / next step: resolved for this change (verification complete); the
  compose/port ergonomics are a follow-up if worktree-based dev continues.

### F-052 — Vaadin picker input constraints go invalid with an *empty* default message (bad UX by default)
- Date: 2026-07-17
- Area: Vaadin
- Severity: Medium
- Task being attempted: fixing issue #85 — empty bullets in the top-of-form error
  summary when a user filled only the date (not the time) in a `DateTimePicker`, or
  typed an unparseable value (`dsdds`) into a `DatePicker`.
- Expected vs actual: expected a component's built-in, non-configurable input
  constraints to carry a sensible **default** error message (as browsers do for
  native inputs — "Please fill out this field"). Actual: in V25 these constraints
  fire but their message is **empty until you set it via i18n** —
  `DateTimePickerI18n.setIncompleteInputErrorMessage` (V25 newly treats date-without-
  time as invalid, per the upgrade guide), `setBadInputErrorMessage` on both pickers.
  An unset message means the field goes red with *no* text under it, and any code
  that surfaces binder errors elsewhere (our shared `ErrorSummary`) renders a blank,
  meaningless entry. Nothing warns the developer at build or run time.
- Workaround used: set the i18n messages on every picker
  (`TravelEditorDialog` departure/return, `ReportDetailView` report date), and hardened
  `ErrorSummary` to substitute a fallback + `warn` (naming the offending field) if a
  blank message ever reaches it again.
- Evidence: `report/ui/TravelEditorDialog.java` (`dateTimeErrorMessages()`),
  `report/ui/ReportDetailView.java` (`reportDate` i18n), `base/ui/ErrorSummary.java`
  (`orFallback`/`describe`); Vaadin docs `date-time-picker` "Bad Input" constraint is
  described as "non-configurable and enabled by default" yet ships no default text;
  the V25 upgrade guide documents the new incomplete-input invalidation.
- Impact: every form with a `DatePicker`/`DateTimePicker` silently ships with a
  broken error state until each constraint message is set by hand — easy to miss
  (the happy path and overlay-picking never trigger it; only typed/partial input
  does), and there is no signal that a message is missing.
- Suggested Vaadin/product improvement: ship a sensible built-in default message
  for the non-configurable input constraints (bad-input, incomplete-input), the way
  the platform already defaults required-indicator styling — i18n should *override*
  a default, not be the only thing standing between the user and a blank error.
- Owner / next step: worked around in this change; the empty-default is a Vaadin
  platform issue to raise upstream.

### F-053 — Dev-toolbar `hidePopover` error swallows the *first* overlay opened over another modal
- Date: 2026-07-21
- Area: Vaadin
- Severity: Low
- Task being attempted: visually verifying issue #86 — a technical error inside the
  modal `TravelEditorDialog` opens the new generic `ErrorDialog` over it.
- Expected vs actual: expected `new ErrorDialog(...).open()` to render immediately,
  as the browserless test proves server-side. Actual: on the **first** trigger the
  server logged the error and attached the dialog, but it never appeared in the DOM;
  the browser console threw `NotSupportedError: Failed to execute 'hidePopover' …
  Not supported on elements that are not popovers` from Vaadin's dev-mode
  `promoteToolbar`/`overlayListener` (the Copilot dev toolbar) as the second overlay
  opened. Re-triggering the exact same action rendered the dialog correctly (2 open
  overlays, detail visible).
- Workaround used: none needed for the product — the error originates in the
  **dev-mode toolbar**, absent in staging/prod builds; the second attempt worked and
  the browserless `ErrorDialogFlowUiTest` confirms the attach behaviour headlessly.
- Evidence: `base/ui/ErrorDialog.java` / `base/ui/UiErrorHandler.java`; console
  `promoteToolbar (…indexhtml-*.js) → overlayListener`; server log "Technical error
  surfaced to the user as a generic dialog" fired on the first (non-rendering) try.
- Impact: only a dev-mode flake — a dialog opened over an existing modal can silently
  fail to render the first time, which is confusing when hand-verifying overlay-over-
  overlay flows locally. No production impact.
- Suggested Vaadin/product improvement: guard the dev toolbar's `beforetoggle`/
  `hidePopover` overlay listener against non-popover elements so a stacked overlay
  doesn't throw and abort the DOM patch.
- Owner / next step: no product action; raise the dev-toolbar overlay-stacking error
  upstream if it recurs.

### F-054 — Playwright MCP `browser_fill_form` can't fill a Vaadin ComboBox (it expects a native `<select>`)
- Date: 2026-07-27
- Area: Verification
- Severity: Low
- Task being attempted: visually verifying issue #124 — filling the
  `TravelEditorDialog` (dates, texts, and the "Destination country" ComboBox) in one
  `browser_fill_form` call, the batching the `visual-verification` skill recommends.
- Expected vs actual: expected a `type: "combobox"` field to select an option like a
  human does. Actual: the call fails outright with `Error: Element is not a <select>
  element` — the MCP maps `combobox` to Playwright's `selectOption`, which only
  drives native `<select>`; a `vaadin-combo-box` exposes an `<input role="combobox">`.
  The failure aborts the **whole** batch, so the other (perfectly fillable) fields in
  the same call are lost. Setting `comboBox.value` from `browser_evaluate` doesn't
  work either — the web component's items are server-fed, so the assignment reads
  back as `""` and the server never sees a value.
- Workaround used: fill every non-ComboBox field in one `browser_fill_form`, then
  drive each ComboBox with two clicks — `#input-vaadin-combo-box-<n>` (found via a
  one-line `browser_evaluate` over `document.querySelectorAll('vaadin-combo-box')`,
  since the accessibility-tree `ref` goes stale after the form fill) and then
  `vaadin-combo-box-item:has-text("…")` in the overlay.
- Evidence: this ticket's verification run — `browser_fill_form` on
  `vaadin-combo-box[label="Destination country"]` → "Element is not a `<select>`
  element"; the click-input-then-click-item pair worked first try.
- Impact: one extra call per ComboBox, and a batch that must be split around them —
  worth knowing before writing the "one big fill_form" call the skill suggests.
- Suggested Vaadin/product improvement: teach the Playwright MCP's `combobox` field
  type to fall back to click-input-then-pick-option for ARIA comboboxes (or have
  `browser_fill_form` skip only the failing field instead of aborting the batch).
- Owner / next step: documented in `docs/manual-verification.md`'s Playwright
  pattern; no product change.

### F-055 — A Vaadin `DateTimePicker` can't be driven by Playwright at all without hand-dispatching `value-changed`
- Date: 2026-07-27
- Area: Verification
- Severity: Medium
- Task being attempted: visually verifying issue #132 — filling the two
  `DateTimePicker`s in `TravelEditorDialog` so the trip earns a partial-day per-diem
  to suppress.
- Expected vs actual: three plausible approaches all fail *silently*, which is the
  expensive part — the client shows the typed date and time, so the dialog looks
  filled, and only the server-side error summary ("Departure date & time is
  required") reveals that nothing arrived. (1) `browser_fill_form` /
  `page.fill()` on the inner `vaadin-date-picker input` + `vaadin-time-picker input`
  updates the visible text but never commits to the composite. (2) Setting
  `picker.value = '2026-07-10T09:00'` from `browser_evaluate` sets the property and
  updates both sub-fields, but Flow's server binding never fires. (3) Dispatching
  `input` + `change` on the inner inputs commits a `TextField` fine (that *is* enough
  for `vaadin-text-field`) but still not the picker.
- Workaround used: set `.value` on the `vaadin-date-time-picker` **and** dispatch
  both a `CustomEvent('value-changed', {detail: {value}})` and a `change`, bubbling
  and composed. Dispatching only `value-changed` was not enough — the pair is what
  works, and I confirmed it by having it fail the first time I dropped the `change`.
- Evidence: this ticket's verification run — three rounds of "Save trip" rejected
  with all four required-field errors, each round narrowing which dispatch was
  missing; the event pair worked first try afterwards.
- Impact: this is the single most expensive step in visually verifying anything
  trip-related, and it is *not* discoverable — the UI lies about being filled. Worth
  a line in the manual-verification Playwright pattern beside F-054's ComboBox note.
- Suggested Vaadin/product improvement: same shape as F-054 — a Playwright MCP that
  knows Vaadin fields (or a documented `setValue` escape hatch on the web component)
  would remove a whole class of silent failures.
- Owner / next step: documented in `docs/manual-verification.md`'s Playwright
  pattern; no product change.

### F-056 — "Seed, don't click" doesn't cover trips: `LocalReportSeeder` seeds no `Travel`
- Date: 2026-07-27
- Area: Verification
- Severity: Medium
- Task being attempted: visually verifying issue #132, whose entire subject is a
  travel-generated line — so the fixture needed is "a DRAFT report with a trip that
  earns a partial-day per-diem" and "a trip that earns a meal allowance carrying a
  receipt".
- Expected vs actual: `docs/manual-verification.md` promises the seeder lands you
  "directly on the screen under test — never click through create → add line → save".
  Actual: every seeded report has one €100 manual line and **no trip**, so anything
  in Phase 4 (per-diem, kilometre, meal, parking, receipts on generated lines, and
  now the Quantity Override) starts with the trip dialog — ~15 interactions per
  fixture, and it is exactly the dialog F-055 makes hardest to drive.
- Workaround used: built both trips by hand through `TravelEditorDialog`, put them on
  one report to pay the login/navigation cost once, and deleted the report from the
  local DB afterwards.
- Evidence: `LocalReportSeeder` has no reference to `Travel`/`TravelDto`; this run
  spent roughly two thirds of its Playwright calls on fixture setup — the precise
  cost the lever exists to remove (issue #68).
- Impact: the cheap-verification lever now misses the phase the project is actually
  building in, so the "~12 calls" target in `manual-verification.md` is unreachable
  for any travel ticket.
- Suggested Vaadin/product improvement: none — this is ours.
- Owner / next step: worth a small ticket — add two travel fixtures to
  `LocalReportSeeder` (a 55 h domestic trip earning full + partial days, and a
  not-eligible trip earning a meal allowance with a receipt) and label them like the
  existing ones. Not done here: it is a change to a seeder outside #132's scope.

### F-057 — A browserless dialog's words are invisible in `UI.getElement().getTextRecursively()` — silently, and only sometimes
- Date: 2026-07-29
- Area: Verification
- Severity: Medium
- Task being attempted: asserting the text of the new clear-your-override confirm
  (issue #133) in `ReportDetailViewUiTest` — a dialog opened *over* the still-open
  `TravelEditorDialog`, so a stacked overlay.
- Expected vs actual: expected the repo's established pattern —
  `assertThat(UI.getCurrent().getElement().getTextRecursively()).contains(…)`, used by
  a dozen dialog tests here — to see the confirm's paragraphs. Actual: that string
  carries **neither the view's text** (it is `""` on a plain report screen with a
  full report rendered) **nor a just-opened dialog's**. A probe showed the trip
  dialog's own content appearing there only *after* a subsequent interaction
  (`setValue` on a field), and the confirm's content never — while
  `findButton().withText("Keep editing").click()` located and clicked a button inside
  that very confirm. So the locator DSL sees the dialog and the text API does not.
- Workaround used: read the dialog, not the UI —
  `findDialog().components().stream().filter(Dialog::isOpened)`, pick the one whose
  `getElement().getTextRecursively()` contains a phrase only it uses (the trip editor
  is still open behind the confirm, so "the dialog" is ambiguous), and assert on that.
  Header titles are a property, not text, so they need `Dialog::getHeaderTitle`.
  Wrapped as `openDialogSaying` / `clearingConfirm` / `tripPreviewText` in the test.
- Evidence: this ticket — six new tests failed with `Expecting actual: ""` or with
  only the *other* dialog's text, while the behaviour they asserted was working (the
  browser run confirmed it). Probe output: `ui text (no dialog): []` beside a
  1 000-character view; `ui text (trip editor open): []` beside a dialog whose own
  `getTextRecursively()` returned the full preview.
- Impact: the failure mode is an empty string, which reads as "the dialog says
  nothing" — i.e. it looks like a product bug in the code under test. Worse, the
  existing passing tests are passing *by luck of an extra interaction*: the same
  assertion is one refactor away from silently asserting against `""`. Any test that
  asserts dialog copy should read the dialog.
- Suggested Vaadin/product improvement: either make `UI.getElement()
  .getTextRecursively()` include attached overlay content deterministically, or give
  the browserless DSL an explicit `text()` on the locators (e.g.
  `findDialog().text()`) so the reliable path is the obvious one. Failing an assert
  on an empty tree is a third option: an empty `getTextRecursively()` on a UI that
  has children is never what the caller meant.
- Owner / next step: documented here and in the test's javadoc; folded the
  IntegerField half of the same class of problem into
  `docs/manual-verification.md`'s Playwright pattern (below).

### F-058 — `setMin`/`setMax` are inclusive, so they cannot express a strict range — and the boundary value fails silently
- Date: 2026-08-25
- Area: Vaadin
- Severity: Medium
- Task being attempted: fixing issue #140 — a trip whose departure and return carry
  the same date *and* time. The trip rule is strict (`returnAt.isAfter(departureAt)`),
  and `TravelEditorDialog` guarded it the obvious way: each picker bounds the other,
  `returnAt.setMin(departure)` / `departure.setMax(returnAt)`.
- Expected vs actual: expected the reciprocal bounds to make an invalid range
  unreachable through the UI — which is what the code's own comment claimed ("the
  overlay can't produce one"). Actual: `setMin`/`setMax` are **inclusive** ("the
  minimum date and time that is allowed to be set"), so the equal instant is a value
  the overlay happily offers on both sides. Half the rule was enforced; the boundary
  — the only value a strict rule and an inclusive bound disagree about — was not.
- Workaround used: shift the bounds by one picker step —
  `returnAt.setMin(departure.plus(TRIP_STEP))` and the mirror — so the inclusive
  bound lands where the strict rule starts. Works because the field has a step; a
  continuous field would have no correct value to shift by.
- Evidence: issue #140. `DateTimePicker.setMin` javadoc (25.2.1); the equal instant
  reached `AllowanceCalculator` and threw. The repo's own regression test
  (`choosingADepartureConstrainsTheReturnPickerRangeAndViceVersa`) asserted
  `ret.getMin()).isEqualTo(DEP)` — it encoded the gap rather than catching it.
- Impact: the failure is invisible at the call site. `setMin(departure)` reads as
  "the return can't be the departure", the overlay looks constrained, and every
  value except one is handled — so the gap survives review and a passing test, and
  surfaces as a user hitting a rule the UI told them they were obeying. Any strict
  range guarded this way has the same hole: date pickers, number fields (`setMin`
  on `IntegerField`/`BigDecimalField` is inclusive too), time pickers.
- Suggested Vaadin/product improvement: give the constraint APIs an exclusive form —
  `setMinExclusive(...)` — or at minimum say "inclusive" in the javadoc's first line
  rather than leaving it to be inferred from "allowed to be set". A strict range is
  not an exotic requirement (a trip, a booking, a date range of any positive length),
  and today every app expressing one has to know the step and do this arithmetic.
- Owner / next step: fixed in this repo (#140). The paired lesson is ours, not
  Vaadin's: the client-side bound was the *only* guard the user ever saw, because the
  server-side rule threw a plain `IllegalArgumentException` and so was bucketed as a
  technical failure and shown as the generic error dialog (issue #86's split). A
  client constraint should never be the only thing standing between the user and a
  rule — the server rule it mirrors has to be user-facing too.

### F-059 — A project-scoped MCP server can be committed but not *used*: the same session that adds it can never call it
- Date: 2026-08-26
- Area: Tooling/Template
- Severity: Medium
- Task being attempted: issue #142 — set up the Figma → Vaadin toolchain, whose
  acceptance criterion is a smoke test (`get_design_context` on node `88-12278`)
  proving the setup works rather than merely being present.
- Expected vs actual: expected that writing the `Figma` entry into `.mcp.json` — the
  project-scoped file, chosen precisely so every dev gets the server without hand
  configuration — would make the server reachable. Actual: `claude mcp list` reports
  it as `⏸ Pending approval (run \`claude\` to approve)`. A project-scoped server is
  gated on two things the agent that wrote the config cannot do: the user approving
  the newly-appeared entry, and an interactive OAuth round-trip via `/mcp`. The tool
  namespace for the current session was fixed at startup, so even after approval no
  `mcp__Figma__*` tool exists until the session restarts.
- Workaround used: none available in-session. Everything else in the issue was
  completed and committed; the smoke test was handed back to the user as an explicit
  blocked item rather than reported as done or quietly skipped.
- Evidence: `claude mcp list` output above; `.mcp.json` at this commit.
- Impact: this is the general shape of "an agent sets up its own tooling". Config
  changes that alter the *tool namespace* — MCP servers above all — are always at
  least one session-boundary and one human approval away from being exercisable, so
  an issue that both configures a server and requires proof it works cannot be closed
  in one pass. The failure is worse when unnoticed: the skills that depend on the
  server degrade to guessing from layer names rather than erroring, so an unauthenticated
  run produces plausible-looking layout invented from nothing. That's why
  `DEVELOPMENT.md` now names the symptom ("layout invented from thin air → check
  `/mcp` first") instead of just the setup step.
- Suggested Vaadin/product improvement: not Vaadin's — Claude Code's. Two things would
  help: (a) a way for a session to re-read `.mcp.json` and pick up newly approved
  servers without a restart, and (b) making a *pending* server visible to the model as
  a named, unusable tool rather than as absence, so "the server isn't authenticated"
  is distinguishable from "this capability doesn't exist". Today both look identical.
- Owner / next step: user to approve the `Figma` entry and authenticate via `/mcp`,
  then re-run the smoke test on this branch and post the node `88-12278` report to
  issue #142. Splitting "configure" from "prove it works" into two issues would have
  made this a non-event.
- Owner / next step: resolved the only way it could be — the user approved the entry
  and authenticated, and the smoke test then ran in the following session (see F-061
  for what it found). Splitting "configure" from "prove it works" into two issues
  would have made this a non-event.

### F-060 — A vendored skill's own reference file doesn't exist upstream, and the skill gives no sign of it
- Date: 2026-08-26
- Area: AI
- Severity: Low
- Task being attempted: issue #142 — copying `figma-to-aura-theme` from
  `juuso-vaadin/figma-to-vaadin-skill` at commit `3a9289c` as a project-owned skill.
- Expected vs actual: expected the skill's bundled references to come across with it.
  Actual: `figma-to-aura-theme/SKILL.md` instructs the agent three separate times to
  consult `property-values.md` for the Aura named-colour, background-colour and
  curated-font tables — the tables that decide every value it emits — and no such file
  exists at that commit. The whole repo is four `SKILL.md` files plus three
  `references/layouts-*.md` belonging to a different skill.
- Workaround used: recorded the gap in the skill's own `## Provenance` section, with
  the substitute source: `get_theme_css_properties theme=aura` via the `vaadin-skills`
  plugin.
- Evidence: `git ls-files` at `3a9289c15df9e7a7659f0d92fee204ad1dc65c14`;
  `.agents/skills/figma-to-aura-theme/SKILL.md`, the "see `property-values.md`" lines.
- Impact: the specific failure mode is quiet. An agent told "find the closest named
  colour in `property-values.md`" and unable to open it does not stop — it matches
  against remembered colour names instead, and emits a `--aura-accent-color-light`
  that looks deliberate and cites a table nobody can check. Every value the skill
  produces has this property. More generally: copying a skill is not copying a file,
  and a dangling reference inside one is invisible until the step that needs it runs.
- Suggested Vaadin/product improvement: upstream should either ship `property-values.md`
  or stop referencing it. Beyond that, a skill that depends on a bundled reference
  should say what to do when it's missing — "look it up with X" — rather than assuming
  presence; and skill-vendoring tooling should validate that in-skill relative links
  resolve, the way a link checker does for docs.
- Owner / next step: **Answered by the spike (issue #143).** The skill *is* usable
  without `property-values.md` — but only via a substitute it never mentions, and the
  substitute the Provenance section suggested is not sufficient on its own.
  `get_theme_css_properties theme=aura` supplies the *defaults* (base-font-size 14,
  contrast 1, surface level 1 / opacity 0.5, overlay opacity 0.85, and "accent defaults
  to `var(--aura-blue)`") but carries **no palette hex values, no named light/dark
  accent pairs, no background table and no curated font list** — precisely the four
  tables the skill cites. What worked instead: read Aura's own computed properties out
  of the *running app*, where the formulas are visible
  (`--vaadin-radius-m = round(baseRadius*2px + 3px, 1px)`,
  `--vaadin-gap-m = round(baseSize*0.75*1px, 1px)`,
  `--aura-font-size-m = round(baseFontSize/16 * 1rem, 0.0625rem)`), and resolve palette
  colours through a canvas probe. That is how the spike established, without guessing,
  that this design is stock Aura: `#3266e4` is exactly `oklch(0.55 0.2 264)` =
  `--aura-blue`, and `#b3329d` is exactly the default `--vaadin-user-color-2`.
  **No values were invented.** The luck is that the design needed no colour matching at
  all — had its accent been custom, the skill's own path offered only an invented named
  pairing. Two of the skill's stated value sets are also wrong: it claims
  `--aura-base-radius` takes the discrete set `-1, 0, 3, 4, 7` when the docs give a
  0–10 range with a default of 3 (`-1` is outside it); its density set 12/16/20 is a
  subset of the documented 12–24. Report upstream, and add the
  "measure the running app" technique to the project copy.

### F-061 — `get_design_context` on a page node fails with "you have nothing selected", which is not the problem
- Date: 2026-08-26
- Area: Tooling/Template
- Severity: Medium
- Task being attempted: issue #142's acceptance smoke test — `get_design_context` on
  node `88-12278`, the node in the design URL the issue was written against.
- Expected vs actual: expected design context for that node, or a clear refusal.
  Actual: `You currently have nothing selected. You need to select a layer first
  before using this tool.` The `nodeId` **was** supplied and **was** valid — the real
  cause is that `88-12278` is a *canvas* (a page), and `get_design_context` accepts
  only layers. On a page node it silently discards the argument and falls back to the
  desktop app's current selection, then reports the fallback's failure as if it were
  the caller's mistake. `get_metadata` accepts the same node without complaint, which
  is how the page's structure was read instead.
- Workaround used: call `get_metadata` on the page for the hierarchy, then
  `get_design_context` on individual frames inside it (proved on `213:1721`, the "Add
  Expense" dialog — full output, with per-component Vaadin annotations).
- Evidence: Figma Debug UUID `3eddcf4c-ebc5-40c2-903b-83efba47800f`. The same call
  against frame `213:1721` succeeds.
- Impact: the error names a precondition the caller did not violate and cannot fix
  from an MCP client — there is no way to "select a layer" over the remote server, and
  an agent has no selection at all. It reads as "the integration is broken" rather
  than "wrong node type", so the obvious next move is to re-authenticate or re-check
  the MCP setup — exactly the wrong direction. Worse for an agent working from a
  pasted URL: a designer who copies a link while a *page* is active hands over a node
  id that every design-to-code skill will choke on, with an error pointing nowhere
  near the cause. Cost here was a wrong-turn diagnosis on a task whose whole purpose
  was proving the setup worked.
- Suggested Vaadin/product improvement: Figma's — when `nodeId` resolves to a node
  type the tool cannot handle, say so ("node 88:12278 is a CANVAS; get_design_context
  requires a FRAME/COMPONENT/INSTANCE — call get_metadata to list its children"), and
  never silently substitute the current selection for an explicitly-passed argument.
  Falling back to a different input than the one supplied is the root problem; the
  misleading message is the symptom.
- Owner / next step: reported in the issue #142 smoke-test comment. Worth folding the
  "page node → get_metadata first" rule into `figma-to-vaadin` step 1 if a second run
  hits it; upstream's step 1 already suggests `get_metadata` as the fallback for
  truncated responses, but not for this failure.

### F-062 — The Figma Aura kit emits `--lumo-*` custom properties, which are undefined in an Aura app
- Date: 2026-08-26
- Area: AI
- Severity: High
- Task being attempted: issue #142's smoke test — reading `get_design_context` output
  for the "Add Expense" dialog to see what a real design-to-code run would produce.
- Expected vs actual: expected an Aura-themed design to yield `--aura-*` / `--vaadin-*`
  tokens. Actual: the reference code is threaded with `--lumo-*` — `--lumo-font-family`,
  `--lumo-font-size-m`, `--lumo-border-radius-m`, `--lumo-border-radius-l` — every one
  written with a hardcoded fallback, e.g.
  `rounded-[var(--lumo-border-radius-m,9px)]`. The variables genuinely are named that
  way in the shared "Aura / Vaadin Design System" Figma library (the
  `figma-to-aura-theme` skill notes the kit "may label some variables with `lumo-`
  prefixes"), so this is faithful output, not a bug in the export.
- Workaround used: the project-owned `figma-to-vaadin` copy carries a **Project
  overrides** section forbidding `--lumo-*` and naming the `--aura-*` / `--vaadin-*`
  replacements, written before this run and confirmed necessary by it.
- Evidence: `get_design_context` on node `213:1721`; `CLAUDE.md`'s Aura-not-Lumo rule;
  `docs/theming-layouts.md`.
- Impact: this is the worst possible shape for a wrong value. `--lumo-*` is undefined
  under Aura, so it contributes nothing — but every occurrence ships with a plausible
  hardcoded fallback, so the CSS *renders*, at a frozen literal that looks right today
  and silently stops tracking the theme forever. Nothing errors, nothing looks broken,
  and dark mode is where it surfaces — months later, as "some corners are the wrong
  colour". Upstream's own gotcha list warns about exactly this pattern
  (`var(--name, fallback)` where the name doesn't exist) without noticing that the
  Figma kit it reads from is a systematic source of it. Any run that pastes the
  reference code through without the override would seed it across every view.
- Suggested Vaadin/product improvement: rename the variables in the Figma Aura library
  to their `--aura-*` / `--vaadin-*` equivalents, or have the design-to-code path map
  the `lumo-` prefixed kit variables to real Aura properties the way
  `figma-to-aura-theme` says a human should. Failing that, `figma-to-vaadin` should
  state the hazard itself — it is not an app-specific preference, it applies to every
  Aura project consuming this kit.
- Owner / next step: **Re-checked by the spike (issue #143): the guard holds.** A full
  run over both frames produced **zero** `--lumo-*` properties, zero `LumoUtility`, zero
  `LUMO_*` variants and zero `getStyle().set(...)` in the generated Java and CSS. But it
  is doing constant work, not guarding a rare case: both nodes' `get_design_context`
  output is saturated with `--lumo-*` — several dozen occurrences across the two frames,
  every one carrying a hardcoded fallback, across four properties (`--lumo-font-family`,
  `--lumo-font-size-m/-s/-l`, `--lumo-border-radius-m/-l`). Every text node carries at
  least the font-family and font-size pair, so the count scales with the frame. The
  override earns its
  place on literally every run, and any project consuming this kit without an equivalent
  rule will seed frozen literals across its whole UI. Worth reporting upstream — the
  hazard belongs in `figma-to-vaadin` itself, not in each project's copy. A separate
  wrinkle found alongside it: `LumoIcon` is a *third* `lumo`-named thing, and unlike the
  other two it is legitimate — a supported Vaadin 25.2 icon set, present on this Aura
  app's classpath via `vaadin-lumo-theme-25.2.1.jar`, and what the Figma annotations
  correctly prescribe (`lumo:plus`, `lumo:edit`, `lumo:calendar`, …). An agent
  pattern-matching on "lumo" would wrongly swap in `VaadinIcon` and change the rendered
  icon size. It also lives in `com.vaadin.flow.theme.lumo`, not beside `VaadinIcon` in
  `com.vaadin.flow.component.icon` — one compile error's worth of trap.

### F-063 — A Figma file that *consumes* the Aura kit as a library hides its variables from the skill's own mode-detection instruction
- Date: 2026-08-26
- Area: AI
- Severity: High
- Task being attempted: issue #143's spike — Step 1 of `figma-to-aura-theme`, which
  decides `color-scheme` by asking whether the file has one mode or two. The skill's
  literal instruction: "If the file has **multiple modes** (e.g. light/dark), use
  `use_figma` instead, and read each variable's value across all of its modes in one
  pass (`variable.valuesByMode`)".
- Expected vs actual: expected the documented `use_figma` route to enumerate the
  design's modes. Actual: the obvious implementation —
  `figma.variables.getLocalVariableCollectionsAsync()` — returns **only the file's own
  local collections**. In the Expense Manager file that is one collection ("Collection
  1", 1 mode, 9 variables, none of them Aura's). Every Aura variable is **remote**,
  living in the shared "Aura colors" / "Aura sizes" libraries. An agent following the
  instruction literally finds one mode and concludes `color-scheme: light`. The truth
  is that "Aura colors" is a remote collection with **two** modes, Light and Dark.
- Workaround used: don't enumerate collections — enumerate *bindings*. Walk a node's
  `boundVariables`, resolve each id with `getVariableByIdAsync`, then
  `getVariableCollectionByIdAsync`. Remote collections and their `valuesByMode` are
  fully readable that way; 34 bound variables resolved across three collections.
- Evidence: issue #143 spike, file `Irsp3cgi1WX3GiLGpJZECa`. `getLocalVariableCollections`
  → `[{name: "Collection 1", remote: false, modes: ["Mode 1"], varCount: 9}]`. The
  binding walk → `{name: "Aura colors", remote: true, modes: ["Light", "Dark"]}`.
- Impact: the failure is silent and inverts the single most visible theme decision. A
  design that ships both schemes gets an app that only ever renders light, and nothing
  errors — the skill's own rule ("If only one mode exists → implement that mode only")
  fires confidently on a wrong premise. It hits *every* file that consumes the shared
  Aura kit as a library, which is how the kit is meant to be consumed, so this is the
  normal case and not an edge one. Two aggravating factors: you must run the expensive
  `use_figma` path merely to discover whether you needed it, and `use_figma` is a
  **write** tool — the skill routes a read-only variable query through the one Figma
  tool that can mutate the design file.
- Suggested Vaadin/product improvement: the skill should say to resolve variables from
  a node's `boundVariables` rather than from local collections, and should note that
  `get_variable_defs` already flattens aliases to the active mode's value (which is why
  it is useless for mode detection). Better still, Figma should expose a read-only
  variables-with-modes call so design-to-code never needs the write tool for this.
- Owner / next step: reported here; the binding-walk script is in the spike report,
  `docs/figma-toolchain-spike.md`. Worth folding into the project copy of
  `figma-to-aura-theme` before issue #144 depends on it.

### F-064 — The design's own spacing, radius and type values systematically miss the Aura token scale
- Date: 2026-08-26
- Area: UX-spec
- Severity: Medium
- Task being attempted: issue #143's spike — implementing Figma nodes `116:4444` and
  `213:1721` with `--vaadin-*` / `--aura-*` tokens, as `docs/theming-layouts.md`
  requires ("use tokens — not hard-coded px").
- Expected vs actual: expected a design drawn from the Aura Figma kit to land on Aura's
  token scale. Actual: it splits cleanly in two. Everything inherited from a **kit
  component** lands exactly on a token; everything the designer **drew by hand** lands
  between tokens. Measured against the real scale (radius s/m/l = 5/9/15 px, padding
  and gap xs…xl = 4/8/12/16/24 px, font-size xs…xl = 12/13/14/16/18 px):
  | Design value | Where | Nearest tokens | Match |
  |---|---|---|---|
  | 9 px | field radius | `--vaadin-radius-m` = 9 | ✅ |
  | 14 px | field label / input | `--aura-font-size-m` = 14 | ✅ |
  | 13 px | Net/VAT label | `--aura-font-size-s` = 13 | ✅ |
  | 12 px | row subtitle, section label | `--aura-font-size-xs` = 12 | ✅ |
  | 16 px | totals amount | `--aura-font-size-l` = 16 | ✅ |
  | 34 px | button height | derived from base-size 16 | ✅ |
  | **12 px** | card radius | m = 9, l = 15 | ❌ between |
  | **20 px** | card padding, card gap | l = 16, xl = 24 | ❌ between |
  | **40 px** | section gap | xl = 24 | ❌ far off |
  | **24 px** | report title | font xl = **18**, the top of the scale | ❌ exceeds |
  | **15 px** | expense row title | m = 14, l = 16 | ❌ between |
- Workaround used: token values taken throughout, accepting a 1–5 px divergence per
  card and heading, and recorded in the spike report rather than hard-coding raw px.
- Evidence: issue #143 spike; token values read from the running app with
  `getComputedStyle`; design values from `get_design_context` on both nodes.
- Impact: this is a standing tax on every per-view issue, not a one-off. Each view has
  to choose between (a) tokens, and being visibly a few pixels off the design on every
  card and heading, (b) raw px, which renders correctly today and silently stops
  tracking the theme — the exact failure mode F-062 describes, and (c) inventing a
  project-level custom-property scale for the design's own values. Without a decision
  recorded up front, different views will make different choices and the app drifts.
  The title is the sharpest case: 24 px has no Aura token at all, because Aura's type
  scale stops at 18 px, so *every* page heading needs an answer.
- Suggested Vaadin/product improvement: ours, not Vaadin's — pick option (c) and define
  the handful of extra properties once, or get the design corrected to the kit's scale.
  Vaadin's side: the Aura Figma kit could expose the size variables for radius/padding
  so a designer drawing a card picks a token instead of typing `12`.
- Owner / next step: **Decided by issue #144** (`/figma-theme`), recorded in
  `docs/design/foundations/` (radius, spacing, typography), with the rule in ADR-0025. Option (c)
  for the four recurring 3–6px gaps — `--em-card-radius` 12, `--em-card-padding` 20,
  `--em-section-gap` 40, `--em-font-size-title` 24 — plus one exact Aura override
  (`--aura-app-layout-radius: 12px`), and the nearest token for the 1–2px cases (15→16,
  10→8) so the type scale stays the single source for text. **One row of the table above
  is wrong:** 12px is *not* off-scale — it is exactly `--aura-font-size-xs` at base 14
  (see F-068). It was recorded as a match against the app's then-current base of 15,
  which happened to give the same verdict for the wrong reason.

### F-065 — `docs/theming-layouts.md` prescribes `setPadding(String)`, which does not exist
- Date: 2026-08-26
- Area: Docs
- Severity: Medium
- Task being attempted: issue #143's spike — writing card padding the way this repo's
  binding layout standard says to.
- Expected vs actual: `docs/theming-layouts.md` documents
  `layout.setPadding("var(--vaadin-padding-m)")` in **three** places — the decision
  table row "Custom padding value | accepts any CSS value", the ✅ Java example, and the
  ❌ counter-example's fix comment. Actual: it does not compile.
  `ThemableLayout` (vaadin-ordered-layout-flow 25.2.1) declares
  `setSpacing(boolean)`, `setSpacing(String)`, `setSpacing(float, Unit)`,
  `getSpacing()` — and for padding only `setPadding(boolean)`. There is no String
  overload, no `getPadding()`, no `(float, Unit)`. The API is asymmetric; the document
  assumed it was symmetric.
- Workaround used: `setPadding(false)` plus the padding value in the scoped CSS class,
  which the same document's "Falling back to CSS" section already allows.
- Evidence: `javap` on `com/vaadin/flow/component/orderedlayout/ThemableLayout.class`
  from `vaadin-ordered-layout-flow-25.2.1.jar`; compile errors in the spike at
  `SpikeItemCard.java:36` and `SpikeTotalsBox.java:33`.
- Impact: low blast radius so far but a bad shape. Production code calls
  `setSpacing(String)` 12 times and `setPadding(String)` zero times — because it
  cannot — so the error has sat in the standard undetected since it was written, purely
  because nobody followed that row. It is exactly the kind of instruction an agent
  *does* follow literally: the document is named as binding authority by
  `CLAUDE.md` and by the project copy of `figma-to-vaadin`, so every future generated
  view starts by trying the one API call that fails.
- Suggested Vaadin/product improvement: ours — fix the three places in
  `docs/theming-layouts.md` to say padding is boolean-only and custom values go in the
  scoped CSS class. Vaadin's — give `setPadding` the same String/(float, Unit)
  overloads `setSpacing` has; the asymmetry has no obvious justification.
- Owner / next step: **Fixed** — PR #148 (`fix-f065-theming-layouts` → `main`), raised
  separately from the spike branch, which is never merged. All three bad occurrences are
  gone: the decision-table row now says there is no Java API and points at the scoped CSS
  class, the ✅ example uses `setPadding(false)` + `addClassName(...)`, and the ❌
  counter-example's fix comment points at CSS rather than at the missing setter. A new
  "The spacing/padding asymmetry" section shows the real signatures so the next reader
  sees why, and the CSS fallback table gains a "Custom padding on any layout" row.
  Verified: `grep -rn 'setPadding("' src/main/java/` returns nothing, so the document and
  the codebase now agree. Still open upstream — Vaadin giving `setPadding` the same
  `String` / `(float, Unit)` overloads `setSpacing` has would remove the trap at source.

### F-066 — `figma-to-vaadin` has no "this view already exists" branch, so on a mature app it generates a rival implementation
- Date: 2026-08-26
- Area: AI
- Severity: High
- Task being attempted: issue #143's spike — running `figma-to-vaadin` against the two
  frames the design provides for screens this app already ships.
- Expected vs actual: expected a design-to-code skill aimed at an existing codebase to
  reconcile with what is there. Actual: its workflow is unconditionally *implement* —
  fetch context, check annotations, research components, resolve preferences, write
  code, verify. No step asks whether the route, view or dialog already exists. Figma
  node `116:4444` is the report detail screen, which the app implements in
  `report/ui/ReportDetailView.java` (75 KB); node `213:1721` is "Add Expense", which
  the app implements in `report/ui/LineEditorDialog.java` (20 KB). Run as written, the
  skill produces a second, thinner implementation of both.
- Workaround used: the spike's output was written to a `report.ui.spike` package at
  `/spike/report/<id>`, deliberately parallel to the real views, and the useful output
  treated as the **delta** rather than the code.
- Evidence: issue #143 spike; `docs/figma-toolchain-spike.md` lists the delta.
- Impact: the generated view *looks* closer to the design than the real one, because it
  does far less — no review mode, no optimistic locking, no receipt validation, no
  quantity overrides, no status transitions. That is a genuinely dangerous comparison to
  put in front of a reviewer: the honest reading is "the design is simpler than the
  app", not "the generated code is better". Left unnoticed, the natural next step is to
  keep the generated file, and a 75 KB view's behaviour is quietly lost. The framing
  matters for the whole `#142` follow-up series: the per-view issues are **restyling and
  reconciliation** tasks against existing views, and this skill is built for greenfield.
- Suggested Vaadin/product improvement: add a step between "fetch design context" and
  "implement" — search the codebase for an existing view serving the same route or
  entity, and if one exists, produce a diff against it rather than a new class. Failing
  that, the skill should at least say which of the two it is doing.
- Owner / next step: the per-view issues should be written as "change view X to match
  frame Y", never "implement frame Y". Worth adding the guard to the project copy.

### F-067 — Figma's Vaadin annotations record the button *variant* but drop its accent scoping, and the skill's own precedence rule then points the wrong way
- Date: 2026-08-26
- Area: AI
- Severity: Medium
- Task being attempted: issue #143's spike — mapping the design's Save and Submit
  buttons to Vaadin.
- Expected vs actual: the design paints both buttons near-black (`#0a0b0d`, bound to
  the kit's `Accent colors/Accent neutral`), and the Figma **variant name** is
  `Color=Accent neutral`. The **annotation** on the same node says
  `Vaadin component: <vaadin-button theme="primary">`. Under Aura, `PRIMARY` alone
  renders in the accent colour — blue. So the annotation is a faithful record of the
  variant and a wrong record of the colour, and following it produces a blue button
  where the design is black.
- Workaround used: `ButtonVariant.PRIMARY` plus Aura's `aura-accent-neutral` utility
  class, which scopes the accent for that subtree. Verified in the browser: the
  rendered background is `oklch(0.15 0.0038 248)` with white text.
- Evidence: `get_design_context` on nodes `213:1721` and `116:4446`; annotations on
  `132:384` / `132:385`; the design's own fill `bg-[var(--accent-colors\/accent-neutral,#0a0b0d)]`.
- Impact: the skill's step 2 says "Annotations override guesses from layer names" — a
  sound rule that is exactly backwards here, because the **layer name** carries the
  information the annotation lost. The same pattern appears twice more in these two
  frames: node `143:2109` is annotated `theme="tertiary icon"`, and `icon` is Lumo-only
  and silently does nothing under Aura (F-017); and Unit Price is annotated
  `<vaadin-text-field>` when it is a currency amount the app already edits with a
  `BigDecimalField`. So three of the annotations in two frames need overriding. They
  remain far better than guessing — every input carries a component name — but they
  cannot be treated as final.
- Suggested Vaadin/product improvement: Figma's Vaadin kit should emit the accent
  scoping alongside the theme variant (`theme="primary" class="aura-accent-neutral"`),
  since the variant axis is literally named `Color=`. The skill should soften
  "annotations override" to "annotations override layer names for *component choice*;
  check the layer name and the rendered fill for *colour*".
- Owner / next step: captured in `docs/figma-toolchain-spike.md`; worth a note in the
  project copy's Project overrides section next to the existing `LUMO_*` rule.
  **Superseded globally by issue #144:** the theme now scopes the accent to neutral for
  every non-tertiary button, reaching the same `oklch(0.15 0.0038 248)` without the
  per-button class. Per-view code should *not* add `aura-accent-neutral` — the annotation
  is still wrong about the colour, but the app no longer needs a per-button remedy.

### F-068 — Aura's docs give the wrong default for `--aura-font-size-xs`, on the design's most-used text size
- Date: 2026-08-27
- Area: Docs
- Severity: Medium
- Task being attempted: issue #144 — `/figma-theme` step 6 looks each Aura default up
  with `get_theme_css_properties theme=aura` before writing the theme, precisely so a
  declaration never merely re-asserts a default.
- Expected vs actual: expected the documented font-size defaults to match the running
  app. Actual: the docs state `--aura-font-size-xs` "default corresponds to `11px`". The
  formula and the running app both give **12px** at the default base of 14. The other
  four steps (13/14/16/18) match. Read from the app, the real derivation is
  `clamp(0.625rem, round(font-size-m * 0.85, 0.0625rem), 0.8125rem)` — `0.875 × 0.85 =
  0.74375rem`, which rounds to `0.75rem` = 12px, inside the clamp.
- Workaround used: trusted the measurement over the docs, and recorded both the formula
  and the discrepancy in `docs/design/tokens/token-reference.md` so the next run does
  not re-derive it.
- Evidence: `get_theme_css_properties theme=aura vaadin_version=25.2` ("The default
  corresponds to `11px`") against a probe element on the running app at 25.2.1 returning
  `12px`; the design's own local variable set names `XS: 11`, `S: 12`.
- Impact: a 1px docs error, but it landed on the single most consequential value in this
  design. 12px is the design's most-used text size — 28 of 65 text nodes in frame
  `116:4444`. Trusting the docs would have classified all 28 as **off-scale**, and the
  recorded decision would then have been either a fifth `--em-*` property or an accepted
  divergence on every caption and section label in the app — a fabricated problem with a
  real cost, defended by a citation. It also nearly inverted the base-font-size decision:
  scored against the wrong scale, base 15 looked like the better fit (44 nodes exact vs
  20); scored against the real one, base 14 wins outright (48 vs 17). The general shape:
  an authoritative-looking default is more dangerous than a missing one, because nobody
  measures what the docs already answered.
- Suggested Vaadin/product improvement: Vaadin's — correct the `--aura-font-size-xs`
  default in the Aura typography docs, and publish the derivation for all five steps.
  The scale is not a geometric ramp (`xs` is a clamped 0.85 of `m`, `s` is the
  *midpoint* of `m` and `xs`), so a reader who infers a constant ratio from one sample
  gets two steps wrong. Better still, ship the resolved table per base size.
- Owner / next step: recorded in `docs/design/tokens/token-reference.md`, with
  all five formulas and an explicit warning that the docs disagree. Worth reporting
  upstream to the Vaadin docs team.

### F-069 — The design renders three font families while declaring one, so "the design is the source of truth" needs a carve-out
- Date: 2026-08-27
- Area: UX-spec
- Severity: Medium
- Task being attempted: issue #144 — reading frame `116:4444`'s global typography to
  settle `--aura-font-family`.
- Expected vs actual: expected the frame's text to use the family its own variable
  declares. Actual: `Typography/Font-family` (a bound Aura kit variable) and the local
  `Font` variable both say **Instrument Sans**, and the frame's 65 text nodes render in
  **three** families — Instrument Sans (11 nodes), **Inter** (44), and **Public Sans**
  (10). The kit-driven text is Instrument Sans; the hand-drawn text is not.
- Workaround used: took the *variable* as authoritative and left `--aura-font-family` at
  the Aura default (Instrument Sans, which is what the variable names). The stray
  families are reported as design defects rather than reproduced.
- Evidence: a `use_figma` binding walk over the frame's subtree returning a
  `fontName.family + style` histogram — `Inter Bold` 18, `Inter Regular` 20,
  `Public Sans Regular` 7, `Public Sans SemiBold` 3, against `Typography/Font-family:
  "Instrument Sans"` from the remote "Aura sizes" collection.
- Impact: this is the finding that forced a decision into ADR-0025 rather than a
  workaround into one issue. A literal reading of "Figma is the source of truth" would
  have shipped three font families, or picked whichever family the majority of nodes
  happened to use — which here is Inter, i.e. exactly the value the design was supposed
  to be replacing. The same split shows up in spacing (F-064): values inherited from a
  kit component land on the token scale, values typed by hand do not. So the useful rule
  is not "the design wins" but **"the design's variables and kit components win; its
  hand-drawn values are a proposal to reconcile against the scale"** (ADR-0025 decision
  4). Without that, a design-to-code toolchain faithfully reproduces a designer's
  leftovers and calls it fidelity.
- Suggested Vaadin/product improvement: partly ours — the design needs its stray text
  nodes rebound to the typography variables. Vaadin's side: the Aura Figma kit could
  expose typography as text *styles* rather than only as variables, so hand-drawn text
  cannot silently drift off the declared family; and `get_design_context` could flag
  when a node's rendered value contradicts a variable bound in the same file, which is
  the machine-checkable version of this whole finding.
- Owner / next step: recorded in `docs/design/foundations/typography.md` and as
  ADR-0025 decision 4. The design fix is the designer's; raise the stray families and
  the design's own 12px "S" step (against Aura's 13px `s`) with them.

### F-070 — Spring Security proxies the view, so the class the navigation chain reports is a CGLIB subclass and `Class` equality silently matches nothing
- Date: 2026-08-27
- Area: Vaadin
- Severity: Medium
- Task being attempted: issue #146 — lighting the correct nav group for the current
  route, by matching `AfterNavigationEvent.getActiveChain().get(0).getClass()` against
  the view classes a `NavGroup` lists.
- Expected vs actual: expected `MyReportsView.class`. Actual
  `MyReportsView$$SpringCGLIB$$0` — every view in this app carries `@RolesAllowed` or
  `@PermitAll` at class level (ADR-0008), so Spring method security proxies it. The
  `equals` comparison matched nothing at all, for every route.
- Workaround used: `isAssignableFrom` instead of `equals` in `NavGroup#contains`, with
  the reason written next to it so nobody "simplifies" it back.
- Evidence: a temporary probe printing the chain's class from `MainLayout#afterNavigation`
  on the running app — `view=class com.vaadin.expensemanager.report.ui.MyReportsView$$SpringCGLIB$$0`.
  The failing assertion was `NavigationShellUiTest#currentGroupFollowsTheGroupNotTheRoute`.
- Impact: this is a **silent, total** failure of a feature that has no error path: the nav
  simply never marks anything current, and every route looks like an unclassified one.
  There is no exception, no log line, and no compile-time signal — the types are correct.
  Two things made it easy to walk into. First, the code it replaced never met it:
  `MenuEntry#menuClass()` reports the *declared* class, so the old `@Menu`-driven side
  nav compared proxies to nothing. Second, a browserless test reproduces it exactly, but
  only if the test asserts the *current* state rather than the presence of the items —
  the four tests that only checked which links render all passed.
- Suggested Vaadin/product improvement: Vaadin's — `AfterNavigationEvent` (and
  `getActiveChain()`) could expose the *route target* class rather than the runtime one,
  or offer `getNavigationTarget()` alongside, since the router already knows the
  registered class. Failing that, the Flow/Spring docs should say plainly that a view
  annotated for method security arrives proxied, next to the security annotations that
  cause it. This will bite anything that keys off the view class — analytics, per-view
  layout switches, breadcrumbs.
- Owner / next step: fixed in `NavGroup#contains`, and covered by
  `NavigationShellUiTest#currentGroupFollowsTheGroupNotTheRoute`. Worth reporting
  upstream to Flow.

### F-071 — A `ContextMenu`'s items are invisible to the browserless tester, so navigation behind a menu cannot be asserted from the rendered tree
- Date: 2026-08-27
- Area: Verification
- Severity: Low
- Task being attempted: issue #146 — replacing four view tests that asserted "an admin
  sees this view in the navigation, a plain user does not" against the old `SideNavItem`
  list.
- Expected vs actual: expected `find(RouterLink.class).all()` to return the links inside
  the two nav group menus. Actual: it returns only the two links in the light DOM (the
  logo and the single-entry group). A `ContextMenu`'s items are attached as a virtual
  child of its target and never appear in the component tree the tester walks, open or
  closed.
- Workaround used: assert against the model the shell renders from —
  `NavGroup.allVisibleTo(authenticationContext)` — and leave the rendered menu to visual
  verification. The access filtering itself is unit-tested against the real
  `AccessAnnotationChecker` in `NavGroupTest`.
- Evidence: a probe test on the running Spring context printing every discoverable
  `RouterLink` while signed in as an admin: two results, where the bar renders eight
  destinations.
- Impact: modest but worth knowing before designing the assertion, not after. The
  practical rule: anything an overlay owns — `ContextMenu`, `MenuBar` submenus, `Dialog`
  content before it opens — is outside what a browserless test can see, so a test that
  needs it either drives the component's own API or moves the guarantee into a model that
  can be tested directly. Choosing the latter is not a weaker test here, but it does move
  the "did we render it" half onto the visual pass, which is worth saying out loud rather
  than quietly losing.
- Suggested Vaadin/product improvement: Vaadin's — the browserless tester could expose
  overlay content through the owning component (a `find(..., withinOverlayOf(menu))`
  scope, or simply including virtual children in the walk). The `MenuBar` API is
  reachable from Java (`getItems().getSubMenu()`), so the information exists; only the
  locator DSL cannot reach it.
- Owner / next step: recorded here; the four tests now assert the model. Worth raising
  with the browserless-testing maintainers.

### F-072 — A container's `color` never reaches an `h1`–`h6` or `a`: Aura re-declares it at element level, and inheritance loses to any matching rule
- Date: 2026-08-28
- Area: Vaadin
- Severity: Medium
- Task being attempted: issue #146's app shell — a coral header bar that sets its text
  colour once, on the bar, and lets the logo, the nav pills and the greeting inherit it.
- Expected vs actual: expected `.app-header { color: var(--em-header-text-color) }` to
  reach the greeting `H1` inside it, since `.app-header` (specificity 0,1,0) far outranks
  Aura's `:where(h1,h2,h3,h4,h5,h6) { color: var(--vaadin-text-color) }` (specificity
  **0,0,0**). Actual: the greeting rendered black on coral. Specificity is not the
  comparison being made — the bar's white arrives at the `H1` by *inheritance*, and an
  inherited value loses to **any** declaration that matches the element itself, at any
  specificity, zero included. `:where()` resets are built to be easy to override, but only
  by a rule targeting the element; an ancestor's colour is not that.
- Workaround used: `color: inherit` on the affected element's own class — a matching
  declaration that opts back into inheritance. `styles.css:.app-header__greeting` and
  `styles.css:.summary-heading`.
- Evidence: `:where(h1,h2,h3,h4,h5,h6){color:var(--vaadin-text-color)}` in
  `META-INF/resources/aura/aura.css` (`vaadin-aura-theme-25.2.5.jar`);
  `styles.css:383` (`.app-header`), `styles.css:530` (`.app-header__greeting`);
  `styles.css:88` (`.error-summary`), `styles.css:103` (`.summary-heading`);
  `AppHeader.java:55`, `ErrorSummary.java:80`.
- Impact: **two instances shipped, found by audit, not by looking.** The header greeting
  (white → black on coral) and the validation summary's heading (`--aura-red-text` →
  `--vaadin-text-color` inside a red callout). Both had the same signature: the class
  overrides *some* of what Aura resets on that element — `.app-header__greeting` sets
  `font-size`, `line-height` and `font-weight`, `.summary-heading` sets `font-weight` and
  `font-size` — and not `color`, while an ancestor declares it. In both, the sibling `a`
  was handled (`.app-header__logo { color: inherit }`, `.error-summary-link` declares the
  token) and the heading was not, so the author had met the mechanism and not generalised
  it. This is a third way Aura fails silently, beside F-062's undefined-property-frozen-
  at-a-fallback and F-013/F-017's accepted-and-ignored variant: here the declaration is
  valid, defined and applied, and simply never reaches the element that needed it.
  Worst of all, an accessibility gate **prefers the bug** — black on `#f16c4e` measures
  6.99:1, the design's white 3.00:1 — so a contrast pass is not merely silent but
  actively wrong, and nothing in a build or a test run mentions any of it.
- Suggested Vaadin/product improvement: Aura's element-level resets are load-bearing for
  unstyled markup and should not go. But `color` is the one property whose reset routinely
  fights an app's own container colour, and Aura already re-declares it on exactly two
  subjects — `h1`–`h6` and `a:any-link`. Documenting that pair in the theming docs as
  "does not inherit your colour; re-declare it" would cost a sentence. A stronger fix:
  reset only what typography needs (`font-size`, `font-weight`, `line-height`) and leave
  `color` to inherit, letting `--vaadin-text-color` reach headings from `:root` the way it
  reaches every `Span`.
- Owner / next step: both instances fixed; recorded in `app-shell.md` and
  `error-summary.md`; the general rule and the audit that finds it are in
  `docs/theming-layouts.md` § *Inherited properties*. Worth raising with the Aura team as
  a docs gap at minimum.

### F-073 — A design frame's sample data can contradict its own copy, and a survey that reads the mock as spec implements the wrong rule
- Date: 2026-08-28
- Area: Process / Figma
- Severity: Medium
- Task being attempted: surveying frame `116:2499` "My Expenses" (the report list) for
  issue #147, whose acceptance criteria required the "Needs you" metric definition to be
  *confirmed against the design* rather than assumed.
- Expected vs actual: expected the frame to state one definition. It states two. The
  metric card reads `2 · €1234.00 · 1 rejected` and the shell greeting reads "2 items
  need you attention", which together say drafts + rejected with rejected broken out.
  But the frame's own card placement says the opposite: the two cards drawn under
  `section-attention` carry **Submitted** and **Approved** badges, while the **Rejected**
  card sits under `section-submitted-closed`. Read literally, "needs your attention"
  would mean submitted + approved and exclude rejected outright — the inverse of the
  metric directly above it, and the inverse of the app's existing
  `ReportStatus.isEditable()` grouping.
- Why it is easy to get wrong: the placement is the more *concrete* signal. Copy can be
  read as loose, but three cards sorted into two named sections looks like a decision
  someone made. It is not: mock rows are dressed to show every badge colour once, so the
  distribution is chosen for visual coverage rather than for semantics. Nothing in the
  frame marks which of the two signals is load-bearing.
- Resolution: copy wins over sample data. Two independent pieces of *prose* agreed
  (metric sub-line, greeting), the app already implemented that rule, and the placement
  had an obvious non-semantic motive. Recorded as settled in `metric-card.md` with the
  contradiction named, so the next survey does not reopen it.
- The general rule this yields: **a frame's copy and structure are specification; its
  row-level content is illustration.** Take counts, statuses and groupings from labels,
  captions and layer names, never from which mock record landed in which group. Where
  only the content answers a question, the question is unanswered — raise it with the
  designer rather than inferring.
- Suggested Vaadin/product improvement: none for Vaadin. For the design process: a frame
  whose mock data is deliberately unrepresentative should say so in a note, the way an
  annotation marks a component. Cheaper than either side guessing.
- Owner / next step: settled for #147; raised with the designer in the survey follow-up
  issue alongside the "CLOSEd" casing typo, the "need you attention" wording, and the
  20/24/28 display ramp.
- **Second instance, 2026-09-02** — frame `116:4444` (report detail), surveyed for #172.
  The same failure mode, and this time the mock is arithmetically checkable, which makes it
  a better example than the original: every one of five rows repeats
  `net €79.68 · VAT €20.32 (25.5 %)` against five different amounts (those two figures sum
  to €100.00, which is no row and not the total), the string appears on the **per-diem**
  row where a statutory tax-free allowance can carry no VAT at all, and the grand total is
  €5468.00 where the rows sum to €5602.00 — short by exactly the €134.00 parking line.
  That last one is the trap: an exact match is precisely what makes a mock look
  authoritative, and "the total excludes a trip's parking fee" is a plausible *domain rule*
  rather than an obvious typo. It is not one — the app counts parking, it is VAT-bearing at
  the parking type's rate — but a survey that took the arithmetic as spec would have
  changed `currentGrandTotal()`. Raised as a question rather than a defect in #173, because
  only the designer can say which it was. The rule above holds unchanged: **copy and
  structure are specification, row content is illustration** — and now, so are the sums.

### F-074 — The browserless locator's `atIndex` is 1-based, so the idiomatic Java `atIndex(0)` throws
- Date: 2026-08-28
- Area: Verification
- Severity: Low
- Task being attempted: issue #162's collapsible sections — closing the *first* of two
  `Details` from a browserless test and asserting the count survives the collapse.
- Expected vs actual: expected `findDetails().withClassName("report-list-section")
  .atIndex(0)` to select the first match, the way every index in Java's own collection
  and stream APIs does. Actual: `IllegalArgumentException: Index must be greater than
  zero, but was 0` — the locator counts from 1, so the first element is `atIndex(1)`.
- Workaround used: `atIndex(1)`, with a one-line comment at the call site so the next
  reader does not "fix" it back.
- Evidence: `com.vaadin.browserless.locator.Locator.atIndex(Locator.java:269)`,
  `browserless-test-shared-1.1.2`; `MyReportsViewUiTest
  #bothSectionsStartExpandedAndCollapsingOneKeepsItsCountVisible`.
- Impact: one failed test run, no more — the exception fires immediately and names the
  rule, so this is the cheap kind of surprise. Logged because the sibling APIs a test
  reaches for in the same breath (`components()`, `List::get`, `Stream::skip`) are all
  0-based, so the mismatch will recur for every author who meets it, and because an agent
  writing the call has no signal short of running it.
- Suggested Vaadin/product improvement: either accept 0-based indices (the Java
  convention, and what the surrounding APIs use) or say so in the method's javadoc —
  neither the name nor the signature hints at 1-based counting. Failing both, `first()`
  and `last()` helpers would cover the common cases without an index at all.
- Owner / next step: worked around in the test; worth reporting upstream to the
  browserless-test maintainers as an API-consistency nit.

### F-075 — Vaadin 25 bundles no Lucide and no Aura-native icon set, and a sprite addressed by `symbol` silently drops every presentation attribute
- Date: 2026-09-01
- Area: Vaadin / Design toolchain
- Severity: Medium
- Task being attempted: issue #163 — replacing all 25 `VaadinIcon` call sites and five
  `"vaadin:name"` strings with Lucide, the icon set the Figma Aura kit draws with.
- Expected vs actual, part 1 (**the set**): expected a Vaadin 25.2 app themed with Aura to
  have an icon collection matching the design kit its own Figma library ships. Actual:
  Vaadin 25.2 bundles exactly two collections, `VaadinIcon` and `LumoIcon`, both Lumo-era
  — filled, heavier glyphs on a 16px grid. Aura has **no** icon set of its own. The Figma
  Aura kit's hand-placed glyphs are Lucide (24px, 2px outline stroke). So an Aura app
  built from an Aura design has to vendor a third-party set before it can draw a single
  glyph that matches the design, and the docs' "Custom Icon Collection APIs" page is the
  only pointer that this is expected of you.
- Expected vs actual, part 2 (**the sprite**, the expensive half): the docs endorse
  wrapping a third-party set in a project-local enum, and `SvgIcon(String src, String
  symbol)` reads as the sprite-sheet constructor. Expected `vaadin-icon` to fetch the
  sprite and honour the file's own `stroke` / `stroke-width` / `viewBox`, the way it
  visibly does for a single-file `src`. Actual: in `vaadin-icon-mixin`'s `__srcChanged`,
  `(symbol || src.includes('#'))` takes a branch that sets
  `<use href="sprite.svg#id">` and **never fetches the file**. Only the *other* branch —
  plain `src`, one file per glyph — parses the response and copies `viewBox`, `fill`,
  `stroke`, `stroke-width`, `stroke-linecap` and `stroke-linejoin` off the file's root
  `<svg>` onto the rendered one. Down the sprite branch all six are `undefined` and
  `ifDefined` omits them.
- Why it bites: a Lucide glyph is *only* strokes — `fill="none" stroke="currentColor"
  stroke-width="2"` on the root and bare `<path d="…">` inside. Vendor such a file as a
  `<symbol>` unchanged and the sprite branch renders it with no stroke at all: the glyph
  is invisible, or (with a `fill` inherited from elsewhere) a black blob that looks
  plausible in light mode and disappears in dark. Nothing errors, nothing logs. It is the
  same silent-success shape as F-062, one layer down.
- Workaround used: put the presentation attributes on **each `<symbol>`** rather than the
  sprite's root, and assert them per symbol in `LucideIconTest` rather than trusting the
  generator. `currentColor` does survive the external reference — it resolves against the
  `<use>` element's context — so one sprite serves both colour schemes once the
  attributes are in the right place. Verified in the browser in both schemes: the same
  `plus` symbol renders white on the black primary button and dark on its white dark-mode
  counterpart.
- A second, quieter coupling: with no `viewBox` read from the file, the rendered viewBox
  is `0 0 ${size}` from `vaadin-icon`'s `size` property, which **defaults to 24**. Lucide
  is a 24 grid, so they agree — by coincidence of matching defaults on both sides, not by
  anything either declares. A 16- or 20-grid set vendored the same way would render
  cropped or padded with no clue why. Pinned by a test.
- Evidence: `@vaadin/icon@25.2.1/src/vaadin-icon-mixin.js` `__srcChanged` (the
  `if (!src.startsWith('data:') && (symbol || src.includes('#')))` branch) and
  `vaadin-icon.js` `render()` (`fill="${ifDefined(this.__fill)}"` and siblings, plus
  `viewBox="${this.__viewBox || \`0 0 ${this.size} ${this.size}\`}"`);
  `src/main/resources/META-INF/resources/icons/lucide.svg`; ADR-0026.
- Impact: the visible cost was one design deviation per view survey (#162 recorded two
  invented glyphs before the set was switched) and, for #163, half a day resolving what
  the design actually draws. The sprite behaviour cost the sharper risk: had the symbols
  been vendored verbatim, the suite would have stayed green and the icons would have
  looked correct until someone opened dark mode.
- Suggested Vaadin/product improvement: three, in order of value. (1) Ship an Aura icon
  set, or state in the Aura docs which existing collection is intended — an Aura app
  currently has no answer to "which icons". (2) Document that `symbol` renders an external
  `<use>` and consequently ignores the source file's presentation attributes; today the
  two branches of `__srcChanged` behave very differently and the API surface
  (`SvgIcon(src, symbol)` vs `SvgIcon(src)`) does not hint at it. Better still, read the
  attributes in both branches. (3) Warn when a `<use>` resolves to nothing — a mistyped
  symbol is currently an empty box and total silence, which is what forces an enum plus a
  test to get a compile-time-ish guarantee.
- Owner / next step: worked around and covered by `LucideIconTest`; (1) and (2) are worth
  raising with the Vaadin design-system and Flow-components teams respectively. The
  invention ledger in ADR-0026 tracks the design-side half — 13 glyphs picked without a
  drawn reference — and shrinks as views get designed.

### F-076 — A custom-property name in an SVG comment makes the sprite malformed XML, and every icon in the app disappears with no console error
- Date: 2026-09-02
- Area: Vaadin / Tooling
- Severity: Medium
- Task being attempted: applying the iconography survey's decision to move stroke width out
  of the sprite's `<symbol>`s and onto the theme's `--vaadin-icon-stroke-width`. The edit
  removed the attribute and added a comment to the sprite explaining why it must not come
  back.
- Expected vs actual: expected a comment to be inert. Actual: every icon in the app
  vanished. **An XML comment may not contain the string `--`**, and the comment named the
  property in full — `--vaadin-icon-stroke-width`. That made `lucide.svg` malformed XML, so
  the browser's SVG parse failed, every external `<use href="icons/lucide.svg#…">` resolved
  to nothing, and 22 icons rendered as empty boxes.
- Why it is worse than a normal typo: **nothing reports it.** The console showed zero
  errors and zero warnings — the parse failure happens inside the browser's own SVG
  resolution for a `<use>` reference, not in a fetch, so there is no network error and no
  script error. The file still serves `200 image/svg+xml`. `getComputedStyle` on the
  `<svg>` and on the `<use>` both still reported `stroke-width: 2px`, because the *host*
  document's CSS is fine; it is the *referenced* document that failed to parse. Every
  signal an agent would reach for says the icons are working.
- The one signal that did show it: `use.getBBox()` returned `0×0` where it had previously
  returned real geometry. Worth remembering as the cheap probe for "did this `<use>`
  actually resolve" — it is the only in-page check that distinguishes a resolved reference
  from a silently failed one.
- Workaround used: name custom properties in the sprite's comments **without their leading
  dashes**, with a line in the comment saying why. Then the real fix: a test that parses the
  sprite as XML. Every other assertion in `LucideIconTest` reads the file as **text**, so
  all 53 of them passed on a file no browser could parse — which is precisely the shape of
  gap a test suite is supposed to close. `theSpriteIsWellFormedXml` now fails with the
  parser's own message ("The string \"--\" is not permitted within comments"), verified by
  replanting the bug.
- Evidence: `src/main/resources/META-INF/resources/icons/lucide.svg`;
  `LucideIconTest#theSpriteIsWellFormedXml`; ADR-0026 decision 4 as amended;
  `docs/design/foundations/iconography.md`.
- Impact: caught in the browser within minutes *because* the change was visually verified —
  and it would not have been caught otherwise. A green 522-test suite, a 200 response and a
  clean console all agreed the icons were fine. This is the third silent-styling mechanism
  logged in this area (F-062 frozen fallbacks, F-072 inheritance losing to an element-level
  rule, F-075 the sprite branch reading no attributes) and the first where the *asset* is
  the thing that fails.
- Suggested Vaadin/product improvement: `vaadin-icon` should warn when a `<use>` reference
  resolves to nothing — it already logs `Error loading icon` for a failed fetch in the
  single-file branch, so the sprite branch is the asymmetric one. A one-line
  `console.warn` on an empty bounding box after load would turn this class of bug from
  invisible to obvious, and would also catch the mistyped-symbol case that forced
  `LucideIcon` to be an enum in the first place.
- Owner / next step: fixed and covered by a test. The Vaadin suggestion is worth filing
  alongside F-075's, since both are about the sprite branch failing quietly.

### F-077 — A component spec's "Tokens used" table cannot distinguish what the theme already does from what you must declare, so it reads as five times more CSS than the view needs
- Date: 2026-09-02
- Area: Design toolchain (`figma-survey` spec format)
- Severity: Low
- Task being attempted: issue #169 — building the reference-table design, whose tab bar
  spec (`docs/design/components/reference-tabs.md`) carries an eleven-row *Tokens used*
  table: bar background, bar radius, bar padding, tab radius, tab padding, label size and
  weight, unselected and selected label colour, selected fill, selected border, selected
  shadow.
- Expected vs actual: the table reads as a list of things the implementation must set —
  it is written as `Part | Token | Design value`, with no column for *who supplies it*.
  Implementing it literally means writing eleven declarations. In fact Aura's stock
  `vaadin-tabs` already renders **nine of the eleven** exactly as specified: it sets the
  bar's background to `--vaadin-background-container`, derives the bar radius as
  `--vaadin-tab-border-radius + --vaadin-tabs-padding`, puts the tab corner on
  `--vaadin-radius-m`, sets the label to `--aura-font-weight-medium`, and gives
  `vaadin-tab[selected]` an `--aura-surface-color` fill, a 1px border and
  `--aura-shadow-s`. Only two rows genuinely differ from the theme — the bar's padding
  (Aura 3px, design 4px) and the two label colours (Aura uses `--vaadin-text-color` /
  `--aura-accent-text-color`, the design wants secondary / plain text).
- Why it happened, and why it is not the surveyor's fault: a survey never boots the app
  (that is the deliberate split with `/figma-theme`), and reading `aura.css` in a jar to
  work out which of eleven values the theme already produces is exactly the kind of
  resolved-value work the spec format hands to a running app. The table is *correct* about
  every value; it simply cannot say which ones are free.
- Cost when implemented literally: the first draft of `.reference-tabs` re-declared all of
  it — a background, a radius, a padding, a per-tab radius and padding, a label font-size
  and weight, and two `vaadin-tab` rules. That is nine declarations fighting rules that
  already agreed with them, and every one is a place the app stops tracking the theme. It
  also nearly hid the two real divergences in the noise.
- Evidence: `styles.css` § *Reference tables* as committed (three declarations plus one
  `a:any-link` rule, against a nine-declaration first draft);
  `docs/design/components/reference-tabs.md` § *Tokens used*;
  `vaadin-aura-theme-25.2.1.jar!/META-INF/resources/aura/aura.css` rules for
  `vaadin-tabs` and `vaadin-tab[selected]`.
- Suggested improvement: give the *Tokens used* table a **Source** column with three
  values — `theme` (Aura already produces this; assert it, do not declare it), `declare`
  (the theme differs, the app must set it), `unverified` (nobody has checked). A survey can
  fill `unverified` honestly for every row without booting anything, and whoever
  implements or runs `/figma-theme` promotes each row to `theme` or `declare` once a
  browser has answered. That turns the eleven-row table from a work list into a checklist
  whose two interesting rows are visible, and it is the same "resolved values are the
  running app's to supply" split the folder already makes for the global theme.
- Owner / next step: a `figma-survey` change. Not done here — this issue only found the
  problem, and altering the spec format mid-implementation is how a contract becomes a
  transcript. Note that the same shape applies to `row-action-menu.md`, whose "No property
  is overridden" line turned out to be right for a different reason than it implies: Aura
  needed a variant (`TERTIARY`) and a chevron suppressed, neither of which is a property.

### F-078 — A `drifted` spec can only say "these values differ", so a design that re-cuts a component reads as two stale tokens

- Date: 2026-09-02
- Area: Design toolchain (`figma-survey` spec format)
- Severity: Medium
- Task being attempted: surveying frame `116:4444` (report detail) for #172, against four
  component specs that had carried `Implementation: drifted` since #144.
- Expected vs actual: expected to reconcile a handful of token values. All four files —
  `expense-line-card`, `travel-card`, `totals-card`, `status-history` — carried an
  *identical* two-row Divergence table: radius 12 vs 15, padding 20 vs 16, with the note
  "tokens for the design's values exist and are deliberately unreferenced until per-view
  work consumes them". Read on its own, each file says the component is two custom
  properties away from conforming. In fact the design had **re-cut the structure**: one card
  per *section* holding the lines as rows separated by rules, where every one of those specs
  describes one bordered card per line. `expense-line-card` was not a drifted card. It was
  no longer a card.
- Why the format hides it: `drifted` and its Divergence table are shaped as
  *property* comparisons — a two-column "Design | Code" grid of values. There is no row
  shape that can say "the Anatomy section is describing the wrong thing", and the *Anatomy*
  and *Tokens used* sections carry no status of their own, so they read as current whatever
  the Divergence table says. Worse, the four identical tables made the drift look like one
  known, bounded, deferred decision (adopt `--em-card-radius`/`--em-card-padding`) rather
  than four files needing rewriting.
- Cost: the risk is entirely on the *next* reader, not on this survey. An implementer
  picking up any one of those files — or an agent asked to "close the card-radius drift" —
  would have written the two declarations, seen them match the design's values, and shipped
  one-card-per-line with correct tokens. Nothing in the file, the build or a test would
  have contradicted them. The structural change is only visible by re-reading the frame,
  which is the one thing a spec exists to make unnecessary.
- Workaround used: rewrote the four files rather than amending them, added
  `expense-item-card` for the section card, and said so explicitly in the
  `components/README.md` note — "four were rewritten rather than amended" — so the churn is
  legible as a re-cut and not as five separate edits.
- Suggested improvement: give the Divergence section a **kind**, not just rows. Something
  as small as a leading line — `Kind: values` vs `Kind: structure` — would separate "adopt
  two tokens" from "this anatomy is superseded", and a structural divergence should
  additionally invalidate the *Anatomy* and *Tokens used* sections rather than leaving them
  to be read as current. Same underlying gap as F-077: the format is good at values and has
  no vocabulary for "the shape moved".
- Owner / next step: a `figma-survey` change, not made here — altering the spec format
  while writing a spec into it is how a contract becomes a transcript (the same reason
  F-077 was left alone). #172 carries the rewrites.

### F-079 — A colour hashed from a display name satisfies the invariant its spec states and fails the one it exists for

- Date: 2026-09-02
- Area: UX-spec
- Severity: Medium
- Task being attempted: surveying frame `116:4444`, which replaces the report detail's
  per-line colour swatch with a per-expense-type icon — so the survey had to establish what
  the swatch was for before withdrawing it.
- Expected vs actual: `ReportViewSupport.categoryColor` hashes `String.hashCode()` of the
  expense type's *name* modulo six Aura palette hues, and
  `expense-line-card.md` documented it as "hashes the expense type name onto the six
  saturated palette hues **so a type reads the same colour throughout a report**". That
  stated property is true and always was. The property the dot actually exists for — that
  two types are told apart — fails badly. Computed against the nine seeded types:

  | Hue | Types |
  |---|---|
  | `--aura-red` | Travel allowance, Taxi/transport, Accommodation, **Other** |
  | `--aura-green` | Parking/supplies/goods, Meal allowance |
  | `--aura-blue` | Restaurant/meals |
  | `--aura-yellow` | Publications |
  | `--aura-orange` | Kilometre allowance |
  | `--aura-purple` | *unreachable* |

  Four of nine collide, including the three most common lines on a travel report, on the
  app's own rejected/error hue; one of six hues is never produced at all.
- Why it survived review, a spec and an audit: the invariant everyone checked — stability —
  is the one a hash trivially gives you. `String.hashCode()` is specified in the JLS, so
  the colour is stable across restarts and JVMs, and the spec, the javadoc and the code all
  say so. **Distinctness was never written down anywhere**, so nothing tested it, nothing
  reviewed it, and nine types over six hues never looked like a number worth checking. It
  is also invisible in the small: a two-line report shows two dots, which are usually
  different. It only reads as broken on a realistic report, which is exactly the report a
  screenshot review does not use.
- A second, quieter defect: the colour is derived from the display **name**, so it is stored
  nowhere and an admin renaming an expense type silently changes its colour everywhere,
  including on historical reports.
- Impact: visual only. ADR-0020's "never colour alone" rule means the type name always
  renders as text beside the dot, so nothing was unreadable and no accessibility floor was
  crossed — which is also why it went unnoticed for so long. The cost is that a deliberate
  affordance was decoration that did not work.
- Resolution: the design withdraws the dot in favour of a per-type glyph, and #172 takes the
  glyph from a new `ExpenseType.icon` column rather than a view-side name→glyph map — the
  map was cheaper and was rejected for carrying this same rename fragility. `.category-dot`,
  `categoryColor` and `CATEGORY_COLORS` are deleted, and no colour replaces them.
- Suggested improvement: for this project, **state the property that matters, not the one
  that is easy** — "the same type reads the same colour" is a stability claim and says
  nothing about legibility; "two types in one report are visually distinct" is the claim,
  and it is falsifiable against the seeded reference data in a plain unit test. More
  generally: a hash onto N buckets needs a collision check against real cardinality at the
  moment it is written, because the failure is silent, data-dependent, and invisible at the
  sizes a developer looks at.
- Owner / next step: closed by #172. Logged because the *shape* — a documented invariant
  that holds while the actual purpose fails, in decoration that no test covers — is the
  reusable lesson, not the dot.

### F-080 — `text-overflow: ellipsis` is inert inside a `VerticalLayout`, and the failure is a page-wide horizontal scroll that a green browserless suite cannot see
- Date: 2026-09-02
- Area: Vaadin
- Severity: Medium
- Task being attempted: issue #172's report-detail header. The design draws the report
  title as one 24px line (`241:10575` is 769×34 for a 44-character string), and the app
  renders `additionalInformation` there — a `TextArea`-backed field capped at 2000
  characters. So it has to truncate.
- Expected vs actual: expected the textbook three declarations —
  `overflow: hidden; text-overflow: ellipsis; white-space: nowrap` — on the title `Span`
  to ellipse it against the header's width. Actual: the `Span` rendered **3332px wide**
  and took the whole document to `scrollWidth: 3709` against a 1440 viewport. Every card
  below it stayed 836px, so the page looked correct and simply scrolled sideways for
  2269px of nothing.
- Workaround used: `max-width: 100%` **and** `min-width: 0` on the title, plus
  `min-width: 0` on both flex ancestors. Two separate mechanisms had to be released:
  - the `Span` is a flex item of a `VerticalLayout`, whose default cross-axis alignment is
    `START` (`setDefaultHorizontalComponentAlignment`), so the item is sized to its
    **content** on the horizontal axis and has no width limit at all — `overflow: hidden`
    has nothing to clip against. `max-width: 100%` is what supplies the box;
  - a flex item's default `min-width` is `auto`, which for `nowrap` text is the whole
    string's width, so even with a `max-width` it refuses to shrink to it. `min-width: 0`
    is what allows it.
  `styles.css:.report-detail-title`, `.report-detail-title-group`,
  `.report-detail-title-column`; `ReportDetailView.headerRow()`.
- Evidence: measured in the browser, not reasoned — the flex chain reported
  `SPAN.report-detail-title` at width 3332 with `min-width: auto` inside a
  `VAADIN-VERTICAL-LAYOUT` at 836. After the fix: 697 and `scrollWidth: 1425`.
- Impact: **the 95-test browserless suite was green the entire time the page was 2.5×
  too wide.** That is the finding. A browserless test asserts the component tree, and the
  tree was right: the `Span` was present, carried the correct class, and held the correct
  text. Nothing in the fast layer can express "and it is 3332px wide", so the only thing
  that caught this was opening the page — which is exactly the boundary
  `implement-use-case` step 3 draws, met in its sharpest form. Note also that the bug was
  invisible on a *short* title: report 21 with a blank `additionalInformation` renders the
  "Expense report" fallback and looks perfect, so the failure is data-dependent as well as
  test-invisible.
- Suggested Vaadin/product improvement: `VerticalLayout`'s `START` cross-axis default is a
  reasonable choice and should not change. But it is the reason the single most common
  truncation recipe on the web does not work inside Vaadin's most common layout, and
  nothing says so. One sentence in the ordered-layout docs — "items are sized to their
  content horizontally; a truncating child needs `max-width: 100%` and `min-width: 0`" —
  would cover it. A `setWidthFull()` on the child is the other cure and is worth naming in
  the same sentence, since it is the fix a Flow developer would reach for first.
- Owner / next step: fixed in #172. The general rule, both mechanisms and the
  `scrollWidth <= innerWidth` assertion are now in `docs/theming-layouts.md` § *Flex
  sizing*, deliberately placed beside § *Inherited properties* — the two are the same
  shape of silent failure and belong together. Worth raising with the Vaadin docs team:
  `VerticalLayout`'s `START` cross-axis default is fine, but nothing says it disables the
  commonest truncation recipe on the web.

### F-081 — `FormLayout.setRowSpacing` is not a gap but a half-margin on every child, so a child that sets `margin: 0` silently halves the space around itself
- Date: 2026-09-03
- Area: Vaadin
- Severity: Low
- Task being attempted: issue #179's travel editor. The spec places two `Hr` rules as
  colspan-2 rows of the `FormLayout` so that "the form's row gap supplies the 20px
  clearance above and below; set none", and the ticket's proposed CSS wrote that intent
  down literally as `.form-rule { …; margin: 0; }` — a reasonable reading of "no margin",
  and one that also neutralises the UA's `hr { margin-block: 0.5em }`.
- Expected vs actual: expected 20px between the purpose field and the rule, and 20px
  between the rule and the `EXPENSES` eyebrow, since `setRowSpacing("var(--em-card-padding)")`
  was set and reads like a grid `row-gap`. Actual, measured: **10px on both sides**.
  Every other pair of neighbours measured 20.
- Workaround used: delete the `margin: 0`. `FormLayout` implements row spacing as
  `margin-block: calc(var(--vaadin-form-layout-row-spacing) / 2)` on every slotted
  child — its own `#layout` is `display: flex` with `row-gap: normal` — so the 20 between
  two fields is 10 + 10. A child that zeroes its own margin removes its half and sits 10
  from *both* neighbours. Once the declaration goes, the slotted rule outranks the UA's
  `hr` margin on its own, so the literal "set none" was right and the `margin: 0` was
  the mistake.
- Evidence: `getComputedStyle` on the form's children — every field `margin-top: 10px`
  / `margin-bottom: 10px`, the `hr` `0px` / `0px`; `purpose.bottom = 316`, `hr.y = 326`,
  `eyebrow.y = 337`. After the fix: 20 on both sides. `styles.css:.form-rule`.
- Impact: the same shape as F-062 and F-072 — a valid declaration, no error, and a result
  that is *plausible* rather than wrong, so a screenshot glance passes it. It only showed
  because the survey had written the expected pixel arithmetic (249 → 269 → 289) into the
  spec, which gave the browser probe a number to disagree with. Any component with a
  margin reset in a shared class (`Hr`, a `Div` with a card class, a `Span` styled for a
  heading) will do this inside every `FormLayout` that holds it.
- Suggested Vaadin/product improvement: the Javadoc of `setRowSpacing` says "the row
  spacing" and nothing about the mechanism. One sentence — "applied as half the value as
  block margin on each child, so a child's own margin declaration replaces its share" —
  is the whole fix. `row-gap` on the layout, which the auto-responsive mode may already
  use, would make the question go away.
- Owner / next step: fixed in #179; recorded in `docs/vaadin-gotchas.md` so the next
  rule or heading dropped into a `FormLayout` does not re-learn it.

### F-082 — Vaadin 25.2 → 25.3 breaks two browserless test idioms: nested dialogs and `use()` on a Grid cell component
- Date: 2026-11-10
- Area: Vaadin
- Severity: Medium
- Task being attempted: bumping Vaadin 25.2.1 → 25.3.5 (and Spring Boot 4.1.0 → 4.1.1)
  as the first commit of the unified-testing-tool spike, whose alpha BOM pins
  Browserless Test 25.3.2. No production code changed; 5 of 583 tests went red.
- Expected vs actual:
  1. **Nested dialogs.** A `Dialog` opened while a modal dialog is open (the trip
     editor's "Clear your override?" confirm) now attaches *inside* that dialog rather
     than to the UI. The outer editor's `getElement().getTextRecursively()` therefore
     contains the confirm's text, and a "first open dialog containing X" helper returns
     the editor. 4 `ReportDetailViewUiTest` failures, all `expected "Clear your
     override?" but was "Edit travel info"`.
  2. **`use(component)` on a Grid cell component.** `use(menu)` now returns a
     `MenuBarLocator` whose actions re-find the component by querying from the UI, and a
     component rendered into a Grid cell is not in that tree → `NoSuchElementException:
     No visible MenuBar in MockedUI[]`, although the test holds the instance.
- Workaround used: (1) the helper picks the innermost matching open dialog
  (`ReportDetailViewUiTest#openDialogSaying`); (2) `new MenuBarTester<>(menu)` instead of
  `use(menu)` in both row-action helpers.
- Evidence: `MenuBarLocator.clickItem` bytecode (`Locator.component()` →
  `ComponentQuery.single()`); surefire reports for `AllowanceRatesViewUiTest`,
  `ReportDetailViewUiTest` on 25.3.5 vs green on 25.2.1.
- Impact: a minor-version bump turns tests red with no production change, and the
  dialog one is a real DOM change any test (or CSS/selector) that scopes by "the open
  dialog" can trip on. Neither is in the 25.3 release notes we found.
- Suggested Vaadin/product improvement: note the dialog-nesting change in the upgrade
  notes; make `use(component)` on an instance act on that instance, or fail with "this
  component is not reachable from the UI (Grid cell?) — use the tester directly".
- Owner / next step: fixed in the upgrade commit on `try/unified-testing-tool`.
