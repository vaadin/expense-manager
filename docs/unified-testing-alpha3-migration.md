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
| ExpenseTypeViewUiTest | 11 | 0.388 | 0.373–0.402 |
| MyReportsViewUiTest | 21 | 0.685 | 0.679–0.764 |
| NavigationShellUiTest | 10 | 0.719 | 0.702–0.727 |
| PageHeaderUiTest | 3 | 0.612 | 0.608–0.620 |
| ReferenceConfigViewUiTest | 6 | 0.189 | 0.183–0.212 |
| ReportDetailViewUiTest | 104 | 6.746 | 6.581–6.793 |
| UserManagementViewUiTest | 10 | 1.132 | 1.109–1.139 |
| VatRateViewUiTest | 4 | 0.596 | 0.582–0.602 |
| **Sum of class times** | **205** | **20.060** | 19.969–20.706 |

## 1. Recipe efficiency

_Steps 3–4. Measured on the raw recipe output, before any hand edit._

## 2. Skill efficiency

_Step 6, `resolve-migration-markers`._

## 3. Browserless runtime — after

_Step 7. Same command and protocol as the baseline._
