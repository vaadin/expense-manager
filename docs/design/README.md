# Design spec

The visual **specification** for this app: the decisions already taken, so a later
session looks them up instead of taking them again. Its job is to stop drift — an agent
with no memory of the last session will otherwise invent a plausible value
(`padding: 12px`) rather than reach for the settled one, and by the fifth view the app
reads as several products.

The spec holds **opinion and precedent**, not capability. Anything an agent can read out
of the source — a component's constructor, which CSS classes exist — does not belong
here. What belongs here is what the source cannot tell you: *which* blue, *why* this
radius, and which differences from the design were decided deliberately.

**Design source of truth:** [Figma](https://www.figma.com/design/Irsp3cgi1WX3GiLGpJZECa/Expense-Manager?node-id=88-12278)
file `Irsp3cgi1WX3GiLGpJZECa`, page `88:12278`. When the design wins and when it does not
is [ADR-0025](../adr/0025-figma-design-source-of-truth.md); this folder holds the values
that rule produced.

## Layout

```
docs/design/
├── foundations/     one file per visual concern — the decisions, per concern
│   ├── color.md         accent, neutral, palette, semantic colour
│   ├── typography.md    family, scale, weights, text roles
│   ├── spacing.md       density, padding and gap scale
│   ├── radius.md        corner radii
│   ├── elevation.md     shadows, surfaces
│   ├── motion.md        transitions
│   └── iconography.md   the icon set, size scale, colour and stroke
├── tokens/
│   └── token-reference.md   master map: every variable, its value, when to use it
└── components/      one file per component that exists in this app
```

Patterns — which Java layout API, which CSS, what is forbidden — stay in
[`../theming-layouts.md`](../theming-layouts.md).

## How it is consumed

This folder is the **contract**. One skill writes it; everything else conforms.

- **`/figma-survey` writes it.** Scoped `theme` it writes `foundations/` and the inputs
  in `tokens/`; scoped to a view or component it writes `components/`. It decides each
  divergence with the user rather than transcribing the design, because the calls that
  matter — which side wins, what to do with an off-scale value — are not in the design.
- **`/figma-theme` applies the theme** and owns one table: the *resolved values* in
  `tokens/token-reference.md`, which only a running app can produce. It refuses a spec
  whose rows are still **open**.
- **Implementation conforms.** Read `tokens/token-reference.md` and the component's file;
  take the tokens and states from there. A difference is a bug in the code. **Never edit a
  spec file to match what was just built** — re-run the survey instead.
- **`/figma-visual-verification` fills the measured figures** a survey cannot: contrast
  ratios and rendered sizes need a running app, so a survey leaves those slots marked
  unverified.

## Status vocabulary

Every decision row carries one:

- **settled** — decided, whichever side won. A later run names it settled and moves on.
  A divergence resolved in the *app's* favour is settled, not absent: that row is the
  whole point, because it stops the next survey reporting the app's own font as a
  mismatch.
- **open** — deferred. A survey may raise it again, and should.

## What is still open

| Area | Owner |
|---|---|
| App shell — top header bar, gradient, nav widths (220/250), page inset (80px) | #146 |
| Per-view spacing against the new token scale | one issue per view |
| Report card hover — invisible on actionable cards; the design draws no hover state | the report-list redesign |
| `--em-font-size-detail-title: 24px` decided but not yet in `aura-theme.css` | the report-detail redesign issue |
| `ExpenseType.icon` — the column the report-detail rows' glyphs read from | the report-detail redesign issue |
| Whether the design specifies a **status callout** at all — frame `116:4444` draws a `DRAFT`, the one status the app hides it on, so it settles nothing | needs a frame drawing `REJECTED` |
| Whether a report gains a **title** field. Ruled out of scope by the report-detail survey, so the header renders `additionalInformation` twice | closed, not deferred — reopen only with the designer |
| Which reference route the Reference Tables pill points at when a user can reach only some of the three — it is `/vat-rates` unconditionally, and all three are ADMIN-only, so the case cannot occur yet | the shell issue |
| Whether the 20/24/28 display ramp should be design tokens rather than `--em-*` properties | the designer |
| **Label case** — Title Case (drawn on `358:3267`, `253:10597` and, for headings, `143:1781`) or the app's sentence case; a global copy convention, refused a per-component answer three times. The app already ships a Title-Case *tab* over a sentence-case *heading* of the same words on `/vat-rates`, so it is now visibly wrong rather than merely undecided | the label-case ticket |
| **"New" against "Add"** for record creation — frame `143:1781` draws `New`, `156:5396` draws `Add`, and the app says both. A copy convention, so it is the label-case ticket's sibling rather than a per-view call | the copy-convention ticket |
| The **zero-row state of a filtered grid** — "no matches, clear the filters" is a different component from "nothing here yet", and no frame draws either. `filters-bar` refused to invent it | `empty-state`'s owner |
| Whether Aura's stock grid already resolves to the design's 9px radius, 32px header and 40px row, or whether three properties need overriding | the reference-view issue's verification pass |
| Whether `report-card`'s title should follow `--em-font-size-title` to 40px, split off its own property, or take the new `--em-font-size-detail-title` (24) — a record title, on the same argument the report detail's took | the report-list issue |

## Lifecycle

`foundations/` and `tokens/` are **data**, refreshed when the theme changes or the Vaadin
version moves. The *rationale* is prose and lives in an ADR, which is immutable — a
deliberate split, because an immutable document holding mutable data goes stale silently
and a survey reading it would trust stale numbers.

`components/` is append-and-amend: each per-view issue updates what it touches.
