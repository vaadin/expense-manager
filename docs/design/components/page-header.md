# Page header

**Category:** composite
**Origin:** design
**Implementation:** drifted
**Code:** `.page-title` in `styles.css`, composed by `ReferenceConfigView` (heading +
Add button + intro) and `AllowanceRatesView#header`. `.page-header`, `.page-header-top`
and `.page-intro` do not exist yet
**Design:** node `143:1935` › `header-row` on frame `143:1781` "Reference Tables - VAT
Rates" — `152:4861` (`Top`: heading `143:1936` + `Button (primary)` `143:1937`) and
`152:4863` (`Subtitle`). The same composition on frame `156:5396`

## Overview

The block that opens a **full-page view**: the page's name at the display size, the page's
one primary action opposite it, and a paragraph of standing explanation underneath.

**Not** [`report-detail-header.md`](report-detail-header.md), which is the header of a
**record** rather than of a page — an eyebrow, the record's own title, its status badge and
its metadata, at `--em-font-size-detail-title` (24). The two differ by 16px of type and by
what changes when the data changes: a page header is the same on every visit, a record
header is different for every record.

**Not** `.section-title` (30px), which names a section *inside* a page — "Foreign Per
Diem" — and never carries the page's primary action.

The intro paragraph is **not optional decoration**. On the reference views it is where the
consequences live: that deactivating keeps history, that a year's rates never change
retroactively. It is the only place a first-time admin learns the rule before acting on it,
which is why it is specified at a readable 16px and not at body size.

## Anatomy

| Part | Element | Design node |
|---|---|---|
| Block | `.page-header` — a `VerticalLayout`, no padding, full width | `143:1935` |
| Top row | `.page-header-top` — a `HorizontalLayout`, `BETWEEN` + `CENTER` | `152:4861` |
| Heading | `H2` + `.page-title` | `143:1936` |
| Primary action | `Button` + `ButtonVariant.PRIMARY`, icon in the prefix slot | `143:1937` |
| Intro | `Paragraph` + `.page-intro` | `152:4863` |

`H2` rather than `H1`: the shell owns the page's `h1`
([`app-shell.md`](app-shell.md)), so a view's own heading is the second level. The class
carries the size, not the element.

## Tokens used

| Part | Token | Design value |
|---|---|---|
| Heading size | `--em-font-size-title` (40) | 40 |
| Heading weight | `--aura-font-weight-semibold` (600) | Instrument Sans **Bold** |
| Heading colour | `--vaadin-text-color`, reached by `color: inherit` | `#0f172a` — unbound |
| Heading line-height | `1.2`, **unitless** | `normal` |
| Intro size | `--aura-font-size-l` (16) | 16 |
| Intro colour | `--vaadin-text-color-secondary` | bound |
| Intro line-height | `1.6`, **unitless** | 1.6 |
| Intro width | the content column's, wrapping at its edge | 900 |
| Block gap | `--em-card-padding` (20) | 20 |
| Button | stock `PRIMARY` — see [`button.md`](button.md) | fill `#0a0b0d`, radius 9 |

**Bold → 600 and Instrument Sans are already-settled rows**, not new ones: the design's
`Bold` is Aura's `--aura-font-weight-semibold`, and the family follows `--aura-font-family`
because the *variable* beats the drawn text (ADR-0025 decision 4). Both are recorded in
[`../foundations/typography.md`](../foundations/typography.md). The heading's `#0f172a` is
bound to no variable at all — a slate near-black where `--vaadin-text-color` is `#0a0b0d` —
so the token wins and the difference is a design defect, reported rather than matched.

**Both line-heights are unitless on purpose, and this is load-bearing.** Aura sets
`line-height` on `body` as a **length** (`--aura-line-height-m`, 20px). A length inherits
unchanged into a 40px heading and crops its descenders; a unitless value re-derives from
the element's own font-size. The same applies to the 16px intro, where the inherited 20px
would be tighter than the design's 1.6. Neither value is a token because the type ramp's
line heights are lengths — using `--aura-font-size-l`'s 22px here would reintroduce exactly
the bug. `.page-title` already does this correctly; `.page-intro` must do the same.

**`color: inherit` on the heading is not defensive either.** Aura re-declares `color` on
`h1`–`h6` at element level, so a container's colour never reaches a heading by inheritance
at any specificity, `:where()`'s zero included (F-072).

## API

CSS-only, composed through the layout API. The 20px block gap goes through
`setSpacing("var(--em-card-padding)")`, not into the class — spacing a layout API can
express never becomes CSS (`docs/theming-layouts.md`).

## States

| State | Behaviour |
|---|---|
| default | heading, primary button opposite, intro below |
| hover | n/a on the block. The button's own hover is Aura's `::before` overlay at 3% opacity — invisible to a computed-style check ([`button.md`](button.md)) |
| active | n/a on the block; the button's is Aura's own |
| focus | n/a on the block. The button takes Aura's focus ring; the heading is not focusable, and must not be given `tabindex` to make the page announce itself |
| disabled | **no disabled state, by rule.** A user who may not create renders **no** button rather than a greyed one — ADR-0008's route-level rule, and the same call [`row-action-menu.md`](row-action-menu.md) makes for an unavailable row. Both reference views are ADMIN-only in their entirety, so the case cannot arise there yet |
| error | n/a — nothing in this block validates. Editor errors surface in [`error-summary.md`](error-summary.md) inside the dialog |

## Code example

```java
var heading = new H2("VAT rates");
heading.addClassName("page-title");

var add = new Button("New", LucideIcon.PLUS.create(), event -> openEditor(null));
add.addThemeVariants(ButtonVariant.PRIMARY);

var top = new HorizontalLayout(heading, add);
top.addClassName("page-header-top");
top.setWidthFull();
top.setPadding(false);
top.setAlignItems(FlexComponent.Alignment.CENTER);
top.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

var intro = new Paragraph("The VAT rates expense lines are filed against. …");
intro.addClassName("page-intro");

var header = new VerticalLayout(top, intro);
header.setPadding(false);
header.setSpacing("var(--em-card-padding)");
header.setWidthFull();
```

`.page-header-top` exists to wrap at narrow widths: a 40px heading and a button cannot
share a 360px row, and the fallback is a page-wide horizontal scrollbar.

## The button's label

The frame draws the create button as bare **"New"**; the app renders **"Add VAT rate"**.

**The design wins here.** The `H2` beside it carries the noun, which is the reasoning #169
already used for the bare "Add" next to the "Foreign Per Diem" heading, and the app has
said "New report" on the report list since the first view — so the verb is not new, only the
dropped noun.

Two things this does *not* settle, and both are named in the delta rather than decided
here:

- **"New" against "Add" for record creation across the app** is a copy convention, and a
  convention outranks one survey. `AllowanceRatesView` keeps "Add"/"Add Year"/"Copy Year";
  taking "New" on the two reference-config views does split that pair, and the frames split
  the same way. Whether the app converges, and on which verb, is a human's call.
- **Label case.** The frame draws the heading "VAT Rates" where the app renders "VAT
  rates", and the tab bar 40px above it already ships the design's Title-Case "VAT Rates".
  Page-heading case is app-wide, so it stays **open** and this frame joins the evidence.
  See [`../foundations/typography.md`](../foundations/typography.md) § *Label case is
  undecided*.

## Divergence

| What | Spec | Code today | Owner |
|---|---|---|---|
| Heading size | `.page-title` (40) | `ReferenceConfigView` adds a bare `H2` with **no class** — Aura's stock `h2` | the reference-view issue |
| Intro size | `--aura-font-size-l` (16) | `Paragraph` default, 14 | the reference-view issue |
| Intro colour | `--vaadin-text-color-secondary` | `--vaadin-text-color` | the reference-view issue |
| Intro leading | 1.6 unitless | Aura's inherited 20px length | the reference-view issue |
| Block gap | `--em-card-padding` (20) | `setSpacing(true)` in `ReferenceConfigView`; `--vaadin-gap-s` (8) in `AllowanceRatesView` | the reference-view issue |
| Wrapping | `.page-header-top` | none — heading and button in a bare `HorizontalLayout` | the reference-view issue |
| Button label | "New" | "Add VAT rate" / "Add expense type" | the reference-view issue |

`AllowanceRatesView` carries `.page-title` correctly and diverges on the remaining six
rows, so closing this reaches all three reference routes.

## Cross-references

[`report-detail-header.md`](report-detail-header.md) — the sibling this is confused with ·
[`button.md`](button.md) · [`reference-tabs.md`](reference-tabs.md) — the bar above it ·
[`filters-bar.md`](filters-bar.md) · [`app-shell.md`](app-shell.md) — which owns the `h1` ·
[`../foundations/typography.md`](../foundations/typography.md) — the 40px and label-case
rows · ADR-0008 (route security), ADR-0025 (the design as contract), F-072 (inherited
colour never reaching a heading)
