# Typography

## Decisions

| Property | Design | App before | Decided | From | Status |
|---|---|---|---|---|---|
| `--aura-font-family` | Instrument Sans | Inter, via Google Fonts `@import` | Aura default (Instrument Sans) | design | **settled** |
| `--aura-base-font-size` | 14 | 15 | 14 (Aura default) | design | **settled** |
| `--aura-font-weight-regular` | 400 | 450 | Aura default (400) | design | **settled** |
| `--aura-font-weight-medium` | 500 | 550 | Aura default (500) | design | **settled** |
| `--aura-font-weight-semibold` | 600 | 650 | Aura default (600) | design | **settled** |
| Button / input / checkbox / radio / grid-header weights | not specified | 600 / 500 / 550 / 550 / 600 | unset | design | **settled** |
| `--aura-line-height-s` | 20 | 18 (default) | 18 (default) | app | **settled** |
| Page-heading size | **40** (`156:5404`); 24 on the report-list frame | `--em-font-size-title: 24px` | `--em-font-size-title: 40px` | design | **settled** — re-decided, see [Two heading sizes, one token](#two-heading-sizes-one-token) |
| Section-heading size | 30 (`156:5737`) | — | `--em-font-size-section: 30px` | design | **settled** |
| Expense row title | 15 | — | `--aura-font-size-l` = 16 (**+1px**) | nearest token | **settled** |
| Report card total size | 20 | — | `--em-font-size-total: 20px` | design | **settled** |
| Metric figure size | 28 | — | `--em-font-size-metric: 28px` | design | **settled** |
| Metric figure / card title / total weight | 700 (Public Sans Bold) | — | `--aura-font-weight-semibold` = 600 (**−100**) | app | **settled** |
| Report-detail title size | **24** (`241:10552`) | `--aura-font-size-l` = 16 | `--em-font-size-detail-title: 24px` | design | **settled** — see [A record title is not a page heading](#a-record-title-is-not-a-page-heading) |
| Grand-total size | 16 (`116:4998`) | `--aura-font-size-xl` = 18 | `--aura-font-size-l` = 16 (**−2px**) | design | **settled** |
| Totals subtotal value | 14 medium, primary (`116:4990`) | 13, secondary — whole row | `--aura-font-size-m` + medium, primary | design | **settled** |
| Filenames and trip dates | 12, **primary** (`215:2265`, `116:4943`) | `.muted-xs`, secondary | primary — a filename is content | design | **settled** |
| Section-heading role | one uppercase 12px style for all three headings | `.section-label` for two, `.status-history-heading` at 13 for the third | `.section-label` everywhere | design | **settled** |
| Grand-total weight | **800** (Inter Extra Bold, `116:4998`) | 600 | `--aura-font-weight-semibold` = 600 (**−200**) | app | **settled** |
| **Label case** | Title Case — `Expense Type`, `Unit Price`, `VAT Rate`, `Edit Expense` (`358:3267`) | sentence case throughout | sentence case **for now** | deferred | **open** — see [Label case is undecided](#label-case-is-undecided) |

The whole font block was deleted rather than ported: 450/550/650 were tuned for Inter's
variable-weight axis and are meaningless against Instrument Sans. The design's own
weights (500 Medium, 600 Semibold) are exactly the Aura defaults.

**No `@import` is needed.** Aura bundles the Instrument Sans webfont —
`document.fonts` reports `Instrument Sans 400 700 loaded` with no import in the theme
file. Had it not, dropping the Inter import would have silently fallen back to the
system stack.

## The scale

At `--aura-base-font-size: 14`, on Vaadin 25.2.1:

| Token | Size | Line height | Used for |
|---|---|---|---|
| `--em-font-size-title` | 40 | — | page headings (off-scale, see below) |
| `--em-font-size-section` | 30 | — | in-page section headings (off-scale, see below) |
| `--em-font-size-detail-title` | 24 | — | a record's own title (off-scale, see below) |
| `--aura-font-size-xl` | 18 | 26 | grand total, list title |
| `--aura-font-size-l` | 16 | 22 | card totals, amounts, detail title, preview amount |
| `--aura-font-size-m` | 14 | 20 | body, card titles, line amounts |
| `--aura-font-size-s` | 13 | 18 | secondary text, line names, callouts, totals rows |
| `--aura-font-size-xs` | 12 | 16 | eyebrows, section labels, technical detail |

Formulas and the derivation trap are in
[`../tokens/token-reference.md`](../tokens/token-reference.md). Two things not to guess:
the ramp is **not** geometric (`xs` is a clamped 0.85 of `m`; `s` is the *midpoint* of
`m` and `xs`), and **Aura's docs are wrong** — they give `xs` as 11px where the app
gives 12px (F-068).

## Text roles

Utility classes in `styles.css`, so a role is named once rather than restated per view:

| Class | Renders |
|---|---|
| `.muted` | secondary colour, `font-size-s` |
| `.muted-xs` | secondary colour, `font-size-xs` |
| `.section-label` | uppercase, `0.05em` tracking, `font-size-xs`, semibold, secondary colour |

## Off-scale

| Design value | Where | Nearest | Decision |
|---|---|---|---|
| 40px | page heading (`156:5404`) | `xl` 18 — top of the scale | `--em-font-size-title: 40px` |
| 30px | section heading (`156:5737`) | `xl` 18 — top of the scale | `--em-font-size-section: 30px` |
| 15px | expense row titles, amounts (16 nodes) | `m` 14 / `l` 16 | accept `l` = 16px (**+1px**) |
| 15px | expense row titles **and** amounts, report detail (12 more nodes) | `m` 14 / `l` 16 | as above — `l` = 16px. The row's name and its amount land on the *same* token, which the app currently splits across 13 and 14 |
| 24px | report-detail title (`241:10552`) | `xl` 18 — top of the scale | `--em-font-size-detail-title: 24px` |
| 20px | report card totals (4 nodes, one per card) | `xl` 18 — top of the scale | `--em-font-size-total: 20px` |
| 28px | metric figures (3 nodes, and 3 more on Approvals) | `xl` 18 — top of the scale | `--em-font-size-metric: 28px` |

Neither 40 nor 30 has an Aura token anywhere near it, and both appear on every reference
page, so each gets a property. 15px is one pixel from a token, and a project property that
shadows the type scale for one pixel is where per-view drift starts.

## Two heading sizes, one token

The design draws its page heading at **24px** on the report-list frame (`116:2499`) and at
**40px** on the reference-table frames (`156:5404`). Same role, same token, two values —
the design contradicts itself across frames, and nothing in either frame says which is
current.

**Decided: 40px, and the 30px section step alongside it.** Taken deliberately over the
cheaper option of keeping 24 and reporting the drift.

**This is a foundation change, not a view's**, because the property is shared: it is
recorded here rather than inside a view's spec so that every consumer moves in one place.
Two consumers move by 16px:

| Consumer | Was | Becomes |
|---|---|---|
| `report-card` title ([`../components/report-card.md`](../components/report-card.md)) | 24 | 40 — **since split off** to `--em-font-size-detail-title`, back at 24 |
| app shell's small-screen greeting ([`../components/app-shell.md`](../components/app-shell.md)) | 24 | 40 |

**The consumer list above was one row longer, and that row was wrong.** It claimed "every
view's page heading" as a consumer, citing a line that is in fact the shell's small-screen
greeting — the row below it. When #169 made the change and looked, **no view's page heading
was on this property at all**: the report list's is `.reports-title` at
`--aura-font-size-xl` (18), and Approvals, Review history and Users render an unstyled
`<h2>` at Aura's own default. So the reflow moved exactly two things, both card titles, and
the property only became a page-heading token when this issue's own `.page-title` class
started using it.

That correction makes the consequence **worse**, not better, and it is worth seeing before
the report-list issue takes its decision: the report card and approval card titles now
render at 40px directly beneath an 18px page heading, so the card titles are more than
twice the size of the heading above them. Both views are visibly hierarchy-inverted today.
It is still not this issue's call to fix — see the note below — but it is no longer the
mild "grows with the page heading" the table implied.

A **report card title is not a page heading** and should not follow this token to 40px —
it shared `--em-font-size-title` only because both happened to be 24. **Decided:** the card
title is a record's own title and sits on `--em-font-size-detail-title` (24), the design's
value on frame `116:2499` — see [`../components/report-card.md`](../components/report-card.md)
§ *Title size*. The approval and review-history cards share the class and moved with it.
The app shell's 50px greeting still sits only 10px above the page heading it was drawn to
tower over; that one goes back to the designer with the frames.

### A record title is not a page heading

The report-detail frame (`116:4444`) draws its title at **24px**, where the reference
frames draw a page heading at 40 and `--em-font-size-title` was re-decided to 40 for that
role. Third frame, third value for what could be read as one role — so this needed
deciding rather than defaulting either way.

**It is a different role, and the frame's geometry proves it.** The `Title Text` node is
769×34 holding a 44-character title: one line at 24px with a 1.4 line height. At 40px the
same string wraps to two lines in the 900px column and three in a narrower one. The design
laid it out as a single line of *record data* — the report's name beside its status pill —
not as a static view name.

That is the argument this file already accepted for `report-card`: "a report card title is
not a page heading and almost certainly should not follow this token to 40px". Applying it
here is consistent with that, not a new exception.

**It takes a property rather than the nearest token.** `xl` 18 is 6px away and is the top
of the text scale, so there is nothing to round to; and 24 is the display ramp's middle
step, which had no property until now. It is named for its role rather than its size
(`--em-font-size-detail-title`, not `--em-font-size-heading`) so that it cannot quietly
become a general-purpose 24 — which is how `--em-font-size-title` came to be shared by a
page heading and two card titles in the first place.

**It did not resolve `report-card` at the time**, and this file deferred that title to the
report-list issue rather than borrow the new property pre-emptively. That decision has since
been taken: the card title is the same record's name in list context, so it now sits on
`--em-font-size-detail-title` too — one property, one role, two consumers. See
[`../components/report-card.md`](../components/report-card.md) § *Title size*.

### The display ramp

20/24/28 form a coherent 4px ramp **above** the text scale, and all three are what the
design uses for its loudest type: a card's total, a page or card heading, a metric
figure. Aura's scale stops at `xl` 18, so none of the three can be derived — 20 is 2px
past the top and 28 is 10px past it.

Both new values took a property rather than the nearest token, which is a deliberate
exception to the "within a pixel or two takes the token" rule that settled 15px:

- **28px** is nowhere near a token, recurs on every metric, and the Approvals frame
  (`327:11681`) reuses the same card — so the alternative is two views inventing 28px
  separately.
- **20px** is only 2px past `xl`, but it is the report card's total sitting directly
  under a 24px title. Flattening it to 18px compresses a deliberate 24/20 pairing into
  24/18 and makes the money read as secondary to the title, which inverts what the card
  is for.

Whether this ramp should be three project properties or three real tokens in the
design's own scale is the designer's call, and is raised as a question rather than
assumed — see the survey follow-up issue.

### Weight 700 has no Aura token

The frame draws its metric figures, card titles and totals in Public Sans **Bold**
(700). Aura's weight scale stops at `--aura-font-weight-semibold` (600) and defines no
bold. 600 is used and the missing 100 accepted, consistent with the settled row above
recording that the design's *specified* weights (500/600) are exactly the Aura
defaults — the 700 comes from the drawn text, not from a variable, and the drawn text on
this frame is already known to be unreliable (see the defect below).

## Label case is undecided

The line-editor frame (`358:3267`) draws every field label in **Title Case** —
`Expense Type`, `Unit Price`, `VAT Rate`, and the dialog header `Edit Expense` — where the
app has been sentence case since the first view. This is the only row in this file that is
**open** rather than settled, and it is open on purpose: it is a global copy convention, so
resolving it per-component is the one thing that cannot be right.

The frame is not self-consistent evidence. `Receipt (optional)` is sentence case on the
same frame, and `Quantity` and `Comment` are single words that cannot distinguish the two
conventions. The travel-editor frame (`253:10597`) is split the same way: `Edit Travel
Info`, `Destination Country`, `Travel Purpose`, `Kilometre Allowance (km)`, `Parking Fees
(€)` and `Trip Total` are Title Case, while `Free lunch provided`, `Trip not eligible for
daily allowance` and both checkbox labels on the same frame are sentence case. No other surveyed frame — `116:2499` (report list), `116:4444` (report
detail), `156:5396` (reference tables) — establishes Title Case anywhere. So the drawn
evidence is one frame's four labels against three frames' worth of sentence case, which is
thin for a change that touches every label in the app.

**The app keeps sentence case until a human decides**, and the decision has its own ticket
rather than riding along with a per-view redesign. Taking Title Case in the line editor
alone would make that dialog the app's single Title-Case surface, sitting directly over a
sentence-case report detail — visibly worse than either convention applied consistently.

A later survey should raise this again. An **open** row means unresolved, not resolved in
the app's favour.

### The VAT-rate frame adds the strongest evidence yet, and it is a new kind

Frame `143:1781` draws the **page heading** as `VAT Rates` where the app renders `VAT
rates`, and the grid headers as `Rate` / `Actions`. On its own that is one more frame's
worth of Title Case. What makes it different is what the app already ships:
`ReferenceTabs` renders the design's Title-Case **`VAT Rates`** tab, and
`AllowanceRatesView`'s own `H2` is **`Allowance Rates`**.

So the app is already split, and split *within one view*: on `/vat-rates` a Title-Case tab
sits directly above a sentence-case heading of the same three words, 40px apart, and the
sibling route's heading uses the other convention. That is not a convention waiting to be
chosen — it is two conventions already shipping in the same viewport.

It stays **open** all the same, and deliberately. Page-heading case reaches every view in
the app — Reports, the report detail, Users — so settling it inside a reference-view
redesign would leave three headings Title Case and the rest sentence case, which is the
same failure one scale up. What this frame changes is the **cost of leaving it open**: it is
now visibly wrong on a shipped screen rather than only inconsistent across a spec folder,
which is the argument for the label-case ticket going first rather than last.

Note that heading case and **field-label** case may reasonably be decided differently —
Title Case headings over sentence-case field labels is a coherent house style, and several
are. The ticket should put the two questions separately rather than as one.

## Known design defect

Frame `116:4444` declares `Instrument Sans` and renders **three** families — Instrument
Sans (11 nodes), Inter (44), Public Sans (10). The app follows the *variable*, not the
drawn text (ADR-0025 decision 4): a literal reading would have kept Inter, the very value
the design was replacing. The stray families are the designer's to fix (F-069).

Confirmed on a **second** frame: `116:2499` "My Expenses" (the report list) draws Public
Sans across its metric cards, report cards and section headers, and Inter on one section
header — the same defect with the same resolution. Two frames now, so it is systematic
rather than a slip on one screen.
