package com.vaadin.expensemanager.allowance.ui;

import java.math.BigDecimal;
import java.util.List;

import com.vaadin.expensemanager.allowance.AllowanceRateService;
import com.vaadin.expensemanager.allowance.DomesticPerDiemDto;
import com.vaadin.expensemanager.allowance.ForeignPerDiemDto;
import com.vaadin.expensemanager.base.ui.EditorDialog;
import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.base.ui.PageHeader;
import com.vaadin.expensemanager.base.ui.ReferenceTabs;
import com.vaadin.expensemanager.base.ui.RowActionMenu;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.RolesAllowed;

import static com.vaadin.expensemanager.allowance.ui.AllowanceViewSupport.formatMoney;
import static com.vaadin.expensemanager.allowance.ui.AllowanceViewSupport.formatRate;
import static com.vaadin.expensemanager.allowance.ui.AllowanceViewSupport.openDecimalEditor;

/**
 * ADMIN-only settings screen for the per-year allowance rate config (issue #48,
 * PRD 4.1/4.4, ADR-0008) — the allowance-package analogue of the reference-data
 * screens ({@code VatRateView} / {@code ExpenseTypeView}). Built to the design
 * in #169, frame {@code 156:5396}.
 *
 * <p>Top to bottom: the shared {@link ReferenceTabs} bar, the shared
 * {@link PageHeader} block, a bottom-ruled toolbar carrying the year selector
 * and the two year-seeding actions, the three per-year rates as one
 * {@code rate-list-card}, and the foreign per-diem section — a searchable,
 * striped, fixed-height grid. Every one of them is a sibling at the 40px
 * section gap, which is what frame {@code 156:5396} draws between them.
 *
 * <p><strong>What "Add Year" and "Copy Year" each mean.</strong> The design
 * splits one action into two, and they overlap deliberately rather than
 * accidentally:
 * <ul>
 * <li><strong>Add Year</strong> — seed a new year from the <em>most recent</em>
 * existing one. One field, one decision, and the common case; PRD 4.4 pins this
 * meaning to this action, so #169 kept it rather than redefining it as an
 * empty-year action.
 * <li><strong>Copy Year</strong> — seed a <em>named</em> target year from a
 * <em>named</em> source year. The deliberate case: reviving an older year's
 * figures, or filling a gap that is not simply "next".
 * </ul>
 * So "Add Year" is "Copy Year" with both years defaulted, and
 * {@link AllowanceRateService#addYear} is literally
 * {@link AllowanceRateService#copyYear} with the source resolved. Neither creates
 * an empty year: there is no designed empty state for a year with no rates, and
 * the domestic per-diem row is not optional.
 *
 * <p>Two-layer authorization (ADR-0008): {@code @RolesAllowed("ADMIN")} gates
 * navigation, while the real enforcement is {@link AllowanceRateService}'s method
 * security. Every editor is a shared {@link EditorDialog} (always-enabled Save +
 * top-of-form error summary, ADR-0020); the single-value ones go through
 * {@link AllowanceViewSupport#openDecimalEditor}, and the service's
 * {@code DomainRuleException}s — a target year that already exists, a source that
 * does not — surface in that summary rather than the generic error dialog.
 *
 * <p>Every row action sits behind a {@link RowActionMenu} ⋮, in the rate card and
 * in the grid alike, per {@code docs/design/components/row-action-menu.md}. That
 * costs a click and hides the action until the menu opens — a real regression in
 * discoverability, taken as the design's call under ADR-0025, and recorded in
 * that spec.
 */
@Route("allowance-rates")
@PageTitle("Allowance rates")
@RolesAllowed("ADMIN")
public class AllowanceRatesView extends VerticalLayout {

    /** The design's toolbar selector width (node {@code 156:6469}). */
    private static final String YEAR_SELECTOR_WIDTH = "180px";

    /** The design's search field width (node {@code 156:5409}). */
    private static final String SEARCH_WIDTH = "400px";

    /**
     * A fixed height, so the list scrolls under the search field instead of
     * growing the page — the design draws the grid as a window, not a listing.
     */
    private static final String GRID_HEIGHT = "512px";

    private final transient AllowanceRateService service;

    private final ComboBox<Integer> yearSelector = new ComboBox<>();
    private final TextField countrySearch = new TextField();
    private final Grid<ForeignPerDiemDto> foreignGrid = new Grid<>();

    /** Rebuilt per year: the one card holding the three per-year rates. */
    private final Div rateCard = new Div();

    /** Built once; hidden while no year is selected. */
    private final VerticalLayout foreignSection = new VerticalLayout();

    /**
     * The grid's current in-memory view, which is what {@link #countrySearch}
     * filters. Replaced on every year change, because {@code setItems} installs a
     * fresh data provider — and that is also what drops the previous year's
     * filter, so nothing accumulates.
     */
    private transient GridListDataView<ForeignPerDiemDto> foreignView;

    public AllowanceRatesView(AllowanceRateService service,
            AuthenticationContext authenticationContext) {
        this.service = service;
        setPadding(true);
        // The design's rhythm between the tab bar, the header block, the
        // toolbar, the rate card and the foreign section.
        setSpacing("var(--em-section-gap)");

        // No action in the header: this view's two are seeding actions that
        // belong to a year, so the design puts them in the toolbar instead.
        var header = new PageHeader("Allowance Rates",
                "The per-year rates the travel calculator costs against. History "
                        + "is kept per year — adding a new year copies the latest "
                        + "year's rates as a starting point and never changes prior "
                        + "years. Verify each figure against the Verohallinto "
                        + "decision for that year.");

        rateCard.addClassName("rate-list-card");
        buildForeignSection();

        add(new ReferenceTabs(getClass(), authenticationContext), header, toolbar(),
                rateCard, foreignSection);
        refreshYears(null);
    }

    // ------------------------------------------------------------- toolbar

    /**
     * The year selector on the left, the two seeding actions on the right, over a
     * hairline rule.
     *
     * <p>The selector carries <strong>no visible label</strong> — the design hides
     * it — so it carries an {@code aria-label} instead. A bare unlabelled field
     * would be announced as nothing at all.
     */
    private Component toolbar() {
        yearSelector.setWidth(YEAR_SELECTOR_WIDTH);
        yearSelector.setAllowCustomValue(false);
        yearSelector.setAriaLabel("Year");
        yearSelector.addValueChangeListener(event -> renderYear(event.getValue()));

        // Tertiary, not primary: with two of them side by side the design draws
        // neither as the page's one loud action.
        var copyYear = new Button("Copy Year", LucideIcon.COPY.create(),
                event -> openCopyYearEditor());
        copyYear.addThemeVariants(ButtonVariant.TERTIARY);

        var addYear = new Button("Add Year", LucideIcon.PLUS.create(),
                event -> openAddYearEditor());
        addYear.addThemeVariants(ButtonVariant.TERTIARY);

        var actions = new HorizontalLayout(copyYear, addYear);
        actions.setPadding(false);
        actions.setSpacing("var(--em-card-padding)");
        actions.setAlignItems(FlexComponent.Alignment.CENTER);

        var bar = new HorizontalLayout(yearSelector, actions);
        bar.addClassName("allowance-toolbar");
        bar.setWidthFull();
        bar.setPadding(false);
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return bar;
    }

    // ------------------------------------------------------------- year state

    /** Reloads the year list; selects {@code preferred} if given, else the newest. */
    private void refreshYears(Integer preferred) {
        var years = service.availableYears();
        yearSelector.setItems(years);
        Integer selection = preferred != null && years.contains(preferred)
                ? preferred
                : years.stream().findFirst().orElse(null);
        yearSelector.setValue(selection);
        // A value change to the same value doesn't fire the listener; render anyway.
        renderYear(selection);
    }

    private void renderYear(Integer year) {
        rateCard.removeAll();
        if (year == null) {
            rateCard.setVisible(false);
            foreignSection.setVisible(false);
            return;
        }
        rateCard.setVisible(true);
        foreignSection.setVisible(true);

        var domestic = service.domesticPerDiem(year).orElse(null);
        var km = service.kilometreRate(year).orElse(null);
        var meal = service.mealAllowance(year).orElse(null);

        // The design's glyphs, two of which say something false — a bed on a
        // per-kilometre rate, a taxi on a per-diem. Implemented as drawn
        // (ADR-0025 decision 1) and reported back to the designer with #160;
        // nothing rests on them, because every row is labelled in text.
        if (domestic != null) {
            rateCard.add(rateRow(LucideIcon.CAR_TAXI_FRONT.create(), "Domestic per Diem",
                    List.of(value("Full day (over " + domestic.fullDayMinHours() + "h)",
                                    formatMoney(domestic.fullDayAmount())),
                            value("Partial day (over " + domestic.partialDayMinHours()
                                            + "h)",
                                    formatMoney(domestic.partialDayAmount()))),
                    () -> openDomesticEditor(year, domestic)));
        }
        if (km != null) {
            rateCard.add(rateRow(LucideIcon.BED.create(), "Kilometre Allowance",
                    List.of(value(null, formatRate(km.amountPerKm()))),
                    () -> openKilometreEditor(year, km.amountPerKm())));
        }
        if (meal != null) {
            rateCard.add(rateRow(LucideIcon.UTENSILS.create(), "Meal Allowance",
                    List.of(value(null, formatMoney(meal.amount()))),
                    () -> openMealEditor(year, meal.amount())));
        }

        foreignView = foreignGrid.setItems(service.foreignPerDiems(year));
        foreignView.addFilter(this::matchesSearch);
    }

    /** The selected year — read by the grid's action cells rather than captured. */
    private int currentYear() {
        return yearSelector.getValue();
    }

    // ----------------------------------------------------- rate list card

    /**
     * One row of the rate card — {@code docs/design/components/rate-list-card.md}.
     *
     * <p>Plain {@link Div}s, not layouts: the row is a composition the design drew
     * rather than a Vaadin component, so its flex, gaps and dotted rule live in
     * the {@code rate-row} class (the same call {@code report-card} makes).
     */
    private Component rateRow(SvgIcon icon, String title, List<Component> values,
            Runnable edit) {
        icon.addClassName("rate-row-icon");

        var titleSpan = new Span(title);
        titleSpan.addClassName("rate-row-title");

        var valueGroup = new Div();
        valueGroup.addClassName("rate-row-values");
        for (var value : values) {
            if (valueGroup.getComponentCount() > 0) {
                var dot = new Span();
                dot.addClassName("rate-row-dot");
                valueGroup.add(dot);
            }
            valueGroup.add(value);
        }

        var row = new Div(icon, titleSpan, valueGroup,
                new RowActionMenu(title).addAction("Edit", edit));
        row.addClassName("rate-row");
        return row;
    }

    /** A regular label plus a semibold amount; {@code label} is null when there is none. */
    private static Component value(String label, String amount) {
        var span = new Span();
        span.addClassName("rate-row-value");
        if (label != null) {
            span.add(new Span(label + " "));
        }
        var amountSpan = new Span(amount);
        amountSpan.addClassName("rate-row-amount");
        span.add(amountSpan);
        return span;
    }

    // -------------------------------------------------- foreign per-diems

    /**
     * The foreign per-diem list: section header, search field, grid. Built once —
     * only its rows change with the year — so the search field keeps its term and
     * its value-change listener is registered exactly once.
     */
    private void buildForeignSection() {
        var add = new Button("Add", LucideIcon.PLUS.create(),
                event -> openAddCountryEditor(currentYear()));
        add.addThemeVariants(ButtonVariant.TERTIARY);

        // "Add", not "Add country": the section heading beside it carries the noun.
        var sectionHeading = new H3("Foreign Per Diem");
        sectionHeading.addClassName("section-title");

        var header = new HorizontalLayout(sectionHeading, add);
        header.addClassName("foreign-section-header");
        header.setWidthFull();
        header.setPadding(false);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        countrySearch.addClassName("foreign-search");
        countrySearch.setWidth(SEARCH_WIDTH);
        countrySearch.setPlaceholder("Search");
        countrySearch.setAriaLabel("Search countries");
        countrySearch.setClearButtonVisible(true);
        countrySearch.setValueChangeMode(ValueChangeMode.LAZY);
        // The filter sits OUTSIDE the grid, so it goes through the data view
        // rather than a Grid API: one filter reading the field, refreshed on
        // change. Adding a filter per keystroke would stack them.
        countrySearch.addValueChangeListener(event -> {
            if (foreignView != null) {
                foreignView.refreshAll();
            }
        });

        foreignGrid.addColumn(ForeignPerDiemDto::country)
                .setHeader("Country").setAutoWidth(true).setFlexGrow(1);
        foreignGrid.addColumn(dto -> formatMoney(dto.amount()))
                .setHeader("Per-diem").setAutoWidth(true).setFlexGrow(0);
        foreignGrid.addComponentColumn(dto -> new RowActionMenu(dto.country())
                        .addAction("Edit", () -> openForeignEditor(currentYear(), dto)))
                .setHeader("Actions").setAutoWidth(true).setFlexGrow(0);
        // The theme-agnostic constant, not LUMO_ROW_STRIPES — a Lumo-only variant
        // is accepted and silently ignored under Aura (F-013, F-017).
        foreignGrid.addThemeVariants(GridVariant.ROW_STRIPES);
        foreignGrid.setHeight(GRID_HEIGHT);

        foreignSection.add(header, countrySearch, foreignGrid);
        foreignSection.setPadding(false);
        foreignSection.setSpacing("var(--vaadin-gap-m)");
        foreignSection.setWidthFull();
        foreignSection.setVisible(false);
    }

    /** True when {@code dto} matches the current search term (country, contains). */
    private boolean matchesSearch(ForeignPerDiemDto dto) {
        var term = countrySearch.getValue();
        return term == null || term.isBlank()
                || dto.country().toLowerCase().contains(term.strip().toLowerCase());
    }

    // -------------------------------------------------------------- editors

    private void openAddYearEditor() {
        var model = new IntField();
        var yearField = new IntegerField("Year");
        yearField.setStepButtonsVisible(true);

        var binder = new Binder<IntField>();
        binder.forField(yearField)
                .asRequired("Year is required")
                .withValidator(value -> value >= 2000 && value <= 2100,
                        "Enter a year between 2000 and 2100")
                .bind(IntField::getValue, IntField::setValue);
        binder.readBean(model);

        new EditorDialog<>("Add Year", yearField, binder, model)
                .onSave(() -> {
                    service.addYear(model.getValue());
                    refreshYears(model.getValue());
                })
                .open();
    }

    /**
     * Source year and target year, both required. The source is a
     * {@link ComboBox} over the years that exist, so the commonest way to get this
     * wrong cannot be typed; the service still rejects a missing source, because
     * the rule belongs there and not in one form.
     */
    private void openCopyYearEditor() {
        var model = new CopyYearForm();
        var source = new ComboBox<Integer>("Source year");
        source.setItems(service.availableYears());
        source.setAllowCustomValue(false);
        var target = new IntegerField("Target year");
        target.setStepButtonsVisible(true);

        var binder = new Binder<CopyYearForm>();
        binder.forField(source).asRequired("Source year is required")
                .bind(CopyYearForm::getSource, CopyYearForm::setSource);
        binder.forField(target).asRequired("Target year is required")
                .withValidator(value -> value >= 2000 && value <= 2100,
                        "Enter a year between 2000 and 2100")
                .bind(CopyYearForm::getTarget, CopyYearForm::setTarget);

        model.setSource(yearSelector.getValue());
        binder.readBean(model);

        var form = new VerticalLayout(source, target);
        form.setPadding(false);
        form.setSpacing(false);

        new EditorDialog<>("Copy Year", form, binder, model)
                .onSave(() -> {
                    service.copyYear(model.getSource(), model.getTarget());
                    refreshYears(model.getTarget());
                })
                .open();
    }

    private void openDomesticEditor(int year, DomesticPerDiemDto existing) {
        var model = new DomesticForm();
        var full = new BigDecimalField("Full-day amount (€)");
        var partial = new BigDecimalField("Partial-day amount (€)");
        var fullHours = new IntegerField("Full-day hours");
        var partialHours = new IntegerField("Partial-day hours");

        var binder = new Binder<DomesticForm>();
        binder.forField(full).asRequired("Full-day amount is required")
                .withValidator(v -> v.signum() >= 0, "Amount must be zero or positive")
                .bind(DomesticForm::getFullAmount, DomesticForm::setFullAmount);
        binder.forField(partial).asRequired("Partial-day amount is required")
                .withValidator(v -> v.signum() >= 0, "Amount must be zero or positive")
                .bind(DomesticForm::getPartialAmount, DomesticForm::setPartialAmount);
        binder.forField(fullHours).asRequired("Full-day hours are required")
                .withValidator(v -> v > 0, "Hours must be positive")
                .bind(DomesticForm::getFullHours, DomesticForm::setFullHours);
        binder.forField(partialHours).asRequired("Partial-day hours are required")
                .withValidator(v -> v > 0, "Hours must be positive")
                .bind(DomesticForm::getPartialHours, DomesticForm::setPartialHours);

        model.setFullAmount(existing.fullDayAmount());
        model.setPartialAmount(existing.partialDayAmount());
        model.setFullHours(existing.fullDayMinHours());
        model.setPartialHours(existing.partialDayMinHours());
        binder.readBean(model);

        var form = new VerticalLayout(full, partial, fullHours, partialHours);
        form.setPadding(false);
        form.setSpacing(false);

        new EditorDialog<>("Edit domestic per diem — " + year, form, binder, model)
                .onSave(() -> {
                    service.updateDomesticPerDiem(year, model.getFullAmount(),
                            model.getPartialAmount(), model.getFullHours(),
                            model.getPartialHours());
                    renderYear(year);
                })
                .open();
    }

    private void openKilometreEditor(int year, BigDecimal current) {
        openDecimalEditor("Edit kilometre allowance — " + year, "Rate (€/km)", "Rate",
                current, value -> {
                    service.updateKilometreRate(year, value);
                    renderYear(year);
                });
    }

    private void openMealEditor(int year, BigDecimal current) {
        openDecimalEditor("Edit meal allowance — " + year, "Amount (€)", "Amount",
                current, value -> {
                    service.updateMealAllowance(year, value);
                    renderYear(year);
                });
    }

    private void openForeignEditor(int year, ForeignPerDiemDto existing) {
        openDecimalEditor("Edit per-diem — " + existing.country() + " " + year,
                "Amount (€)", "Amount", existing.amount(), value -> {
                    service.updateForeignPerDiem(existing.id(), value);
                    renderYear(year);
                });
    }

    private void openAddCountryEditor(int year) {
        var model = new ForeignForm();
        var country = new TextField("Country");
        var amount = new BigDecimalField("Amount (€)");

        var binder = new Binder<ForeignForm>();
        binder.forField(country).asRequired("Country is required")
                .withValidator(v -> !v.isBlank(), "Country is required")
                .bind(ForeignForm::getCountry, ForeignForm::setCountry);
        binder.forField(amount).asRequired("Amount is required")
                .withValidator(v -> v.signum() >= 0, "Amount must be zero or positive")
                .bind(ForeignForm::getAmount, ForeignForm::setAmount);
        binder.readBean(model);

        var form = new VerticalLayout(country, amount);
        form.setPadding(false);
        form.setSpacing(false);

        new EditorDialog<>("Add country — " + year, form, binder, model)
                .onSave(() -> {
                    service.addForeignPerDiem(year, model.getCountry(), model.getAmount());
                    renderYear(year);
                })
                .open();
    }

    // --------------------------------------------------- mutable form beans

    /** Single-value {@code int} binding model (Binder needs a bean with a setter). */
    private static final class IntField {
        private Integer value;

        Integer getValue() {
            return value;
        }

        void setValue(Integer value) {
            this.value = value;
        }
    }

    private static final class CopyYearForm {
        private Integer source;
        private Integer target;

        Integer getSource() {
            return source;
        }

        void setSource(Integer source) {
            this.source = source;
        }

        Integer getTarget() {
            return target;
        }

        void setTarget(Integer target) {
            this.target = target;
        }
    }

    private static final class DomesticForm {
        private BigDecimal fullAmount;
        private BigDecimal partialAmount;
        private Integer fullHours;
        private Integer partialHours;

        BigDecimal getFullAmount() {
            return fullAmount;
        }

        void setFullAmount(BigDecimal fullAmount) {
            this.fullAmount = fullAmount;
        }

        BigDecimal getPartialAmount() {
            return partialAmount;
        }

        void setPartialAmount(BigDecimal partialAmount) {
            this.partialAmount = partialAmount;
        }

        Integer getFullHours() {
            return fullHours;
        }

        void setFullHours(Integer fullHours) {
            this.fullHours = fullHours;
        }

        Integer getPartialHours() {
            return partialHours;
        }

        void setPartialHours(Integer partialHours) {
            this.partialHours = partialHours;
        }
    }

    private static final class ForeignForm {
        private String country;
        private BigDecimal amount;

        String getCountry() {
            return country;
        }

        void setCountry(String country) {
            this.country = country;
        }

        BigDecimal getAmount() {
            return amount;
        }

        void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }
}
