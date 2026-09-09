# Component specs

One file per component that **exists in this app, or that a survey's delta will
create**. Nothing speculative beyond that: a component nobody has committed to building
does not get a file, and one that is merely *imagined* for a future view does not either.

A file written ahead of the code carries `Implementation: none`, which is exactly what
that value is for — the spec is the contract, so writing it first is the intended order
when the delta that will satisfy it is already filed.

**Written by `/figma-survey`**, scoped to a view or a component. These files are the
**contract**: implementation takes its tokens and states from them, and a difference is a
bug in the code rather than in the file. Never edit one to match what was just built —
that turns the contract into a transcript and makes the drift invisible. Where the design
has moved, or a component has no file, re-run the survey.

A spec records what the source cannot tell you — when to use the component, when not to,
which tokens it is entitled to, and which states must be covered. It does **not**
duplicate the constructor signature or the class javadoc; those are readable from the
source and would go stale here.

## Inventory

| Component | Category | Origin | Implementation |
|---|---|---|---|
| [`button.md`](button.md) | themed primitive | design | conforms |
| [`badge.md`](badge.md) | themed primitive | design | conforms |
| [`report-card.md`](report-card.md) | composite | design | unaudited |
| [`metric-card.md`](metric-card.md) | composite | design | none |
| [`report-list-section.md`](report-list-section.md) | composite | design | unaudited |
| [`report-detail-header.md`](report-detail-header.md) | composite | design | **drifted** |
| [`expense-item-card.md`](expense-item-card.md) | composite | design | none |
| [`expense-line-card.md`](expense-line-card.md) | composite | design | **drifted** |
| [`line-editor-dialog.md`](line-editor-dialog.md) | composite | design | **drifted** |
| [`travel-editor-dialog.md`](travel-editor-dialog.md) | composite | design | **drifted** |
| [`travel-card.md`](travel-card.md) | composite | design | **drifted** |
| [`totals-card.md`](totals-card.md) | composite | design | **drifted** |
| [`status-callout.md`](status-callout.md) | composite | **unresolved** | unaudited |
| [`status-history.md`](status-history.md) | composite | design | **drifted** |
| [`error-summary.md`](error-summary.md) | shared Java component | code | conforms |
| [`editor-dialog.md`](editor-dialog.md) | shared Java component | code | conforms |
| [`empty-state.md`](empty-state.md) | shared Java component | code | unaudited |
| [`theme-switcher.md`](theme-switcher.md) | shared Java component | code | unaudited |
| [`reference-tabs.md`](reference-tabs.md) | composite | design | unaudited |
| [`rate-list-card.md`](rate-list-card.md) | composite | design | unaudited |
| [`row-action-menu.md`](row-action-menu.md) | composite | design | unaudited |
| [`data-grid.md`](data-grid.md) | themed primitive | design | **drifted** |
| [`filters-bar.md`](filters-bar.md) | composite | design | none |
| [`page-header.md`](page-header.md) | composite | design | **drifted** |
| [`app-shell.md`](app-shell.md) | shell | design | unaudited — rebuilt in #146, Navigation rewritten in #169 |

## The two axes

They are independent, and collapsing them into one status field hides which problem you
have.

**Origin — where the spec came from, and therefore which side is authoritative:**

| Origin | Meaning | Direction of authority |
|---|---|---|
| `design` | the design specifies this component | **the spec is the contract.** Code conforms to it; a difference is a bug in the code. *Never* edit the spec to match the code |
| `code` | the app invented it; the design never drew it | **the code is the source.** The spec documents it, and updating the spec when the code changes is correct |
| `unresolved` | not yet established | treat as `design` and find out |

The split matters because "never edit a spec to match what you built" is right for a
design-origin component and wrong for a code-origin one. `ErrorSummary` came from ADR-0020
and the GOV.UK pattern, `EmptyState` from ADR-0017, `EditorDialog` from F-045 — there is no
design contract for any of them, so the code is what a spec can be checked against. If a
code-origin component later gets designed, origin flips to `design` and
[ADR-0025](../../adr/0025-figma-design-source-of-truth.md) takes over: the design wins.

**Implementation — whether the code matches, which is *derived, not declared*:**

| Value | Meaning |
|---|---|
| `none` | the spec is ahead of the code; nothing implements it yet |
| `conforms` | no mechanical divergence found |
| `drifted` | the code and the spec disagree; the spec carries a **Divergence** section naming what and who owns closing it |
| `unaudited` | no comparison has been made |

**This field belongs to an audit, not to a human.** A hand-kept conformance field goes
stale silently, which is the failure it exists to catch. `conforms` should be read as "no
mechanical divergence found" and no more — an audit can tell that a token is missing from
a table, not that the right component was chosen for the design's intent.

> **The values above were assessed by hand during issue #144.** No audit tooling exists
> yet, so treat every `conforms` as provisional and every `unaudited` row as literally
> that. One spec still predates any check of its design counterpart: `status-callout` was
> written from the stylesheet alone.
>
> The report *list* frame (`116:2499`) was surveyed for the report-list redesign, which
> resolved `report-card`'s design reference and added `metric-card` and
> `report-list-section`. `metric-card` is `none` on purpose — the spec is ahead of the
> code, which is what that value is for.
>
> `empty-state` dropped from `conforms` to `unaudited` in #163, which changed its
> constructor to take a built icon: the spec was updated by the change that caused the
> drift, so its `conforms` would have been self-asserted. That is what `unaudited` is for.
>
> The **reference-table frame** (`156:5396`) was surveyed for the allowance-rates
> redesign. It added `reference-tabs`, `rate-list-card` and `row-action-menu`, and that
> survey also re-decided `--em-font-size-title` from 24 to 40 and superseded
> `app-shell`'s Navigation row by replacing the Reference Tables menu with in-content
> tabs.
>
> **#169 then built all four**, so the three new rows moved `none` → `unaudited` and
> `app-shell`'s Navigation table was rewritten to describe the pill it now renders. Note
> what that move is and is not: `unaudited` says the code exists and nobody has compared
> it to the spec, which is the honest reading after a change audits its own work. It is
> **not** a step towards `conforms` — that value is an audit's to give.
>
> **The report-detail frame (`116:4444`) was surveyed for the report-detail redesign**,
> and it re-cut the line list rather than restyling it. The design now draws **one card per
> section** with the lines as rows inside it, where the previous frame drew one card per
> line. That inverts what four of these files described, so four were rewritten rather than
> amended — `expense-line-card` (now a row, not a card), `travel-card` (a trip row plus
> indented allowance rows, and the accent tint withdrawn), `totals-card` and
> `status-history` — and two were added: `expense-item-card` for the section card, and
> `report-detail-header`, which had never had a file.
>
> `expense-item-card` is `none` on purpose: nothing in the app builds a section card yet.
> Every other row on that frame stays **drifted** — the code implements the *previous*
> revision of its spec, which is a structural difference and not a token mismatch. Each
> carries a Divergence table naming it, and all of them are owned by the one redesign
> issue.
>
> Three decisions on that frame went to the **app**, and are settled rather than absent:
> the "Report #12" eyebrow the design does not draw (the id is a user's only handle on a
> report), the grey Draft pill against a solid accent one, and the three tax-free allowance
> subtotal rows the design omits. Two went to the domain: `ExpenseType` gains an `icon`
> column, and the design's editable report **title** was ruled **out of scope** — so the
> header keeps rendering `additionalInformation` twice, as a known infidelity.
>
> `status-callout` is *still* `unresolved`, and the frame did not help: it draws a `DRAFT`
> report, the one status on which the app hides the callout too. Its own file now says so.
>
> **One design-origin row needs a survey, not an edit.** `report-list-section`'s *Anatomy*
> names the chevron as `VaadinIcon.CHEVRON_UP`, an API that #163 removed from the app and
> that was never what the design drew — the design's node is `lucide/chevron-up`
> (`134:1768`), and the shipped implementation uses Aura's own `Details` toggle rotated in
> CSS (correct, per ADR-0026 decision 2). The row is wrong against the design *and*
> against the code, so it is `/figma-survey`'s to fix; editing it here would launder a
> design-origin spec into a transcript.
>
> **The line-editor dialog frame (`358:3267`) was surveyed next**, and it added
> `line-editor-dialog` — a component that had never had a file despite being one of the
> app's oldest. It is **drifted** for a structural reason, not a token one: the design lays
> the content out as a **two-column grid** with the VAT rate deliberately half width, where
> the code stacks every field in one column at every viewport. Nine further rows differ,
> including a `TextArea` where the code has a `TextField` and the total row's two figures,
> which follow `totals-card`'s already-settled precedent rather than a fresh judgement.
>
> Two of that frame's rows went **against** the design and are settled rather than absent:
> the Quantity **step buttons** are not built, because `setStepButtonsVisible` exists only
> on `NumberField`/`IntegerField` and ADR-0023 makes quantity `numeric(19,2)` so a line can
> read `12.5 km`; and the labels keep ADR-0023's information, moved from the label into the
> helper-text slot the frame itself draws.
>
> One row is **open**, which is rarer here than settled: the frame's **Title Case** labels.
> That is a global copy convention, so it was refused a per-component answer — the app keeps
> sentence case and the question has its own ticket for a human decision. See
> [`../foundations/typography.md`](../foundations/typography.md) § *Label case is undecided*.
> The survey added **no new `--em-*` property**; every value on the frame resolved to a
> token or to an already-settled off-scale one.
>
> **The travel editor frame (`253:10597`) was surveyed next**, and it added
> `travel-editor-dialog`, the line editor's sibling, which had never had a file either. It is
> **drifted** structurally: the design cuts the form into two sections (`DESTINATIONS`,
> `EXPENSES`) with eyebrows and rules, reorders the fields, and draws the earned lines as
> transparent rows of the form with `Trip total` last — where the code has a flat form and an
> accent-tinted preview box with the total on top. The accent that `travel-card` let the
> preview keep, because its frame did not draw the dialog, is withdrawn now that one does.
>
> Two rows went to the **domain**, and both are settled in the app's favour: the frame's
> `City` label stays `Destinations`, because the field holds a multi-place route; and its
> two-option radio group becomes a **three-option** one, because two options cannot express
> the default state (eligible, no free lunch). The group is vertical — an accepted infidelity,
> since three long labels do not fit the row — and gains the `Daily allowance` label the
> frame omits, which is the accessibility floor. The km and parking fields keep
> `BigDecimalField` against a `<vaadin-text-field>` annotation, on the line editor's
> reasoning. Label case stays **open**; this frame adds six Title Case labels to that ticket's
> evidence. No new `--em-*` property.
>
> **The VAT-rate frame (`143:1781`) was surveyed next** — the sibling of the reference frame
> `156:5396` that #169 built, targeting `VatRateView` and, through the shared
> `ReferenceConfigView`, `ExpenseTypeView`. It added the three files above, none of which had
> ever had one despite all three being among the app's oldest compositions: `data-grid` (the
> app's `Grid` as the design draws it — stock Aura plus one theme variant), `filters-bar` (the
> search-and-filter row the design puts above it, which nothing in the app builds) and
> `page-header` (heading + primary action + intro, where `ReferenceConfigView` renders a bare
> `H2` with no class at all).
>
> `filters-bar` is `none` on purpose. The other two are **drifted** for accumulated small
> reasons rather than a structural recut: no row stripes, Vaadin's `SINGLE` selection default
> on a grid that reads no selection, a left-aligned actions column, four inline icon buttons
> where the frame draws a `⋮`, an unclassed heading and a 14px body-colour intro where the
> frame draws 16px secondary.
>
> **Two decisions on that frame went to the app, and are settled rather than absent.** The
> **Status column** stays, against a frame that draws only Rate and Actions: ADR-0018 keeps
> inactive rows in the grid so an admin can reactivate them, and with no column an active and
> an inactive rate are indistinguishable while the row's menu offers Activate *or* Deactivate.
> The frame's own **Status filter** is the argument — a frame offering to filter by status
> agrees status belongs on the view — so the filter is taken *and* the column kept. And the
> percent format stays `13.5 %` against the frame's `13.5%`, because Finnish typography puts a
> space before the unit and that is a locale rule rather than a visual choice.
>
> The frame's bare **"New"** button label wins over the app's "Add VAT rate"; whether the app
> converges on "New" or "Add" for record creation is a copy convention and has its own
> question. **Label case stays open**, and this frame is the strongest evidence yet — the app
> already renders a Title-Case `VAT Rates` *tab* directly above a sentence-case `VAT rates`
> *heading*, so two conventions ship in one viewport. See
> [`../foundations/typography.md`](../foundations/typography.md).
>
> The survey also **decided a question `row-action-menu` had deferred**: the boundary-disabled
> reorder actions become **disabled** `MenuItem`s rather than omitted ones, which needs an
> `addAction(String, boolean, Runnable)` overload. And it left one **gap it refused to
> invent**: filtering to zero rows has no designed state, and "no matches" is a different
> component from "nothing here yet" — that belongs to `empty-state`'s owner. **No new `--em-*`
> property; no new off-scale value.**

## Who writes these

| Origin | Author |
|---|---|
| `design` | `/figma-survey`, scoped to a view or a component |
| `code` | whoever builds the component — `implement-use-case` |
| either, once implemented | the audit sets `Implementation` and adds or clears **Divergence** |

## Template

Every file follows this order. Omit a section only when it genuinely does not apply, and
say so rather than dropping the heading silently.

```markdown
# <Name>

**Category:** themed Vaadin primitive | composite | shared Java component | shell
**Origin:** design | code | unresolved
**Implementation:** none | conforms | drifted | unaudited      <- set by the audit
**Code:** <path(s) and CSS classes> — or an em dash if nothing implements it yet
**Design:** <node id > layer name> — or an em dash, with why, if never designed

## Overview
When to use it. When *not* to — the sibling it is confused with.

## Anatomy
The parts, and which class or element each maps to.

## Tokens used
Only tokens. A raw px value in this table is a bug or a recorded off-scale decision.

## API
Constructor and the few methods a caller needs. Skip for CSS-only composites.

## States
default · hover · active · focus · disabled · error — say "n/a" where a state
cannot occur, and never leave a row blank.

## Code example
The shortest correct usage.

## Divergence
Only when Implementation is `drifted`: what differs, and who owns closing it.

## Cross-references
Related components, and the ADR or finding behind any non-obvious choice.
```

## Why States is mandatory

It is the section most easily skipped and the one that catches the most. A real example:
issue #144 verified the new black primary button's contrast at rest and reported the
states as checked, when hover had never been tested and the disabled check was reading
`background-color` — a property Aura does not touch for either state. A spec with a
States row forces the question. See
[`../foundations/elevation.md`](../foundations/elevation.md) and the gotcha in
[`../../vaadin-gotchas.md`](../../vaadin-gotchas.md).
