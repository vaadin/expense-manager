# Architecture Decision Records

Each ADR records one load-bearing decision for the Expense Management app (V1), the context that forced it, and its consequences. ADRs are immutable once accepted — to change a decision, add a new ADR that supersedes the old one.

Status values: `Accepted`, `Superseded by ADR-XXXX`, `Deprecated`.

| ADR | Decision | Status |
|-----|----------|--------|
| [0001](0001-flow-only-no-react.md) | Flow only (server-side Java), no React/Hilla | Accepted |
| [0002](0002-package-by-feature.md) | Package-by-feature code organization | Accepted |
| [0003](0003-persistence-jpa-dto-boundary.md) | Spring Data JPA + DTO/record UI boundary | Accepted |
| [0004](0004-postgres-everywhere.md) | PostgreSQL everywhere (Compose + Testcontainers) | Accepted |
| [0005](0005-flyway-migrations.md) | Flyway SQL migrations | Accepted |
| [0006](0006-rich-domain-aggregate.md) | Rich domain, ExpenseReport aggregate root | Accepted |
| [0007](0007-google-oauth-provisioning.md) | Google OAuth2 + domain-gated auto-provisioning | Accepted |
| [0008](0008-route-and-method-security.md) | Route + method security; invariants in domain | Accepted |
| [0009](0009-receipts-as-blobs.md) | Receipts stored as Postgres bytea | Accepted (refined by 0020) |
| [0010](0010-money-eur-bigdecimal.md) | EUR-only, BigDecimal scale 2, no Money VO | Accepted |
| [0011](0011-optimistic-locking.md) | Optimistic locking (@Version) on the report | Accepted |
| [0012](0012-testing-strategy.md) | Test pyramid layers 1–3, OAuth form-stub | Accepted |
| [0013](0013-config-secrets-observability.md) | 4 profiles, env-var secrets, Actuator + JSON logs | Accepted |
| [0014](0014-java-25.md) | Java 25 in pom and Dockerfile | Accepted |
| [0015](0015-binder-and-signals.md) | Binder for form validation; Signals for dynamic state | Accepted |
| [0016](0016-persistence-baseline-pk-audit.md) | Bigint PKs, app-side audit timestamps, minimal V1 baseline | Accepted |
| [0017](0017-base-ui-shell-and-ux-states.md) | Base UI shell: auto-menu nav + shared UX-state primitives | Accepted |
| [0018](0018-expense-type-and-vat-config.md) | Expense Type & VAT Rate as editable reference config | Accepted |
| [0019](0019-report-save-model.md) | Report edit/save: whole-aggregate, in-memory until first save | Accepted (receipt consequence retired by 0021) |
| [0020](0020-accessible-and-mobile-friendly.md) | Accessible (WCAG 2.1 AA) and mobile-friendly baseline | Accepted |
| [0021](0021-receipt-entity-upload-serving.md) | Receipt: separate entity, buffered upload, summary-only DTO | Accepted |
| [0022](0022-form-models-top-level.md) | Binder form-backing models are top-level classes, not view inner classes | Accepted |
| [0023](0023-expense-line-quantity.md) | Expense-line quantity: `amount` becomes a unit price, gross derived | Accepted |
| [0024](0024-generated-line-quantity-override.md) | Generated lines corrected by overriding the quantity, never the amount | Accepted |
| [0025](0025-figma-design-source-of-truth.md) | Figma is the design-system source of truth, for visual decisions only | Accepted |
| [0026](0026-lucide-icon-set.md) | Lucide is the app's icon set, delivered as one vendored SVG sprite | Accepted |
| [0027](0027-unified-ui-tests.md) | UI tests on the Unified API, run on Browserless and Playwright | Proposed (spike) |

See also: [../glossary.md](../glossary.md) · [../plan.md](../plan.md) · [../findings.md](../findings.md)
