# Filters bar

**Category:** composite
**Origin:** design
**Implementation:** none
**Code:** — nothing builds this row yet. `AllowanceRatesView#countrySearch` is a lone
search field in a different composition, not an instance of this
**Design:** node `143:1951` › `filters-bar` on frame `143:1781` "Reference Tables - VAT
Rates"; search `143:1952` (`<vaadin-text-field>`, 300px), status `143:1953`
(`<vaadin-combo-box>`, 180px)

## Overview

The row directly above a [`data-grid.md`](data-grid.md) that narrows what the grid shows:
free-text search on the left, a property filter on the right, pushed apart, 20px of air
before the grid's top border.

**It carries filters and nothing else.** That is the whole distinction from
`.allowance-toolbar`, the row it will be mistaken for: a toolbar carries **actions** — the
year selector plus "Add Year" and "Copy Year" — and is separated from the content below it
by a hairline rule. This bar has no rule, no button, and no action. Putting a button in it
is how the two collapse into one ambiguous strip.

Reach for it when a grid is long enough that a user arrives looking for one row. It is
worth building on a four-row VAT-rate grid only because the same bar serves
`ExpenseTypeView`, where there is a name to search — and because the design draws it
(ADR-0025). On the rate view itself, searching four percentages earns little; that is a
cost in usefulness, not a difference from the design, and it is not a reason to skip it.

## Anatomy

| Part | Element | Design node |
|---|---|---|
| Bar | `.filters-bar` — a `HorizontalLayout`, `JustifyContentMode.BETWEEN` | `143:1951` |
| Search | `TextField`, 300px, placeholder "Search" | `143:1952` |
| Property filter | `ComboBox<…>`, 180px, placeholder naming the property | `143:1953` |

**Both fields render with no visible label.** The frame hides the `Label` layer on each
(`I143:1952;19:19`, `I143:1953;8961:6740`), so each **must** carry an `aria-label` — the
`yearSelector` precedent. A bare unlabelled field is announced as nothing at all, and that
is the accessibility floor rather than a preference.

**The visible word is a placeholder, not a value.** The frame paints "Search" and "Status"
in the field's Value slot, which reads at first like a selected value — but the combo
box's `Clear button` layer (`I143:1953;8968:1413`) is **hidden** in the same frame, which
is exactly what `ComboBox` renders when it holds nothing. So the resting state is *no
filter*, drawn through the placeholder.

The design paints both placeholders at `--vaadin-text-color`, i.e. body colour. Aura's
stock placeholder colour is taken instead; a placeholder at full text colour is
indistinguishable from a value, which is the thing the paragraph above had to reason
around. Reported to the designer.

## Tokens used

Every field value is Aura stock — the design asks for the theme's own field, and nothing
here overrides a property.

| Part | Token | Design value |
|---|---|---|
| Field radius | `--vaadin-radius-m` (9) | 9 |
| Field border | `--vaadin-input-field-border-width` / `-color` | 1px |
| Field background | `--vaadin-input-field-background` | bound |
| Field shadow | `--aura-shadow-xs` | `0 1px 4px -2px` — the design's own Shadow XS |
| Field text | `--aura-font-size-m` (14), `--aura-font-weight-regular` | 14 / 400 |
| Field padding | Aura's own | 16 horizontal / 9 vertical |
| Dropdown glyph | `--em-icon-size-s` (16) | 16 |
| Gap below the bar | `--em-card-padding` (20) | 20, drawn as the bar's `padding-bottom` |
| Search width | `300px` — a literal | 300 |
| Filter width | `180px` — a literal | 180 |

The two widths are literals, not tokens, exactly as `AllowanceRatesView`'s
`YEAR_SELECTOR_WIDTH` (180) and `SEARCH_WIDTH` (400) are. A custom property for a single
placement is where a parallel scale starts. Note the sibling frame draws the search at
400px and this one at 300px — per-frame values, not a convention either of them
establishes.

## API

Stock `TextField` and `ComboBox`. The project rules are the shape:

```java
search.setPlaceholder("Search");
search.setAriaLabel("Search rates");
search.setClearButtonVisible(true);
search.setValueChangeMode(ValueChangeMode.LAZY);
search.addValueChangeListener(event -> view.refreshAll());

status.setPlaceholder("Status");
status.setAriaLabel("Status");
status.setItems("Active", "Inactive");
status.setClearButtonVisible(true);
status.setAllowCustomValue(false);
status.addValueChangeListener(event -> view.refreshAll());
```

**The filter sits outside the grid, so it goes through the data view rather than a `Grid`
API**: one filter that *reads* both fields, re-applied on change. Adding a filter per
keystroke stacks them and the grid narrows monotonically until the page reloads — the trap
`AllowanceRatesView` already names.

```java
view = grid.setItems(items);
view.addFilter(this::matches);        // once, per setItems
```

**An empty control means "no filter", not "match nothing".** With `Active` / `Inactive` as
the only two items and nothing selected meaning both, no third "All" item is needed and the
clear button is the way back — which is what the frame draws.

## Filtering to nothing

Search plus a status filter can produce **zero rows**, and the frame draws no state for it.
Neither does it draw one for a reference table that is genuinely empty.

This spec does **not** invent one. [`empty-state.md`](empty-state.md) is the app's own
(code origin, ADR-0017), and a "no matches — clear the filters" state is a different thing
from "nothing here yet": the first must not offer an Add button and the second must. Both
belong to whoever owns `empty-state.md`, and the delta names it as a gap rather than
guessing. What this spec does settle: **the bar stays visible when the grid is empty.**
Hiding the controls that produced the empty result leaves the user no way to undo it.

## States

| State | Behaviour |
|---|---|
| default | two fields at rest, placeholders showing, no clear buttons — the frame hides both clear layers |
| hover | Aura's own field hover. **Unverified**; the design draws no hover state for either field |
| active | n/a on the bar. The combo box's open state is Aura's own overlay (`--aura-shadow-m`, `--vaadin-radius-m`) |
| focus | Aura's focus ring on whichever field has focus. Tab order is search → filter → the grid |
| disabled | n/a. A filter the user can see is one they can use, and neither field's availability depends on a role — both reference views are `@RolesAllowed("ADMIN")` in their entirety |
| error | n/a — a filter cannot be invalid. A term matching nothing is a zero-row result, not an error; see *Filtering to nothing* |

## Responsive

`flex-wrap: wrap` with `row-gap: var(--vaadin-gap-s)`, the `.allowance-toolbar` precedent:
at 360px a 300px field and a 180px field cannot share a row, and the alternative to
wrapping is a horizontal scrollbar on the page. The fields keep `max-width: 100%` so
neither forces the column wider than the shell's.

## Code example

```java
var bar = new HorizontalLayout(search, status);
bar.addClassName("filters-bar");
bar.setWidthFull();
bar.setPadding(false);
bar.setAlignItems(FlexComponent.Alignment.CENTER);
bar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
```

The 20px below the bar is the gap to the grid, so it belongs to whichever layout holds the
two — `setSpacing("var(--em-card-padding)")` on that column, not `padding-bottom` on the
bar. The frame draws it as the bar's own padding because Figma has no other way to say it.

## Cross-references

[`data-grid.md`](data-grid.md) — what it filters ·
[`page-header.md`](page-header.md) — the block above it ·
[`empty-state.md`](empty-state.md) — the zero-row gap this hands over ·
ADR-0017 (empty states), ADR-0025 (the design as contract)
