# Data grid

**Category:** themed Vaadin primitive
**Origin:** design
**Implementation:** drifted
**Code:** stock `com.vaadin.flow.component.grid.Grid` — `ReferenceConfigView#grid`
(`VatRateView`, `ExpenseTypeView`), `AllowanceRatesView#foreignGrid`,
`UserManagementView`. No project wrapper and no CSS class
**Design:** node `143:2465` › `Grid with cells` (component definition `143:2153`,
annotated `<vaadin-grid>`) on frame `143:1781` "Reference Tables - VAT Rates"; the same
component on `156:5396` as the foreign per-diem list. Columns `I143:2465;8708:24251`
(Rate) and `I143:2465;8708:25671` (Actions); row backgrounds `I143:2465;8708:8124`…`8132`

## Overview

A **list that grows** — an unbounded set of records, one per row, each row carrying its
actions behind a [`row-action-menu.md`](row-action-menu.md) `⋮`. Stock `Grid` with one
theme variant; this app does not wrap it and overrides no property.

**Not** [`rate-list-card.md`](rate-list-card.md), which is the sibling it is confused
with, because the design puts the two side by side on frame `156:5396`: a small **fixed**
set of settings, each edited individually, is that card — three rows separated by dotted
rules, no header, no column structure. A set whose size is the data's business is this.
The test is whether a fourth row can arrive without a designer being consulted.

**Not** [`expense-item-card.md`](expense-item-card.md) either: those rows are a report's
content inside one card, and they are laid out by the card rather than by columns.

## Anatomy

| Part | Element | Design node |
|---|---|---|
| Container | `vaadin-grid` — 1px border, `--vaadin-radius-m`, clipped | `143:2465` |
| Header row | `::part(header-cell)`, 32px, bottom rule | `I143:2465;8705:8569` |
| Body row | `::part(row)`, ~40px, bottom rule, striped when odd | `I143:2465;8708:8124`… |
| Data column | `Grid.Column`, `setAutoWidth(true).setFlexGrow(0)` | `I143:2465;8708:24251` |
| Actions column | a component column, `ColumnTextAlign.END`, `setFlexGrow(1)` | `I143:2465;8708:25671` |
| Row actions | one [`row-action-menu.md`](row-action-menu.md) per row | `…;5484:18629` |

The **Actions column takes the remaining width and right-aligns its content** — the frame
draws it `flex-[1_0_0]` with `items-end`, and both the header label "Actions" and the `⋮`
sit against the right edge. `setFlexGrow(1)` alone leaves the content left-aligned in a
wide cell, which is what the app renders today.

## Tokens used

Every row here is an **Aura stock value**. The design asks for nothing this theme does not
already do, and the whole visual difference from the app's current grids is one theme
variant plus two column calls.

| Part | Token | Design value |
|---|---|---|
| Container background | `--vaadin-grid-background` | bound, `rgba(255,255,255,0.85)` |
| Container border | `--vaadin-grid-border-color` / `-width` | 1px, kit `--aura-border-color-secondary` |
| Container radius | `--vaadin-grid-border-radius` — the design's 9 is `--vaadin-radius-m` | 9 |
| Row rule | `--vaadin-grid-row-border-width` | 1px |
| Odd-row fill | `--vaadin-grid-row-odd-background-color` | bound, `rgba(11,11,11,0.04)` |
| Header font size | `--vaadin-grid-header-font-size` — `--aura-font-size-m` (14) | 14 |
| Header weight | `--vaadin-grid-header-font-weight` — `--aura-font-weight-medium` (500) | 500 |
| Header colour | `--vaadin-grid-header-text-color` — `--vaadin-text-color` | bound |
| Cell text | `--aura-font-size-m` (14), `--aura-font-weight-regular` | 14 / 400 |
| Cell text colour | `--vaadin-text-color` | bound |
| Cell padding | `--vaadin-grid-cell-padding` | 8 horizontal; 6 header / 10 body vertical |
| Header height | Aura's own, from `--aura-base-size` | 32 — **unverified** |
| Row height | Aura's own, from `--aura-base-size` | ~40 (19 + 2 + 19) — **unverified** |

**Whether Aura's defaults already equal the design's 9px radius, 32px header and 40px row
is unverified** — a survey does not boot the app. The design's values are all on the
scale, so the expectation is that nothing needs setting; a verification pass confirms it
or turns three rows into overrides.

**Two of the design's variables are not CSS properties.**
`--aura-border-color-secondary` does not exist (it is `--vaadin-border-color-secondary`)
and `--components/grid-even-row-background` is a Figma variable *path*, note the slash.
Copying either across renders its hardcoded fallback forever (F-062). Neither needs
translating in practice, because the properties above are the real knobs.

**One correction to a note #169 left in `styles.css`:**
`--vaadin-grid-row-odd-background-color` **is** an Aura-supported style property in
Vaadin 25.2, not an undefined one. `GridVariant.ROW_STRIPES` is still what a caller
reaches for — the variant is what switches striping on at all — but the property is a real,
documented knob for the stripe colour rather than "a property nobody can confirm". That
comment should be corrected where it stands.

## API

```java
grid.addThemeVariants(GridVariant.ROW_STRIPES);
grid.setSelectionMode(Grid.SelectionMode.NONE);
grid.setAllRowsVisible(true);

grid.addColumn(dto -> formatPercent(dto.value()))
        .setHeader("Rate").setAutoWidth(true).setFlexGrow(0);
grid.addComponentColumn(this::actions)
        .setHeader("Actions").setAutoWidth(true).setFlexGrow(1)
        .setTextAlign(ColumnTextAlign.END);
```

`GridVariant.ROW_STRIPES`, never `LUMO_ROW_STRIPES` — a Lumo-only variant is accepted and
silently ignored under Aura (F-013, F-017).

**`SelectionMode.NONE` is not a detail.** `Grid`'s default is `SINGLE`, so a row click
selects the row and paints `--vaadin-grid-row-selected-background-color`. The design draws
no selected state, and selection means nothing on any of this app's grids — nothing reads
`getSelectedItems()`. Left at the default, clicking a VAT rate highlights it for no reason
and the highlight survives until another row is clicked.

**Height is a per-caller decision, and the design draws both.**
`setAllRowsVisible(true)` where the list is short and the page should grow with it — the
VAT-rate and expense-type grids, where the frame draws no scrollbar and the whole list is
four rows. A **fixed height** where the list is long and the page should not grow: the
foreign per-diem grid's 512px, "a window, not a listing". Never both.

**Sorting stays off**, on every grid whose rows carry a manual display order. The design
draws no sorter indicators, and the reason is structural rather than visual: the reference
rows are ordered by an admin through Move up / Move down (ADR-0018), so a sortable column
offers the user a second, conflicting ordering and no way to get back. A grid whose order
*is* the data's — the foreign per-diems — may sort.

## Cell values are formatted by the view, and one format is a decided divergence

The design draws the Rate column as **`13.5%`**; the app renders **`13.5 %`**
(`ReferenceViewSupport.formatPercent`, which also feeds `ExpenseTypeView`'s Default VAT
rate column and every row's `aria-label`).

**The app wins, and it is settled rather than absent.** Finnish typography puts a space
between the figure and the unit, so this is a locale rule rather than a visual choice, and
it is not the design's to make. Reported to the designer as a minor defect. Recorded here
because a later survey comparing this grid to its frame lands on exactly this difference,
and rediscovering it is the drift this folder exists to stop.

## States

| State | Behaviour |
|---|---|
| default | bordered container, `--vaadin-radius-m`, 1px row rules, odd rows tinted |
| hover | `--vaadin-grid-row-hover-background-color`, Aura's own. **The design draws no hover.** Two things to check in verification: whether it reads at all against an already-tinted odd row, and whether it is *wanted* — the reference rows are not clickable, so a row-wide hover advertises a click that does nothing. `row-action-menu.md` decided the `⋮` is the only affordance |
| active | n/a — no row is pressable. The `⋮` inside it has its own pressed state |
| focus | `::part(focused-cell)`'s ring, from the grid's own cell navigation. A `⋮` inside a cell is reachable by Tab into the row and by arrow keys |
| disabled | n/a — `Grid` has no disabled state, and none is drawn. An **inactive record** is a data state shown in the Status column, not a disabled row: rows the admin may reactivate must stay live (ADR-0018) |
| error | n/a — a grid has no validation state. Editor validation surfaces in [`error-summary.md`](error-summary.md) inside the dialog |
| selected | **n/a by configuration**, per `SelectionMode.NONE` above. The design draws no selected row and nothing in this app reads a selection |
| empty | **not drawn.** The frame always has rows, so a grid filtered to zero — or a reference table with no rows at all — has no designed state. [`empty-state.md`](empty-state.md) is the app's own (code origin, ADR-0017) and choosing where it goes is not this spec's to invent; see [`filters-bar.md`](filters-bar.md) § *Filtering to nothing* |

## Code example

```java
var grid = new Grid<VatRateDto>();
grid.addThemeVariants(GridVariant.ROW_STRIPES);
grid.setSelectionMode(Grid.SelectionMode.NONE);
grid.setAllRowsVisible(true);

grid.addColumn(dto -> formatPercent(dto.value()))
        .setHeader("Rate").setAutoWidth(true).setFlexGrow(0);
grid.addColumn(dto -> statusLabel(dto.active()))
        .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
grid.addComponentColumn(dto -> new RowActionMenu(formatPercent(dto.value()))
                .addAction("Edit", () -> openEditor(dto)))
        .setHeader("Actions").setAutoWidth(true).setFlexGrow(1)
        .setTextAlign(ColumnTextAlign.END);
```

## The Status column the design does not draw

The frame's grid has **two** columns, Rate and Actions. It has no Status column — and on
the same frame it adds a **Status filter** ([`filters-bar.md`](filters-bar.md)).

**The app's Status column stays.** This is a settled divergence in the app's favour, on
two grounds that outrank a visual preference:

- ADR-0018's whole point is that deactivating never deletes and the grid shows inactive
  rows *so an admin can reactivate them*. Without a column, an active and an inactive rate
  are indistinguishable, and the row's own menu offers "Activate" or "Deactivate" with
  nothing on screen saying which applies.
- ADR-0020 forbids meaning resting on colour alone. The design does not put status in a
  colour either — it puts it nowhere, which is the same failure one step further on.

The design's own filter is the argument: a frame that offers to filter by status is a frame
that agrees status belongs on this view. Reported to the designer as a defect on the frame
rather than as a difference of taste.

## Divergence

| What | Spec | Code today | Owner |
|---|---|---|---|
| Row stripes | `GridVariant.ROW_STRIPES` | absent on `ReferenceConfigView#grid`; present on `foreignGrid` | the reference-view issue |
| Selection | `SelectionMode.NONE` | Vaadin's `SINGLE` default on every grid in the app | the reference-view issue |
| Actions alignment | `ColumnTextAlign.END` | left-aligned in a flex-grown cell | the reference-view issue |
| Row actions | one `⋮` per row | four inline icon buttons in a `HorizontalLayout` | the reference-view issue |
| Actions column contents | `RowActionMenu` | `iconButton` / `reorderButton` / `activeToggle` from `ReferenceConfigView` | the reference-view issue |

`AllowanceRatesView#foreignGrid` already carries the stripes and the `⋮`; it diverges only
on selection mode and the actions column's alignment.

## Cross-references

[`rate-list-card.md`](rate-list-card.md) — the sibling this is confused with ·
[`row-action-menu.md`](row-action-menu.md) — every row's actions ·
[`filters-bar.md`](filters-bar.md) — the row above it ·
[`page-header.md`](page-header.md) ·
ADR-0018 (deactivate never deletes), ADR-0020 (never colour alone), ADR-0025 (the design
as contract), F-013 / F-017 (Lumo-only variants), F-062 (undefined properties)
