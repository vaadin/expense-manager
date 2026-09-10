package com.vaadin.expensemanager.reference.ui;

import java.math.BigDecimal;
import java.util.List;

import com.vaadin.expensemanager.base.ui.EditorDialog;
import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.RolesAllowed;

import static com.vaadin.expensemanager.reference.ui.ReferenceViewSupport.formatPercent;

/**
 * ADMIN-only settings screen for VAT rates (issue #22, ADR-0018) — one of the
 * two reference-data screens (see {@link ExpenseTypeView} for expense types).
 *
 * <p>An admin can <strong>add, edit, reorder, and deactivate</strong> rates.
 * Two-layer authorization (ADR-0008): {@code @RolesAllowed("ADMIN")} gates
 * navigation (a USER can't reach the route, and the top nav hides the
 * entry that leads to it), while the real enforcement is
 * {@link ReferenceDataService}'s method security. There is no delete — the only
 * removal is <em>deactivate</em>, which flips the {@code active} flag so the row
 * is hidden from new line choices but retained for historical lines (ADR-0018);
 * the grid shows active and inactive rows so an admin can reactivate.
 *
 * <p>The heading, grid, and row-action helpers come from
 * {@link ReferenceConfigView}; the editor is a shared {@link EditorDialog}
 * (always-enabled Save + error summary, ADR-0020). This class owns only the
 * VAT-rate specifics: the single rate column and the required-rate field.
 */
@Route("vat-rates")
@PageTitle("VAT rates")
@RolesAllowed("ADMIN")
public class VatRateView extends ReferenceConfigView<VatRateDto> {

    private final transient ReferenceDataService service;

    public VatRateView(ReferenceDataService service,
            AuthenticationContext authenticationContext) {
        super("VAT rates",
                "The VAT rates expense lines are filed against. Deactivating a "
                        + "rate hides it from new lines but keeps it on existing "
                        + "ones — nothing is deleted, so past reports keep their "
                        + "original rate.",
                authenticationContext);
        this.service = service;

        grid.addColumn(dto -> formatPercent(dto.value()))
                .setHeader("Rate").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(dto -> statusLabel(dto.active()))
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actions)
                .setHeader("Actions").setAutoWidth(true).setFlexGrow(1);

        refresh();
    }

    @Override
    protected List<VatRateDto> fetchItems() {
        return service.allVatRates();
    }

    private Component actions(VatRateDto dto) {
        int index = indexOf(dto);
        String label = formatPercent(dto.value());

        var edit = iconButton(LucideIcon.PENCIL.create(), "Edit rate " + label, () -> openEditor(dto));
        var up = reorderButton(LucideIcon.ARROW_UP.create(), "Move rate " + label + " up",
                index > 0, () -> {
                    service.moveVatRate(dto.id(), -1);
                    refresh();
                });
        var down = reorderButton(LucideIcon.ARROW_DOWN.create(), "Move rate " + label + " down",
                index >= 0 && index < currentItems().size() - 1, () -> {
                    service.moveVatRate(dto.id(), 1);
                    refresh();
                });
        var toggle = activeToggle(dto.active(), "rate " + label, () -> {
            service.setVatRateActive(dto.id(), !dto.active());
            refresh();
        });
        return new HorizontalLayout(edit, up, down, toggle);
    }

    @Override
    protected void openEditor(VatRateDto existing) {
        var model = new VatRateForm();
        var valueField = new BigDecimalField("Rate (%)");

        var binder = new Binder<VatRateForm>();
        binder.forField(valueField)
                .asRequired("Rate is required")
                .withValidator(value -> value.signum() >= 0, "Rate must be zero or positive")
                .bind(VatRateForm::getValue, VatRateForm::setValue);

        if (existing != null) {
            model.setValue(existing.value());
        }
        binder.readBean(model);

        new EditorDialog<>(existing == null ? "Add VAT rate" : "Edit VAT rate",
                valueField, binder, model)
                .onSave(() -> {
                    if (existing == null) {
                        service.createVatRate(model.getValue());
                    } else {
                        service.updateVatRate(existing.id(), model.getValue());
                    }
                    refresh();
                })
                .open();
    }

    /** Mutable binding model for the editor (Binder needs setters). */
    private static final class VatRateForm {
        private BigDecimal value;

        BigDecimal getValue() {
            return value;
        }

        void setValue(BigDecimal value) {
            this.value = value;
        }
    }
}
