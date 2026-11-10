# Development

## Prerequisites

- **JDK 25** (ADR-0014). Point `JAVA_HOME` at a Java 25 install before building.
- **Docker** must be running. Local dev and the integration test layer use real
  PostgreSQL — Docker Compose for local, Testcontainers for tests. No H2, ever
  (ADR-0004).

### Figma access (only for design work)

The visual design lives in Figma, and the design-application agent skills read it
through a **remote Figma MCP server**. The server is declared in
[`.mcp.json`](.mcp.json), so it is project-scoped — every developer who opens this
repo gets the entry, and nobody has to configure it by hand.

What you do need to do, **once per machine**:

1. Run `/mcp` in Claude Code and authenticate the `Figma` server in the browser
   window it opens. The token persists; you won't be asked again.
2. Have a **Figma seat** on the Expense Manager file. The MCP server authenticates
   as you — no seat, no access, regardless of the MCP setup.

Without both, the Figma-facing skills — `figma-survey`, `figma-theme`,
`figma-to-vaadin`, `figma-visual-verification` — fail in a way that reads as a bug rather than as a
missing credential: the design lookup returns nothing and the agent carries on
against a guess. If a design task starts producing layout invented from thin air,
check `/mcp` before debugging anything else.

Nothing else in this repo needs Figma. Building, running and testing the app work
exactly the same without it.

Note there is deliberately **no `Vaadin` MCP server entry**. The Vaadin docs tools
(`search_vaadin_docs`, `get_full_document`, `get_component_java_api`,
`get_theme_css_properties`) come from the `vaadin-skills` plugin instead; two
identically-named tool sets would make the skills' bare tool references ambiguous.

## Build & Run Commands

```bash
./mvnw                            # Run in dev mode (default goal: spring-boot:run)
./mvnw clean package              # Production build (JAR in target/)
./mvnw test                       # Run all tests
./mvnw test -Dtest=ClassName      # Run a single test class

# Unified UI tests (tag "unified", ADR-0027 proposed) in a real Chromium
./mvnw test -Dgroups=unified -Dvaadin.testing.tool=playwright
```

The Unified UI tests run on Browserless by default, as part of `./mvnw test`.
`-Dvaadin.testing.tool=playwright` runs the same tests in a browser. Playwright
downloads its own Chromium the first time. To use a Chromium that's already
installed, add `-Dvaadin.testing.playwright.executable=/path/to/chrome` and set
`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`.

`./mvnw` is the single documented command to run the app locally. It boots in
the **`local`** profile (the default), and Spring Boot's Docker Compose support
auto-starts the PostgreSQL service in [`compose.yaml`](compose.yaml) and wires
the datasource to it — no manual DB setup. The app runs on port 8080
(configurable via `PORT`).

On an empty DB the `local` profile seeds labelled DRAFT/SUBMITTED/APPROVED
report fixtures so you land directly on the screen under test — see
[`docs/manual-verification.md`](docs/manual-verification.md) for the fixtures,
logins, and the Playwright smoke-test pattern.

## Profiles

Four profiles override a base `application.properties` (ADR-0013):

| Profile   | Postgres                      | OAuth / login            | Logging        |
|-----------|-------------------------------|--------------------------|----------------|
| `local`   | Docker Compose (auto-started) | form-stub (Phase 1.3)    | human-readable |
| `test`    | Testcontainers                | form-stub                | human-readable |
| `staging` | external managed              | real Google OAuth        | structured JSON |
| `prod`    | external managed              | real Google OAuth        | structured JSON |

`local` is the default when no profile is set. Select another with
`SPRING_PROFILES_ACTIVE=staging` (or `prod`/`test`).

## Environment variables & secrets

No secrets live in git. Credentials come from environment variables, with safe
defaults **only** in `local` (ADR-0013). In `staging`/`prod` these have **no
defaults** — a missing variable is a hard startup failure.

| Variable               | Purpose                     | `local` default                  |
|------------------------|-----------------------------|----------------------------------|
| `DB_URL`               | JDBC URL                    | `jdbc:postgresql://localhost:5432/expense_manager` |
| `DB_USERNAME`          | DB user                     | `expense`                        |
| `DB_PASSWORD`          | DB password                 | `expense`                        |
| `GOOGLE_CLIENT_ID`     | Google OAuth client id      | `local-dev-client-id`            |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret  | `local-dev-client-secret`        |
| `ADMIN_EMAIL`          | Seed admin identity         | `jean-christophe@vaadin.com`     |
| `PORT`                 | HTTP port                   | `8080`                           |

In `local`, Docker Compose supplies the datasource connection automatically, so
`DB_*` only matter if you point at an external DB instead of Compose.

## Docker

To build a Docker image, run:

```bash
docker build -t my-application:latest .
```

If you use commercial components, pass the license key as a build secret:

```bash
docker build --secret id=proKey,src=$HOME/.vaadin/proKey .
```
