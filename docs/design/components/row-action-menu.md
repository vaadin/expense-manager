# Row action menu

**Category:** composite
**Origin:** design
**Implementation:** unaudited
**Code:** `com.vaadin.expensemanager.base.ui.RowActionMenu` (#169)
**Design:** node `147:4473` › `Grid Buttons` (grid variant, 36×34) and `178:2029`
(card variant, 21×34); glyph `lucide/ellipsis-vertical` at `170:7881` / `178:2033`.
Placed on frame `156:5396` in both the rate card rows and every grid row, on frame
`143:1781` on every row of the VAT-rate grid — the grid variant, four placements — and on
frame `116:4444` on **every** row of the report detail — the card variant, six placements

## Overview

The vertical-ellipsis (`⋮`) overflow menu that carries a row's actions. It replaces the
inline row of icon buttons the reference views draw today.

Use it wherever a repeating row has one or more actions: a `Grid` row, a
[`rate-list-card.md`](rate-list-card.md) row, an
[`expense-line-card.md`](expense-line-card.md) row. **Not** for a view-level action — those
stay visible buttons in the toolbar ("Add Year", "Copy Year", "Add").

**On the report detail it is the *only* route to a row's actions.** The report-detail
survey decided the row itself is not clickable, so the menu replaces both the inline
buttons and the whole-row click-to-edit. That makes the trigger the sole affordance on
the row, which raises the stakes on everything in
[What this costs](#what-this-costs-and-why-it-is-still-the-designs-call) below.

**Not** `ContextMenu` on a `Button`. Vaadin's own guidance is explicit: a `MenuBar` with a
single top-level item *is* a drop-down button, and gives better keyboard and
assistive-technology behaviour than a `Button` paired with a `ContextMenu`. Use `MenuBar`.

**Not** `GridContextMenu` either — that is right-click only, and the docs say a context
menu must never be the only route to a task.

## Anatomy

| Part | Element |
|---|---|
| Trigger | a single `MenuItem` on a `MenuBar`, icon-only, `LucideIcon.ELLIPSIS_VERTICAL` |
| Menu | that item's `SubMenu` |
| One action | a `MenuItem` with a **text** label |

**Every item is text-labelled.** The design shows only the closed trigger, so the menu's
contents are this spec's, and text is not a style choice: it is what makes the actions
discoverable at all once they are behind a click, and it satisfies ADR-0020's rule that
meaning never rests on an icon or a colour alone.

## Tokens used

| Part | Token |
|---|---|
| Glyph | `--em-icon-size-m` (20) — `LucideIcon.SIZE_M`, the default |
| Glyph colour | `currentColor` |
| Trigger geometry | `MenuBar`'s own; the design's 21×34 and 36×34 are the drawn boxes, not values to set |
| Overlay | Aura's own menu overlay — `--aura-shadow-m`, `--vaadin-radius-m` |

No property is overridden. If the trigger's horizontal padding needs tightening,
**`MenuBarVariant.LUMO_ICON` and `LUMO_TERTIARY_INLINE` will not do it** — both are
Lumo-only and silently inert under Aura (F-013, F-017). Use a scoped CSS class.

## API

Stock `MenuBar`. The project rule is the shape:

```java
var menu = new MenuBar();
var trigger = menu.addItem(LucideIcon.ELLIPSIS_VERTICAL.create());
trigger.setAriaLabel("Actions for " + rowLabel);
trigger.getSubMenu().addItem("Edit", event -> openEditor(row));
```

An action that is **unavailable on this row but available on others** takes an enabled
flag rather than being left out — see [Reorder at the list
boundary](#reorder-at-the-list-boundary):

```java
menu.addAction("Move up", index > 0, () -> move(row, -1));
```

`setAriaLabel` on the **trigger** is required, not optional: an icon-only button with no
accessible name is announced as "button" and nothing else. Name the row in it — "Actions"
alone is useless when the page has fourteen of them.

## States

| State | Behaviour |
|---|---|
| default | icon-only trigger, no fill |
| hover | Aura's own `vaadin-menu-bar-button` hover. **Unverified** — Aura expresses button hover as a `::before` overlay at 3% opacity, which is invisible to a computed-style check and may not read at all against a card's surface fill |
| active (pressed / open) | Aura's own; the trigger stays visibly pressed while its menu is open |
| focus | Aura's focus ring on the trigger. Inside a `Grid` the trigger is reachable by Tab into the row and by the grid's own cell navigation |
| disabled | a row whose actions are all unavailable renders **no** menu, rather than a disabled trigger — the same rule `expense-line-card` follows. An individual `MenuItem` may be disabled via `setEnabled(false)`, but note that disabled root buttons are not focusable or hoverable unless the `accessibleDisabledButtons` feature flag is on, so a tooltip explaining *why* will not show |
| error | n/a — a menu has no validation state |

## What this costs, and why it is still the design's call

Moving actions from three visible buttons into a menu costs a click and hides the
available actions until it is opened. That is a real regression in discoverability, and it
is the design's decision under ADR-0025 rather than a neutral one.

Three places it needs watching when it lands:
- **`VatRateView` and `ExpenseTypeView` carry four row actions each** — edit, move up,
  move down, activate/deactivate — where the reference frame's rows carry one. A menu
  suits four better than four cramped icon buttons do. The *boundary-disabled* reorder
  buttons are now decided rather than watched: see [Reorder at the list
  boundary](#reorder-at-the-list-boundary).
- **The reorder buttons are the app's only affordance for row order.** Behind a menu,
  reordering becomes open-menu-click-close per step. Worth raising before the retrofit.
- **The report detail loses a one-click edit on its largest target.** Editing a line goes
  from clicking anywhere on the row to opening a 21px menu and choosing Edit. The row also
  carries the most actions of any caller: a trip row has four (Edit, Remove, and its
  allowance rows' own), and a generated allowance line has up to three (Receipt, Override,
  Reset to calculated). Keeping the click as an unadvertised shortcut alongside the menu
  was the alternative and was **not** taken — the design draws no click affordance, and
  ADR-0025 gives it the call.

The second goes to the reference-view issues; the third is the report-detail redesign's,
and is the row to check first in its visual verification.

## Reorder at the list boundary

`VatRateView` and `ExpenseTypeView` order their rows manually (ADR-0018), so the first row
has no **Move up** and the last no **Move down**. Behind a menu that is a real question,
because the frame draws one action and says nothing about four.

**Decision: a disabled `MenuItem`, not an omitted one.** The menu keeps the same four items
on every row, and the greyed item *is* the boundary information — "this row is already at
the top" is what the user needs to know, and an item that quietly vanishes says it to
nobody. A varying menu also moves **Activate/Deactivate** up a slot on the first and last
rows, so the one destructive-ish action changes position depending on which row you opened.

This needs an `addAction(String, boolean, Runnable)` overload on `RowActionMenu`; the
existing two-argument form stays the common case.

**What it costs, honestly.** A disabled root menu item is not focusable or hoverable
unless the `accessibleDisabledButtons` feature flag is on, so a tooltip explaining *why* it
is disabled cannot be attached — the user gets the greyed item and no sentence. That is the
same limitation the **disabled** row in [States](#states) records, and it is accepted here
rather than solved: the alternative on offer was omission, which explains even less.

**Reordering through a menu is worse than reordering through a button**, and this makes it
concrete: moving a rate three places is now open-menu → click → menu closes, three times
over, where it used to be three clicks on a button that stayed put. It stands because
ADR-0025 gives the frame the call on the affordance, and it is the first thing to raise if
an admin ever complains about ordering.

## Cross-references

[`rate-list-card.md`](rate-list-card.md) — one caller ·
[`../foundations/iconography.md`](../foundations/iconography.md) — the `ellipsis-vertical`
row this survey closes ·
[`button.md`](button.md) — the inline icon button this replaces ·
ADR-0020 (never icon or colour alone), F-013 / F-017 (Lumo-only variants)
