package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.base.ui.RowActionMenu;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.QuantityOverride;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseLineDto;
import com.vaadin.expensemanager.report.service.ReportDetailDto;
import com.vaadin.expensemanager.report.service.TravelDto;
import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.menubar.MenuBarTester;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroupLocator;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.upload.Upload;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for {@link ReportDetailView}
 * (UC-001/UC-005, ADR-0019). Runs as the seeded plain user via
 * {@link WithUserDetails}.
 *
 * <p>Covers the create path (transient working copy, date defaulting to today,
 * first save persisting a DRAFT and routing to {@code /report/{id}}), the
 * always-enabled-Save + validation-error-summary rule (no disabled submit,
 * ADR-0020), report-level editing, and the draft-only delete returning to the
 * list. Persisted state is asserted through {@link #service}.
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class ReportDetailViewUiTest extends AbstractReportViewUiTest {

    /**
     * Only to detach a just-seeded {@code Receipt}. This test class is
     * {@code @Transactional}, so one session spans a whole test, whereas each of the
     * real clicks below is its own request with its own session. That matters for
     * exactly one case: a receipt created and then destroyed in the same test.
     */
    @Autowired
    private EntityManager entityManager;

    @Test
    void newReportSaveWithDefaultsPersistsTodayDatedDraftAndRoutesToId() {
        navigate(ReportDetailView.class);

        // No field touched: the date defaults to today; first save persists.
        findButton().withText("Save").click();

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        assertThat(mine.getFirst().reportDate()).isEqualTo(LocalDate.now());
        // Still on the detail view — now the persisted /report/{id} form.
        assertThat(getCurrentView()).isInstanceOf(ReportDetailView.class);
    }

    @Test
    void missingRequiredDateShowsErrorSummaryAndPersistsNothing() {
        navigate(ReportDetailView.class);

        // The DatePicker tester refuses to *set* an invalid value (null on a
        // required field), so clear the value straight on the component to model
        // a user emptying the field, then save (finding F-021).
        $(DatePicker.class).first().clear();
        findButton().withText("Save").click();

        // Always-enabled Save + top-of-form error summary (ADR-0020).
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Report date is required");
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void reportDateCarriesABadInputErrorMessage() {
        // Issue #85: typing an unparseable date (e.g. "dsdds") makes the picker
        // invalid; without a bad-input message the error reaches the summary blank.
        // The tester can't type an unparseable value, so we assert the message that
        // prevents the blank error is configured on the field.
        navigate(ReportDetailView.class);

        var picker = (DatePicker) $(DatePicker.class).first();
        assertThat(picker.getI18n()).isNotNull();
        assertThat(picker.getI18n().getBadInputErrorMessage()).isNotBlank();
    }

    @Test
    void editingExistingReportUpdatesItsFields() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "before");
        navigate(ReportDetailView.class, id);

        findTextArea().withLabel("Additional Info").setValue("after");
        findDatePicker().withLabel("Report date").setValue(LocalDate.of(2026, 7, 20));
        findButton().withText("Save").click();

        var reloaded = service.findMine(id);
        assertThat(reloaded.additionalInformation()).isEqualTo("after");
        assertThat(reloaded.reportDate()).isEqualTo(LocalDate.of(2026, 7, 20));
    }

    @Test
    void deletingADraftRemovesItAndReturnsToTheList() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "scratch");
        navigate(ReportDetailView.class, id);

        findButton().withText("Delete").click();          // opens the confirm dialog
        findButton().withText("Delete report").click();   // confirms

        assertThat(getCurrentView()).isInstanceOf(MyReportsView.class);
        assertThat(service.listMine()).noneMatch(r -> r.id().equals(id));
    }

    @Test
    void newReportDoesNotOfferDelete() {
        navigate(ReportDetailView.class);

        // Delete is hidden until the report is a persisted DRAFT.
        assertThat(findButton().withText("Delete").exists()).isFalse();
    }

    @Test
    void addingALineViaTheDialogUpdatesLiveTotalsAndPersistsOnSave() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withAriaLabel("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        saveExpense();

        // Live total bar reflects the added line before the report is saved.
        assertThat(getCurrentView().getElement().getTextRecursively()).contains("€100.00");

        findButton().withText("Save").click();

        var reloaded = service.findMine(id);
        assertThat(reloaded.lines()).hasSize(1);
        assertThat(reloaded.lines().getFirst().amount()).isEqualByComparingTo("100.00");
        assertThat(reloaded.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void lineEditorDefaultsQuantityToOneAndShowsTheLineTotalLive() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withAriaLabel("Add expense").click();

        // Quantity starts at 1, so the line total is the unit price itself.
        assertThat(findBigDecimalField().withLabel("Quantity").getComponent().getValue())
                .isEqualByComparingTo("1");
        findBigDecimalField().withLabel("Unit price")
                .setValue(new BigDecimal("12.50"));
        assertThat(findDialog().getComponent().getElement().getTextRecursively())
                .contains("Total").contains("€12.50");

        // Raising the quantity recomputes the total before anything is saved.
        findBigDecimalField().withLabel("Quantity").setValue(new BigDecimal("3"));
        assertThat(findDialog().getComponent().getElement().getTextRecursively()).contains("€37.50");
    }

    @Test
    void lineEditorLaysItsFieldsOutAsTheDesignsTwoColumnGrid() {
        // docs/design/components/line-editor-dialog.md § Layout. Everything spans
        // both columns except unit price/quantity, which pair, and VAT rate, which
        // is drawn half width with the cell beside it deliberately empty.
        navigate(ReportDetailView.class);
        findButton().withAriaLabel("Add expense").click();

        var dialog = findDialog().getComponent();
        assertThat(dialog.getWidth()).isEqualTo("26rem");

        var form = $(FormLayout.class).single();
        // ResponsiveStep exposes no getters, only toJson() — so compare the
        // serialised steps against the two the spec prescribes.
        assertThat(form.getResponsiveSteps())
                .extracting(step -> step.toJson().toString())
                .containsExactly(
                        new FormLayout.ResponsiveStep("0", 1).toJson().toString(),
                        new FormLayout.ResponsiveStep("24rem", 2).toJson().toString());
        assertThat(form.getColumnSpacing()).isEqualTo("var(--em-section-gap)");
        assertThat(form.getRowSpacing()).isEqualTo("var(--em-card-padding)");

        assertThat(form.getColspan(comboBox("Expense type"))).isEqualTo(2);
        assertThat(form.getColspan(bigDecimalField("Unit price"))).isEqualTo(1);
        assertThat(form.getColspan(bigDecimalField("Quantity"))).isEqualTo(1);
        assertThat(form.getColspan(comboBox("VAT rate"))).isEqualTo(1);
        assertThat(form.getColspan(
                findTextArea().withLabel("Comment").getComponent())).isEqualTo(2);
        // The total row and the receipt block are grid children too, both full width.
        assertThat(form.getColspan($(HorizontalLayout.class)
                .withClassName("line-total-row").single())).isEqualTo(2);
        assertThat(form.getColspan($(Upload.class).single().getParent().orElseThrow()))
                .isEqualTo(2);
    }

    @Test
    void lineEditorUsesTheDesignsFieldTypesAndCopy() {
        // Delta items 4, 9, 10, 11 of #175, plus the component mapping: the frame
        // hides the header close button, a third footer button and a checkbox.
        navigate(ReportDetailView.class);
        findButton().withAriaLabel("Add expense").click();

        var comment = findTextArea().withLabel("Comment").getComponent();
        assertThat(comment.getMinRows()).isEqualTo(2);
        assertThat(comment.getMaxLength()).isEqualTo(500);

        assertThat(bigDecimalField("Unit price").getHelperText()).isEqualTo("Gross, each");
        assertThat(findDialog().getComponent().getElement().getTextRecursively())
                .contains("Total").doesNotContain("Line total");

        assertThat(findButton().withText("Save").inside(findDialog()).exists()).isTrue();
        assertThat(findButton().withText("Save expense").exists()).isFalse();
        assertThat(findCheckbox().inside(findDialog()).exists()).isFalse();
    }

    @Test
    void lineEditorShowsNoReceiptStatusUntilOneIsAttached() {
        // Delta item 12: the empty state carries no status text — the Upload control
        // already says it, and the design draws none.
        navigate(ReportDetailView.class);
        findButton().withAriaLabel("Add expense").click();

        assertThat(findDialog().getComponent().getElement().getTextRecursively())
                .doesNotContain("No receipt attached");
        assertThat(findButton().withText("Remove").exists()).isFalse();

        findUpload().upload("taxi.jpg", "image/jpeg", jpegBytes());

        assertThat(findDialog().getComponent().getElement().getTextRecursively())
                .contains("taxi.jpg");
        assertThat(findButton().withText("Remove").exists()).isTrue();
    }

    @Test
    void lineEditorPutsTheUploadGlyphInTheDropLabelNotOnTheButton() {
        // Delta item 13, and iconography.md's Lucide-only rule (#163): the drawn
        // anatomy slots the glyph beside the drop label and leaves the button plain.
        navigate(ReportDetailView.class);
        findButton().withAriaLabel("Add expense").click();

        var upload = $(Upload.class).single();
        assertThat(((Button) upload.getUploadButton()).getText())
                .isEqualTo("Upload receipt\u2026");
        // Plain text: the label's Text node and nothing else — no icon slot.
        assertThat(upload.getUploadButton().getChildren())
                .noneMatch(SvgIcon.class::isInstance);
        assertThat(upload.getDropLabelIcon()).isInstanceOf(SvgIcon.class);
        assertThat(((SvgIcon) upload.getDropLabelIcon()).getSymbol())
                .isEqualTo(LucideIcon.UPLOAD.glyph());
    }

    @Test
    void aQuantityLinePersistsItsUnitPriceAndTotalsTheProduct() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withAriaLabel("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        findBigDecimalField().withLabel("Quantity").setValue(new BigDecimal("3"));
        saveExpense();

        // The card shows the qty × unit = gross breakdown and the live total bar
        // reflects the product, both before the report is saved.
        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("3 × €100.00 = €300.00").contains("€300.00");

        findButton().withText("Save").click();

        var line = service.findMine(id).lines().getFirst();
        assertThat(line.amount()).isEqualByComparingTo("100.00");     // unit price
        assertThat(line.quantity()).isEqualByComparingTo("3.00");
        assertThat(service.findMine(id).total()).isEqualByComparingTo("300.00");
    }

    @Test
    void aQuantityOneCardShowsNoQuantityBreakdown() {
        // The feature is invisible until used (ADR-0023).
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "100.00");
        navigate(ReportDetailView.class, id);

        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("€100.00")
                .doesNotContain("1 × €100.00");
    }

    @Test
    void aNonPositiveQuantityIsBlockedWithAnErrorSummary() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withAriaLabel("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        findBigDecimalField().withLabel("Quantity").setValue(BigDecimal.ZERO);
        // Always-enabled Save (ADR-0020): the click is allowed, the reason shows.
        saveExpense();

        assertThat(findDialog().getComponent().getElement().getTextRecursively())
                .contains("Quantity must be greater than zero");
        assertThat(service.findMine(id).lines()).isEmpty();
    }

    @Test
    void editingALineLoadsAndUpdatesItsQuantity() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "50.00");
        navigate(ReportDetailView.class, id);

        // Open the existing (quantity-1) line and give it a quantity. The card body
        // itself carries the click listener (no Edit button), so click that.
        openLineCardEditor();
        assertThat(findBigDecimalField().withLabel("Quantity").getComponent().getValue())
                .isEqualByComparingTo("1");
        findBigDecimalField().withLabel("Quantity").setValue(new BigDecimal("4"));
        saveExpense();
        findButton().withText("Save").click();

        var line = service.findMine(id).lines().getFirst();
        assertThat(line.amount()).isEqualByComparingTo("50.00");
        assertThat(line.quantity()).isEqualByComparingTo("4.00");
        assertThat(service.findMine(id).total()).isEqualByComparingTo("200.00");
    }

    @Test
    void lineEditorKeepsSaveEnabledAndBlocksAnEmptyLine() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withAriaLabel("Add expense").click();
        // Always-enabled Save (ADR-0020): clicking it with nothing filled must
        // not add a line — validation blocks it and the dialog stays open.
        saveExpense();

        // Dialog is still open (validation blocked the save) and its Save is still
        // enabled — never a disabled submit.
        assertThat(findDialog().exists()).isTrue();
        assertThat(findButton().withText("Save").inside(findDialog())
                .getComponent().isEnabled()).isTrue();
        // … and no line was added: the empty state still shows, nothing persisted.
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("No expenses yet");
        assertThat(service.findMine(id).lines()).isEmpty();
    }

    @Test
    void submittingADraftWithALineLocksItReadOnly() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);

        findButton().withText("Submit for approval").click(); // opens the confirm dialog
        findButton().withText("Submit report").click();       // confirms

        // The report is now SUBMITTED and read-only to the owner: the editing
        // affordances are gone and the status is shown as text (ADR-0020).
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Submitted");
        assertThat(findButton().withText("Save").exists()).isFalse();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();
        assertThat(findButton().withAriaLabel("Add expense").exists()).isFalse();
        assertThat(findButton().withText("Delete").exists()).isFalse();
    }

    @Test
    void submittingAnEmptyDraftShowsTheReasonAndStaysEnabled() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "empty");
        navigate(ReportDetailView.class, id);

        // Always-enabled Submit (ADR-0020): a zero-line submit is blocked with the
        // reason in the top-of-form error summary, not a silent no-op. The report
        // date is valid, so the confirm dialog opens; the domain guard fires on
        // confirm and the atomic save+submit rolls back (issue #81).
        findButton().withText("Submit for approval").click();
        findButton().withText("Submit report").click();

        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("at least one line");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
        // Submit button is still there (never disabled) so the owner can retry.
        assertThat(findButton().withText("Submit for approval").exists()).isTrue();
    }

    // --- Submit saves the current state + confirmation dialog (issue #81) ---

    @Test
    void submittingPersistsAReportLevelEditThatWasNotSavedFirst() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);

        // Edit a report-level field but never click Save — the edit lives only in
        // the working copy until the submit persists it (issue #81).
        findTextArea().withLabel("Additional Info")
                .setValue("edited but not saved");

        findButton().withText("Submit for approval").click(); // opens the confirm dialog
        findButton().withText("Submit report").click();       // confirms

        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.additionalInformation()).isEqualTo("edited but not saved");
    }

    @Test
    void submittingPersistsALineAddedButNotSavedFirst() {
        // A seed with no lines: before issue #81 a line added in the dialog but not
        // Saved was lost on submit, so the submit failed the "≥1 line" guard. Now
        // the submit saves the pending line first, so it succeeds.
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withAriaLabel("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        saveExpense();

        findButton().withText("Submit for approval").click();
        findButton().withText("Submit report").click();

        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.lines()).hasSize(1);
        assertThat(reloaded.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void cancellingTheSubmitConfirmationLeavesItAnEditableDraft() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);

        findButton().withText("Submit for approval").click(); // opens the dialog
        findButton().withText("Cancel").click();              // backs out

        // Nothing submitted: still an editable DRAFT with both actions offered.
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
        assertThat(findButton().withText("Submit for approval").exists()).isTrue();
        assertThat(findButton().withText("Save").exists()).isTrue();
    }

    @Test
    void submittingWithNoReportDateBlocksBeforeConfirming() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);

        // Clearing the required date must block the submit at validation — the
        // confirm dialog never opens and nothing transitions.
        $(DatePicker.class).first().clear();
        findButton().withText("Submit for approval").click();

        assertThat(findButton().withText("Submit report").exists()).isFalse();
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Report date is required");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
    }

    @Test
    void aRejectedReportShowsTheRealReasonRejecterAndDateToTheOwner() {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100",
                "Please attach the hotel receipt.");
        navigate(ReportDetailView.class, id);

        // The owner's callout carries the real reason, who rejected it (the seeded
        // admin), and when — replacing the placeholder note. The reason and the
        // rejecter also appear in the status-history audit trail below.
        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("Rejected — changes requested",
                "Please attach the hotel receipt.", "Expense Admin", "14 Jul 2026");
        assertThat(text).contains("Status history");
        // A rejected report is editable again (owner can resubmit), so Save shows.
        assertThat(findButton().withText("Save").exists()).isTrue();
    }

    // --- Resubmit a rejected report (Phase 5.5) ---

    @Test
    void aRejectedReportOffersResubmitNotSubmit() {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Fix the total.");
        navigate(ReportDetailView.class, id);

        // The forward action is relabelled "Resubmit" on a REJECTED report; the
        // first-submit label is gone (same button, flipped by status).
        assertThat(findButton().withText("Resubmit").exists()).isTrue();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();
    }

    @Test
    void resubmitIsNotOfferedOnDraftSubmittedOrApproved() {
        // DRAFT offers the first-submit label, never "Resubmit".
        var draft = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, draft);
        assertThat(findButton().withText("Submit for approval").exists()).isTrue();
        assertThat(findButton().withText("Resubmit").exists()).isFalse();

        // SUBMITTED is read-only — no forward action of either label.
        var submitted = seedSubmittedReport(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, submitted);
        assertThat(findButton().withText("Resubmit").exists()).isFalse();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();

        // APPROVED is terminal and read-only — likewise no forward action.
        var approved = seedApprovedReport(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, approved);
        assertThat(findButton().withText("Resubmit").exists()).isFalse();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();
    }

    @Test
    void resubmittingARejectedReportSendsItBackToSubmittedAndLocksItReadOnly() {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Fix the total.");
        navigate(ReportDetailView.class, id);

        findButton().withText("Resubmit").click();         // opens the confirm dialog
        findButton().withText("Resubmit report").click();  // confirms

        // Back through the queue: REJECTED → SUBMITTED, and read-only to the owner
        // again (the editing affordances and the forward action are gone).
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Submitted");
        assertThat(findButton().withText("Resubmit").exists()).isFalse();
        assertThat(findButton().withText("Save").exists()).isFalse();
        assertThat(findButton().withAriaLabel("Add expense").exists()).isFalse();
    }

    @Test
    void resubmittingPersistsAnEditThatWasNotSavedFirst() {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Add detail.");
        navigate(ReportDetailView.class, id);

        // Address the feedback but don't click Save — the resubmit must save it
        // as part of the transition (issue #81).
        findTextArea().withLabel("Additional Info")
                .setValue("addressed the feedback");

        findButton().withText("Resubmit").click();
        findButton().withText("Resubmit report").click();

        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.additionalInformation()).isEqualTo("addressed the feedback");
    }

    @Test
    void resubmittingAnEmptyRejectedReportShowsTheReasonAndStaysEnabled() {
        var id = seedEmptyRejectedReport(LocalDate.of(2026, 7, 1), "Fix the total.");
        navigate(ReportDetailView.class, id);

        // Always-enabled Resubmit (ADR-0020): a zero-line resubmit is blocked with
        // the reason in the top-of-form error summary, not a silent no-op. The
        // confirm dialog opens (the date is valid); the domain guard fires on
        // confirm and the atomic save+resubmit rolls back (issue #81).
        findButton().withText("Resubmit").click();
        findButton().withText("Resubmit report").click();

        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("at least one line");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.REJECTED);
        // The button is still there (never disabled) so the owner can add a line and retry.
        assertThat(findButton().withText("Resubmit").exists()).isTrue();
    }

    @Test
    void anAlreadySubmittedReportOpensReadOnly() {
        var id = seedSubmittedReport(LocalDate.of(2026, 7, 1), "80.00");
        navigate(ReportDetailView.class, id);

        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Submitted");
        assertThat(findButton().withText("Save").exists()).isFalse();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();
        assertThat(findButton().withText("Delete").exists()).isFalse();
        assertThat(findButton().withAriaLabel("Add expense").exists()).isFalse();
        // The line is shown but carries no ⋮ menu at all — not a disabled one.
        assertThat($(RowActionMenu.class).all()).isEmpty();
    }

    @Test
    void uploadingAReceiptOnAnUnsavedReportPersistsItOnFirstSave() {
        // Brand-new, never-saved report: the receipt attaches at any time, no
        // "save first" gate (ADR-0021 overrides ADR-0019's save-before-attach).
        navigate(ReportDetailView.class);

        findButton().withAriaLabel("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        // Drive the real UploadHandler: bytes are validated server-side by magic
        // bytes and buffered in memory (the browser mime is not trusted).
        findUpload().upload("taxi.jpg", "image/jpeg", jpegBytes());
        saveExpense();

        // The card reflects the buffered receipt before the report is even saved —
        // as an image thumbnail (its filename is the accessible name), matching how
        // a persisted receipt renders (issue #89).
        assertThat(findButton().withAriaLabel("Preview receipt: taxi.jpg").exists())
                .isTrue();

        findButton().withText("Save").click();

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        var line = service.findMine(mine.getFirst().id()).lines().getFirst();
        assertThat(line.hasReceipt()).isTrue();
        assertThat(line.receiptFilename()).isEqualTo("taxi.jpg");
        assertThat(line.receiptContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void aMislabeledUploadIsRejectedAndNothingIsBuffered() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withAriaLabel("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        // A text file renamed to .jpg: magic-byte check rejects it at upload; the
        // control is never disabled, the reason is surfaced instead (ADR-0020).
        findUpload().upload("fake.jpg", "image/jpeg", "not an image".getBytes());
        saveExpense();
        findButton().withText("Save").click();

        // The line saved, but with no receipt — the rejected bytes were dropped.
        assertThat(service.findMine(id).lines().getFirst().hasReceipt()).isFalse();
    }

    @Test
    void aSubmittedReportShowsTheReceiptSummaryButOffersNoUpload() {
        var id = seedSubmittedReportWithReceipt(LocalDate.of(2026, 7, 1), "50.00",
                "hotel.jpg");
        navigate(ReportDetailView.class, id);

        // The receipt is viewable (read path, ADR-0021): its image preview shows
        // even on a read-only report. But every mutation surface is gone — no Add
        // expense to open the editor, so the receipt cannot be changed.
        assertThat(findButton().withAriaLabel("Preview receipt: hotel.jpg").exists())
                .isTrue();
        assertThat(findButton().withAriaLabel("Add expense").exists()).isFalse();
        assertThat($(RowActionMenu.class).all()).isEmpty();
    }

    @Test
    void aSavedImageReceiptRendersAnEnlargeablePreview() {
        // A persisted image receipt: the card shows a keyboard-operable thumbnail
        // button (accessible name) that streams from the DB and enlarges in a
        // dialog (ADR-0021 / ADR-0020) — visible even on a read-only report.
        var id = seedSubmittedReportWithReceipt(LocalDate.of(2026, 7, 1), "50.00",
                "hotel.jpg");
        navigate(ReportDetailView.class, id);

        assertThat(findButton().withAriaLabel("Preview receipt: hotel.jpg").exists())
                .isTrue();
        assertThat($(Image.class).exists()).isTrue();
    }

    @Test
    void aSavedPdfReceiptOffersAnOpenAffordance() {
        var id = seedReportWithReceipt(LocalDate.of(2026, 7, 1), "70.00",
                "invoice.pdf", pdfBytes());
        navigate(ReportDetailView.class, id);

        // A PDF is not thumbnailed. On a row it is the design's chip — a paperclip
        // and the filename — and activating it still opens the browser's PDF viewer
        // in a new tab (ADR-0021); the "open" verb moved to the accessible name.
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("invoice.pdf");
        assertThat($(Anchor.class).withClassName("expense-row-attachment")
                .single().getElement().getAttribute("aria-label"))
                .isEqualTo("Open receipt: invoice.pdf");
    }

    @Test
    void anUnsavedImageAttachmentPreviewsFromBufferedBytes() {
        // Brand-new report, receipt attached but nothing saved yet: the editor
        // previews the buffered bytes directly (no DB round-trip, no receipt id).
        navigate(ReportDetailView.class);

        findButton().withAriaLabel("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        findUpload().upload("taxi.jpg", "image/jpeg", jpegBytes());

        // The preview affordance appears in the still-open dialog, before any save.
        assertThat(findButton().withAriaLabel("Preview receipt: taxi.jpg").exists())
                .isTrue();
    }

    @Test
    void anUnsavedImageAttachmentPreviewsOnTheCardBeforeSave() {
        // Issue #89: after the line editor closes, the card must show the image
        // thumbnail from the buffered bytes right away — not only after the report
        // is saved (previously the card showed just a "📎 filename" chip until the
        // receipt was persisted and had an id).
        navigate(ReportDetailView.class);

        findButton().withAriaLabel("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        findUpload().upload("taxi.jpg", "image/jpeg", jpegBytes());
        saveExpense();

        // Dialog is closed; the preview button now belongs to the line card, and it
        // renders before the report has ever been saved (no receipt id yet).
        assertThat(findDialog().exists()).isFalse();
        assertThat(findButton().withAriaLabel("Preview receipt: taxi.jpg").exists())
                .isTrue();
        assertThat($(Image.class).exists()).isTrue();
    }

    // --- Travel Calculator / domestic per-diem (Phase 4.2/4.3) ---

    private static final LocalDateTime DEP = LocalDateTime.of(2026, 7, 1, 8, 0);

    /**
     * The purpose every seeded trip carries, and therefore the accessible name of
     * its ⋮ trigger — "Actions for Client visit". A row menu is addressed by the row
     * it names (row-action-menu.md), which is what makes fourteen of them on one
     * page distinguishable.
     */
    private static final String TRIP = "Client visit";

    @Test
    void insertingADomesticTripPreviewsGeneratesAndPersistsThePerDiem() {
        navigate(ReportDetailView.class);

        findButton().withAriaLabel("Add travel").click();
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(11));
        findComboBox(String.class).withLabel("Destination country")
                .selectItem("Finland (domestic)");
        findTextField().withLabel("Destinations").setValue("Helsinki");
        findTextField().withLabel("Travel purpose").setValue("Client visit");

        // The per-diem previews live as the dates are filled — no separate compute
        // step (11 h → one full day €54). The preview lives in the dialog overlay
        // (attached to the UI, not the view), so assert against the whole UI tree.
        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("Per diem allowance (full day)", "€54.00", "Trip total");

        saveTrip();

        // The trip card + the live "Per diem allowance" subtotal row appear before
        // the report is even saved.
        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("Client visit", "Helsinki, Finland",
                "Per diem allowance", "€54.00");

        findButton().withText("Save").click();

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        var loaded = service.findMine(mine.getFirst().id());
        assertThat(loaded.travels()).hasSize(1);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(loaded.total()).isEqualByComparingTo("54.00");
    }

    @Test
    void insertingAForeignTripCostsThePerDiemAgainstTheDestinationCountry() {
        navigate(ReportDetailView.class);

        findButton().withAriaLabel("Add travel").click();
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(11));
        // The picker lists the seeded foreign countries alongside Finland; pick one.
        var country = findComboBox(String.class).withLabel("Destination country");
        assertThat(country.getSuggestions())
                .contains("Finland (domestic)", "Germany", "Sweden");
        country.selectItem("Germany");
        findTextField().withLabel("Destinations").setValue("Berlin");
        findTextField().withLabel("Travel purpose").setValue("Conference");

        // Costed against Germany's €71.00/day, not the Finnish €54.00.
        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("Per diem allowance (full day)", "€71.00", "Germany");

        saveTrip();
        // The chosen country shows on the Trip & Allowance card ("destinations, country").
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Berlin, Germany", "€71.00");

        findButton().withText("Save").click();

        var loaded = service.findMine(service.listMine().getFirst().id());
        assertThat(loaded.travels().getFirst().country()).isEqualTo("Germany");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("71.00");
    }

    @Test
    void aTripCannotBeSavedWithNoDestinationCountryChosen() {
        navigate(ReportDetailView.class);

        findButton().withAriaLabel("Add travel").click();
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(11));
        findTextField().withLabel("Destinations").setValue("Helsinki");
        findTextField().withLabel("Travel purpose").setValue("Client visit");
        // Country left unchosen: Save surfaces a clear message and generates nothing.
        saveTrip();

        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("Destination country is required");
        assertThat(findButton().withText("Save").inside(findDialog()).exists()).isTrue();
        assertThat(findSpan().withText("Per diem allowance").exists()).isFalse();
    }

    @Test
    void insertingATripWithKmMealParkingShowsEachSubtotalAndFoldsParkingIntoNetVat() {
        navigate(ReportDetailView.class);

        findButton().withAriaLabel("Add travel").click();
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(11));
        findComboBox(String.class).withLabel("Destination country")
                .selectItem("Finland (domestic)");
        findTextField().withLabel("Destinations").setValue("Helsinki");
        findTextField().withLabel("Travel purpose").setValue("Client visit");
        findBigDecimalField().withLabel("Kilometre allowance (km)")
                .setValue(new BigDecimal("120"));
        // A meal allowance is only paid when no per-diem applies (issue #93), so
        // clicking it marks the trip not eligible — the per-diem line drops out and
        // the meal-allowance line takes its place.
        findCheckbox().withLabel("Pay meal allowance?").click();
        findBigDecimalField().withLabel("Parking fees (€)")
                .setValue(new BigDecimal("12.00"));

        // The remaining outputs preview live in the dialog (120 km × €0.55 = €66.00,
        // etc.); no per-diem, since the trip is now not eligible for one.
        var previewText = UI.getCurrent().getElement().getTextRecursively();
        assertThat(previewText).contains("Kilometre allowance", "€66.00",
                "Meal allowance", "€13.50", "Parking", "€12.00");
        assertThat(previewText).doesNotContain("Per diem allowance");

        saveTrip();

        // The two new tax-free subtotal rows are visible; parking is not a subtotal.
        assertThat(findSpan().withText("Kilometre allowance").exists()).isTrue();
        assertThat(findSpan().withText("Meal allowance").exists()).isTrue();
        assertThat(findSpan().withText("Per diem allowance").exists()).isFalse();
        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("€66.00", "€13.50");

        findButton().withText("Save").click();

        var loaded = service.findMine(service.listMine().getFirst().id());
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("0.00");
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("66.00");
        assertThat(loaded.mealTotal()).isEqualByComparingTo("13.50");
        // Parking folds into Net/VAT; grand total sums it all.
        assertThat(loaded.netTotal()).isEqualByComparingTo("9.56");
        assertThat(loaded.total()).isEqualByComparingTo("91.50");
    }

    @Test
    void theKilometreLineCardShowsTheKmTimesRateBreakdown() {
        // 12.5 km × the seeded 2026 rate (€0.550/km) = €6.88 — the km line reads like
        // a multi-unit manual card (ADR-0023), and the euros are unchanged.
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("12.5"), false, BigDecimal.ZERO);
        navigate(ReportDetailView.class, id);

        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("12.5 × €0.55 = €6.88");
        // The flat per-diem line on the same trip carries no breakdown (quantity 1).
        assertThat(shown).contains("€54.00").doesNotContain("1 × €54.00");

        // Totals are untouched: the km subtotal is the product, the grand total sums.
        var loaded = service.findMine(id);
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("6.88");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(loaded.total()).isEqualByComparingTo("60.88");
        assertThat(loaded.netTotal()).isEqualByComparingTo("0.00");
        assertThat(loaded.vatTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void reCostingATripUpdatesItsKilometreCardWithoutLeavingAStaleOne() {
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("12.5"), false, BigDecimal.ZERO);
        navigate(ReportDetailView.class, id);

        // Re-cost the trip through its editor: 12.5 km → 120 km.
        clickRowAction(TRIP, "Edit");
        findBigDecimalField().withLabel("Kilometre allowance (km)")
                .setValue(new BigDecimal("120"));
        saveTrip();

        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("120 × €0.55 = €66.00")
                .doesNotContain("12.5 × €0.55");

        findButton().withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("66.00");
        assertThat(loaded.travels().getFirst().generatedLines()).hasSize(2);
    }

    @Test
    void aTripWithoutKmOrMealHidesThoseSubtotalRows() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        navigate(ReportDetailView.class, id);

        // Per-diem-only trip: its row shows, but the km/meal rows stay hidden.
        assertThat(findSpan().withText("Per diem allowance").exists()).isTrue();
        assertThat(findSpan().withText("Kilometre allowance").exists()).isFalse();
        assertThat(findSpan().withText("Meal allowance").exists()).isFalse();
    }

    @Test
    void aTripListsItsGeneratedLinesAndAReceiptCanBeAttachedToOne() {
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00"));
        navigate(ReportDetailView.class, id);

        // Each earned line is listed under the trip with an attach affordance.
        assertThat(rowActions("Per diem allowance (full day)"))
                .contains("Add receipt");
        assertThat(rowActions("Parking")).contains("Add receipt");

        // Attach a receipt to the parking line via its focused editor.
        clickRowAction("Parking", "Add receipt");
        findUpload().upload("parking.jpg", "image/jpeg", jpegBytes());
        findButton().withText("Save receipt").click();

        // Issue #89: the buffered receipt previews as a thumbnail on the row right
        // away — before the report is even saved — not just as a filename chip.
        assertThat(findButton().withAriaLabel("Preview receipt: parking.jpg").exists())
                .isTrue();
        assertThat($(Image.class).exists()).isTrue();

        findButton().withText("Save").click();

        var parking = service.findMine(id).travels().getFirst()
                .generatedLine(com.vaadin.expensemanager.report.domain.GeneratedLineKind
                        .PARKING).orElseThrow();
        assertThat(parking.hasReceipt()).isTrue();
        assertThat(parking.receiptFilename()).isEqualTo("parking.jpg");
        assertThat(parking.receiptId()).isNotNull();
    }

    @Test
    void editingATripWithFreeLunchRegeneratesAHalvedPerDiem() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        navigate(ReportDetailView.class, id);

        clickRowAction(TRIP, "Edit");                    // the trip row's menu
        dailyAllowance().selectItem("Halved, free lunch provided");
        saveTrip();

        // Live per-diem halves to €27.00; a save regenerates the line.
        assertThat(getCurrentView().getElement().getTextRecursively()).contains("€27.00");
        findButton().withText("Save").click();

        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("27.00");
    }

    @Test
    void aTripEarningAPartialDayListsTwoPerDiemCardsUnderOneSubtotal() {
        // Issue #124: 31 h → a full-day card (1 × €54.00) and a partial-day card
        // (1 × €25.00), each labelled by the day it prices, both folded into the single
        // "Per diem allowance" subtotal row (€79.00).
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(31));
        navigate(ReportDetailView.class, id);

        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("Per diem allowance (full day)", "€54.00",
                "Per diem allowance (partial day)", "€25.00");
        // One subtotal row for both lines, and it is the sum.
        assertThat(findSpan().withText("Per diem allowance").exists()).isTrue();
        assertThat(shown).contains("€79.00");

        var loaded = service.findMine(id);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("79.00");
        assertThat(loaded.total()).isEqualByComparingTo("79.00");
        assertThat(loaded.travels().getFirst().generatedLines()).hasSize(2);
    }

    @Test
    void aMultiDayPerDiemCardShowsTheDaysTimesRateBreakdown() {
        // 55 h → 2 full days + 1 partial: the full-day card reads like an invoice line
        // (ADR-0023), the partial-day card stays a plain quantity-1 card.
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);

        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("2 × €54.00 = €108.00")
                .doesNotContain("1 × €25.00");
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void reCostingATripDropsThePerDiemCardItNoLongerEarns() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(31));
        navigate(ReportDetailView.class, id);
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Per diem allowance (partial day)");

        // Shorten the trip to a whole 24 h through its editor: the partial-day card
        // disappears, the full-day one stays, and the subtotal follows live.
        clickRowAction(TRIP, "Edit");
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(24));
        saveTrip();

        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("Per diem allowance (full day)")
                .doesNotContain("Per diem allowance (partial day)");

        findButton().withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(loaded.travels().getFirst().generatedLines()).hasSize(1);
    }

    // --- Quantity Override on a generated line (ADR-0024, issue #131) ---
    //
    // The user corrects a travel-generated allowance line by changing its COUNT,
    // with a mandatory reason; the unit price stays statutory and server-computed.

    @Test
    void overridingTheFullDayCountRescalesTheLineTheSubtotalAndTheTotal() {
        // 55 h → 2 full days (€108.00) + 1 partial day (€25.00) = €133.00.
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("€133.00");

        overrideCount("Per diem allowance (full day)", 1, "the Wednesday was personal");

        // The row now reads the effective figures, badges the correction, gives the
        // reason and names what the calculator produced — before any save.
        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("Overridden",
                "Reason: the Wednesday was personal",
                "Calculated: 2 × €54.00 = €108.00");
        // 1 full day + the untouched partial day, live in the subtotal and total.
        assertThat(shown).contains("€79.00");

        findButton().withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("79.00");
        assertThat(loaded.total()).isEqualByComparingTo("79.00");
        // The unit price is the law — only the count moved.
        var full = loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow();
        assertThat(full.unitPrice()).isEqualByComparingTo("54.00");
        assertThat(full.quantity()).isEqualByComparingTo("1.00");
    }

    @Test
    void anOverrideSurvivesSavingAndReloadingWithItsReason() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);
        overrideCount("Per diem allowance (full day)", 1, "the Wednesday was personal");
        findButton().withText("Save").click();

        // Reload the persisted report from scratch: the badge, the reason and the
        // statutory baseline are all still there.
        navigate(ReportDetailView.class, id);

        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("Overridden",
                "Reason: the Wednesday was personal",
                "Calculated: 2 × €54.00 = €108.00", "€79.00");
    }

    @Test
    void theMealAllowanceCountIsOverridableToo() {
        // A not-eligible trip paying a meal allowance: one flat €13.50 line.
        var id = seedReportWithMealTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        navigate(ReportDetailView.class, id);

        overrideCount("Meal allowance", 2, "two meals were taken");
        findButton().withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.mealTotal()).isEqualByComparingTo("27.00");
        assertThat(loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.MEAL).orElseThrow()
                .overrideReason()).isEqualTo("two meals were taken");
    }

    @Test
    void aBlankReasonIsRejectedInTheDialogsErrorSummaryAndChangesNothing() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);

        clickRowAction("Per diem allowance (full day)", "Override");
        findIntegerField().withLabel("Count").setValue(1);
        findTextArea().withLabel("Reason for the override").setValue("   ");
        // Always-enabled submit (ADR-0020): the click is allowed, the reason shows.
        findButton().withText("Save override").click();

        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("A reason for the override is required.");
        // The dialog stays open and nothing was corrected.
        assertThat(findButton().withText("Save override").exists()).isTrue();
        assertThat(findSpan().withText("Overridden").exists()).isFalse();
    }

    @Test
    void thePartialDayCountIsCappedAtOneWithTheReasonInTheErrorSummary() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);

        clickRowAction("Per diem allowance (partial day)", "Override");
        findIntegerField().withLabel("Count").setValue(2);
        findTextArea().withLabel("Reason for the override").setValue("two leftovers");
        findButton().withText("Save override").click();

        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("one partial day");
        assertThat(findButton().withText("Save override").exists()).isTrue();
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void theCountFieldTakesWholeNumbersAtOrAboveTheFloor() {
        // Integrality is enforced at the widget (an IntegerField) and the floor with
        // it; the domain enforces both again on save. The tester refuses to set an
        // out-of-range value, so assert the constraint that makes that so (F-021).
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);

        clickRowAction("Per diem allowance (full day)", "Override");

        var count = (IntegerField) findIntegerField().withLabel("Count").getComponent();
        assertThat(count.getMin()).isEqualTo(0);
    }

    @Test
    void kilometreAndParkingLinesOfferNoOverride() {
        // Their numbers are trip inputs with a single home — edited on the trip.
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), false,
                new BigDecimal("12.00"));
        navigate(ReportDetailView.class, id);

        assertThat(rowActions("Per diem allowance (full day)")).contains("Override");
        assertThat(rowActions("Kilometre allowance")).doesNotContain("Override");
        assertThat(rowActions("Parking")).doesNotContain("Override");
    }

    @Test
    void resetToCalculatedRemovesTheOverrideWithoutTouchingTheTrip() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);
        overrideCount("Per diem allowance (full day)", 1, "the Wednesday was personal");
        findButton().withText("Save").click();

        clickRowAction("Per diem allowance (full day)", "Reset to calculated");

        // Back to the statutory figure, live, and the badge is gone.
        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("€133.00").doesNotContain("Overridden");

        findButton().withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("133.00");
        assertThat(loaded.travels().getFirst().quantityOverrides()).isEmpty();
        // The trip itself was never edited.
        assertThat(loaded.travels().getFirst().returnAt()).isEqualTo(DEP.plusHours(55));
    }

    @Test
    void aSubmittedReportShowsAnOverrideReadOnlyWithNoWayToChangeIt() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);
        overrideCount("Per diem allowance (full day)", 1, "the Wednesday was personal");
        findButton().withText("Save").click();
        service.submit(id, service.findMine(id).version());

        navigate(ReportDetailView.class, id);

        // Editability falls out of the existing DRAFT/REJECTED gating — the badge,
        // reason and baseline still read, but every mutation surface is gone.
        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("Overridden", "Reason: the Wednesday was personal",
                "Calculated: 2 × €54.00 = €108.00");
        // A read-only report builds no menu at all, rather than a disabled trigger.
        assertThat(rowActions("Per diem allowance (full day)")).isEmpty();
    }

    @Test
    void aRejectedReportCanStillBeCorrectedWithAnOverride() {
        // The owner acts on an approver's feedback: REJECTED is editable again.
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100",
                "The Wednesday looks personal.");
        var loaded = service.findMine(id);
        service.update(id, new ReportDetailDto(id, loaded.reportDate(),
                loaded.additionalInformation(), loaded.status(), loaded.version(),
                loaded.lines(), List.of(TravelDto.domestic(null, DEP,
                        DEP.plusHours(55), "Helsinki", "Client visit", false, false,
                        false, BigDecimal.ZERO.setScale(2), false,
                        BigDecimal.ZERO.setScale(2))),
                loaded.total(), loaded.netTotal(), loaded.vatTotal(),
                loaded.perDiemTotal(), loaded.kilometreTotal(), loaded.mealTotal()),
                loaded.version());
        navigate(ReportDetailView.class, id);

        overrideCount("Per diem allowance (full day)", 1, "the Wednesday was personal");
        findButton().withText("Save").click();

        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("79.00");
    }

    /**
     * Drives the whole override flow for one generated line: opens the row's
     * override dialog, enters a count and a reason, and confirms.
     */
    private void overrideCount(String lineLabel, int count, String reason) {
        clickRowAction(lineLabel, "Override");
        findIntegerField().withLabel("Count").setValue(count);
        findTextArea().withLabel("Reason for the override").setValue(reason);
        findButton().withText("Save override").click();
    }

    // --- Zero suppresses the line, with the receipt-destruction confirm (issue #132) ---
    //
    // A count of 0 drops the line — the only way to say "keep the full days, lose the
    // partial leftover". Because a dropped line takes its receipt with it (a DB-level
    // cascade, ADR-0024), the user is warned first and the file is named.

    @Test
    void aZeroCountRemovesTheLineWithoutPromptingWhenItCarriesNoReceipt() {
        // 55 h → 2 full days (€108.00) + 1 partial day (€25.00) = €133.00.
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);

        overrideCount("Per diem allowance (partial day)", 0, "the leftover was personal");

        // Nothing to destroy, so nothing to confirm — the correction applied at once.
        assertThat(findButton().withText("Remove line and receipt").exists()).isFalse();
        var shown = getCurrentView().getElement().getTextRecursively();
        // The line is gone from the report; the full days are untouched and the
        // per-diem subtotal and total drop to just them.
        assertThat(shown).contains("€108.00").doesNotContain("€133.00", "1 × €25.00");
        // What is left in its place says what was dropped and why, and offers the way
        // back — without which a zero override would be unreachable once made.
        assertThat(shown).contains("Removed",
                "Removed from the report. Reason: the leftover was personal");
        assertThat(rowActions("Per diem allowance (partial day)"))
                .containsExactly("Reset to calculated");

        findButton().withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL)).isEmpty();
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("108.00");
        assertThat(loaded.total()).isEqualByComparingTo("108.00");
    }

    @Test
    void suppressingALineCarryingAReceiptConfirmsFirstAndNamesTheFile() {
        var id = seedMealTravelWithSavedReceipt("lunch.jpg");
        navigate(ReportDetailView.class, id);

        overrideCount("Meal allowance", 0, "no meal was taken after all");

        // Nothing has happened yet: the dialog names the line, names the file, and says
        // the receipt goes with it.
        var prompt = UI.getCurrent().getElement().getTextRecursively();
        assertThat(prompt).contains("removes the Meal allowance line", "lunch.jpg",
                "deleted with the line and cannot be recovered");
        assertThat(service.findMine(id).mealTotal()).isEqualByComparingTo("13.50");

        findButton().withText("Remove line and receipt").click();
        findButton().withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.travels().getFirst().generatedLine(GeneratedLineKind.MEAL))
                .isEmpty();
        assertThat(loaded.mealTotal()).isEqualByComparingTo("0.00");
        assertThat(loaded.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void cancellingTheReceiptWarningLeavesBothTheLineAndItsReceiptAlone() {
        var id = seedMealTravelWithSavedReceipt("lunch.jpg");
        navigate(ReportDetailView.class, id);

        overrideCount("Meal allowance", 0, "no meal was taken after all");
        findButton().withText("Cancel").click();

        // The line still stands at its statutory figure, with its receipt attached and
        // no override recorded — cancelling is a full retreat, not a half-applied one.
        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("Meal allowance", "€13.50").doesNotContain("Removed");
        assertThat(rowActions("Meal allowance")).contains("Edit receipt");

        var stillThere = service.findMine(id).travels().getFirst()
                .generatedLine(GeneratedLineKind.MEAL).orElseThrow();
        assertThat(stillThere.amount()).isEqualByComparingTo("13.50");
        assertThat(stillThere.hasReceipt()).isTrue();
        assertThat(stillThere.receiptFilename()).isEqualTo("lunch.jpg");
        assertThat(service.findMine(id).travels().getFirst().quantityOverrides())
                .isEmpty();
    }

    @Test
    void suppressingALineDropsItsBufferedReceiptSoNoStaleReferenceIsSaved() {
        // The receipt is attached but the report not yet saved (ADR-0021 buffering), so
        // the doomed file lives only in the view's pending map. Suppressing the line
        // must prune it: on save the service would otherwise be handed a
        // GeneratedLineRef for a kind the trip no longer generates.
        var id = seedReportWithMealTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        navigate(ReportDetailView.class, id);
        clickRowAction("Meal allowance", "Add receipt");
        findUpload().upload("lunch.jpg", "image/jpeg", jpegBytes());
        findButton().withText("Save receipt").click();

        overrideCount("Meal allowance", 0, "no meal was taken");
        // A buffered receipt is just as destructible as a saved one — same warning.
        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("lunch.jpg");
        findButton().withText("Remove line and receipt").click();

        findButton().withText("Save").click();

        // The save went through (a stale ref would have failed it) and left nothing
        // behind: no line, no receipt, no error.
        assertThat(findSpan().withText("Something went wrong").exists()).isFalse();
        var loaded = service.findMine(id);
        assertThat(loaded.travels().getFirst().generatedLines()).isEmpty();
        assertThat(loaded.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void resetToCalculatedRestoresASuppressedLineAtItsStatutoryCount() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);
        overrideCount("Per diem allowance (partial day)", 0, "the leftover was personal");
        findButton().withText("Save").click();
        // Reload, so the reset acts on a persisted suppression rather than a live one.
        navigate(ReportDetailView.class, id);
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Removed from the report. Reason: the leftover was personal");

        clickRowAction("Per diem allowance (partial day)", "Reset to calculated");

        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("Per diem allowance (partial day)", "€25.00",
                "€133.00").doesNotContain("Removed");

        findButton().withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.travels().getFirst().quantityOverrides()).isEmpty();
        assertThat(loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL).orElseThrow()
                .quantity()).isEqualByComparingTo("1.00");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void aSuppressedLineIsShownReadOnlyOnASubmittedReport() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);
        overrideCount("Per diem allowance (partial day)", 0, "the leftover was personal");
        findButton().withText("Save").click();
        service.submit(id, service.findMine(id).version());

        navigate(ReportDetailView.class, id);

        // The record of what was dropped and why still reads — an approver's question
        // about a missing partial day has an answer on the report — but the way back is
        // gone with every other mutation surface (the existing DRAFT/REJECTED gating).
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Removed from the report. Reason: the leftover was personal");
        assertThat(rowActions("Per diem allowance (partial day)")).isEmpty();
    }

    /**
     * A DRAFT report with one meal-allowance trip whose line carries a <em>saved</em>
     * receipt — the state the receipt-destruction warning is about.
     *
     * <p>The session is cleared afterwards so the suppression runs against a fresh
     * load, as a real request would: a request that loads a report never loads its
     * receipts (nothing references them — {@code ExpenseLine} has no back-reference,
     * which is why the cascade lives in the schema), whereas this
     * {@code @Transactional} test would still be holding the one it just created.
     */
    private Long seedMealTravelWithSavedReceipt(String filename) {
        var id = seedReportWithMealTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        navigate(ReportDetailView.class, id);
        clickRowAction("Meal allowance", "Add receipt");
        findUpload().upload(filename, "image/jpeg", jpegBytes());
        findButton().withText("Save receipt").click();
        findButton().withText("Save").click();
        entityManager.clear();
        return id;
    }

    // --- A trip edit clears the override it invalidates (issue #133) ---
    //
    // An override is a standing correction to a specific calculated number ("2 full
    // days, not the 3 you calculated"). When a trip edit moves that number the
    // correction no longer applies to anything, so saving the edit clears it — but
    // asks first, naming the change. The trigger is deliberately narrow: per kind, and
    // only when the RECALCULATED COUNT actually differs. A confirm that cries wolf gets
    // clicked through, which is the silent data loss it exists to prevent.

    @Test
    void aTripEditThatMovesTheCalculatedCountConfirmsAndClearsTheOverride() {
        // 55 h → 2 full days + 1 partial; the full days are overridden down to 1.
        var id = seedOverriddenTravel(DEP.plusHours(55));

        // Lengthen the trip to 79 h → 3 full days: the number the override corrected
        // has moved, so the correction cannot stand.
        editTripReturn(DEP.plusHours(79));

        assertThat(clearingConfirm().getHeaderTitle()).isEqualTo("Clear your override?");
        assertThat(dialogText(clearingConfirm())).contains(
                "Per diem allowance (full day): calculated 2 → 3 days.",
                "Your override (1 day — \"the Wednesday was personal\") "
                        + "will be cleared.");
        findButton().withText("Clear and save trip").click();

        // The line is back to the statutory count, live: 3 × €54.00 + the partial day.
        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("3 × €54.00 = €162.00", "€187.00")
                .doesNotContain("Overridden", "the Wednesday was personal");

        findButton().withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.quantityOverrides()).isEmpty();
        assertThat(trip.returnAt()).isEqualTo(DEP.plusHours(79));
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("187.00");
    }

    @Test
    void cancellingTheClearingConfirmAbandonsTheTripSaveAndKeepsTheOverride() {
        var id = seedOverriddenTravel(DEP.plusHours(55));

        editTripReturn(DEP.plusHours(79));
        findButton().withText("Keep editing").click();

        // A full retreat: the trip editor is still open on the user's edit, and behind
        // it the trip and its override are untouched (1 × €54.00 + €25.00 = €79.00).
        assertThat(findButton().withText("Save").inside(findDialog()).exists()).isTrue();
        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("Overridden",
                "Reason: the Wednesday was personal", "€79.00");

        // Abandon the trip edit, then save the report: nothing of the edit survived.
        findButton().withText("Cancel").click();
        findButton().withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.returnAt()).isEqualTo(DEP.plusHours(55));
        assertThat(trip.quantityOverrides()).containsKey(GeneratedLineKind.PER_DIEM_FULL);
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("79.00");
    }

    @Test
    void editingOnlyThePurposeOrDestinationsNeverPromptsAndNeverClears() {
        var id = seedOverriddenTravel(DEP.plusHours(55));

        clickRowAction(TRIP, "Edit");
        findTextField().withLabel("Travel purpose").setValue("Client visit (Acme)");
        findTextField().withLabel("Destinations").setValue("Helsinki, Espoo");
        saveTrip();

        // Nothing the calculation can see changed, so nothing is asked and nothing lost.
        assertThat(findButton().withText("Clear and save trip").exists()).isFalse();
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Overridden", "Reason: the Wednesday was personal", "€79.00");

        findButton().withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.purpose()).isEqualTo("Client visit (Acme)");
        assertThat(trip.quantityOverrides()
                .get(GeneratedLineKind.PER_DIEM_FULL).quantity())
                .isEqualByComparingTo("1.00");
    }

    @Test
    void aTimeChangeTheDayCountAbsorbsNeverPromptsAndNeverClears() {
        var id = seedOverriddenTravel(DEP.plusHours(55));

        // 55 h → 55 h 15 min: still 2 full days and one leftover over the 6 h partial
        // threshold. The inputs moved; the counts the override corrects did not.
        editTripReturn(DEP.plusHours(55).plusMinutes(15));

        assertThat(findButton().withText("Clear and save trip").exists()).isFalse();
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Overridden", "Reason: the Wednesday was personal", "€79.00");

        findButton().withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.returnAt()).isEqualTo(DEP.plusHours(55).plusMinutes(15));
        assertThat(trip.quantityOverrides())
                .containsKey(GeneratedLineKind.PER_DIEM_FULL);
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("79.00");
    }

    @Test
    void clearingIsPerKindSoAnUnaffectedOverrideStands() {
        var id = seedOverriddenTravel(DEP.plusHours(55));
        overrideCount("Per diem allowance (partial day)", 1, "the leftover was worked");
        findButton().withText("Save").click();

        // 55 h → 31 h: the full days drop 2 → 1, but the trip still earns exactly one
        // partial day, so only the full-day override is invalidated.
        editTripReturn(DEP.plusHours(31));

        assertThat(clearingConfirm().getHeaderTitle()).isEqualTo("Clear your override?");
        assertThat(dialogText(clearingConfirm()))
                .contains("Per diem allowance (full day): calculated 2 → 1 day.")
                .doesNotContain("Per diem allowance (partial day)");
        findButton().withText("Clear and save trip").click();
        findButton().withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.quantityOverrides())
                .containsOnlyKeys(GeneratedLineKind.PER_DIEM_PARTIAL);
        assertThat(trip.generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL).orElseThrow()
                .overrideReason()).isEqualTo("the leftover was worked");
    }

    @Test
    void aMealCountChangeLeavesAPerDiemOverrideInPlace() {
        // The other direction of per-kind clearing: a trip paying a meal allowance
        // (so earning no per-diem, issue #93) with an override on each kind — the
        // per-diem one dormant, since an override never conjures a line.
        var id = seedMealTravelOverriddenOnBothKinds();
        navigate(ReportDetailView.class, id);

        // Stop paying the meal allowance: its count drops 1 → 0. The trip stays not
        // eligible, so the per-diem count is 0 either side and its override stands.
        clickRowAction(TRIP, "Edit");
        findCheckbox().withLabel("Pay meal allowance?").click();
        saveTrip();

        assertThat(dialogText(clearingConfirm()))
                .contains("Meal allowance: calculated 1 → 0 meals.")
                .doesNotContain("Per diem allowance");
        findButton().withText("Clear and save trip").click();
        findButton().withText("Save").click();

        assertThat(service.findMine(id).travels().getFirst().quantityOverrides())
                .containsOnlyKeys(GeneratedLineKind.PER_DIEM_FULL);
    }

    @Test
    void aTripEditThatMovesTwoOverriddenCountsReportsBothInOneConfirm() {
        var id = seedOverriddenTravel(DEP.plusHours(55));
        overrideCount("Per diem allowance (partial day)", 1, "the leftover was worked");
        findButton().withText("Save").click();

        // 55 h → 72 h: three whole days and no leftover at all, so both corrected
        // counts move (full 2 → 3, partial 1 → 0) — one dialog, both named.
        editTripReturn(DEP.plusHours(72));

        // One dialog, both corrections named — not two prompts in a row.
        assertThat(clearingConfirm().getHeaderTitle()).isEqualTo("Clear your overrides?");
        assertThat(dialogText(clearingConfirm())).contains(
                "Per diem allowance (full day): calculated 2 → 3 days.",
                "Per diem allowance (partial day): calculated 1 → 0 days.",
                "\"the Wednesday was personal\"", "\"the leftover was worked\"");
        findButton().withText("Clear and save trip").click();
        findButton().withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.quantityOverrides()).isEmpty();
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("162.00");
    }

    @Test
    void editingAnOverrideNeverChangesTheCalculationAndNeverPrompts() {
        // The two surfaces are independent: an override is a correction to the
        // calculation's output, so it can never move the calculation's input.
        var id = seedOverriddenTravel(DEP.plusHours(55));

        clickRowAction("Per diem allowance (full day)", "Edit override");
        findIntegerField().withLabel("Count").setValue(2);
        findTextArea().withLabel("Reason for the override")
                .setValue("both days were personal after all");
        findButton().withText("Save override").click();

        assertThat(findButton().withText("Clear and save trip").exists()).isFalse();
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Calculated: 2 × €54.00 = €108.00",
                        "Reason: both days were personal after all");

        findButton().withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.returnAt()).isEqualTo(DEP.plusHours(55));
        assertThat(trip.quantityOverrides()
                .get(GeneratedLineKind.PER_DIEM_FULL).quantity())
                .isEqualByComparingTo("2.00");
    }

    @Test
    void theTripPreviewShowsCalculatedFiguresAnnotatedWhereAnOverrideIsInForce() {
        // What makes the "2 → 3" warning legible: the dialog previews the trip inputs,
        // never the corrected figures — and says so where a correction is in force, so
        // the preview and the report showing different numbers is never a mystery.
        seedOverriddenTravel(DEP.plusHours(55));

        clickRowAction(TRIP, "Edit");

        assertThat(tripPreviewText()).contains(
                "Per diem allowance (full day)", "€108.00",
                "Overridden: 1 day on the report (the Wednesday was personal)");
        // The report row behind still shows the effective figure — the two disagree by
        // design, which is exactly why the preview line is annotated.
        assertThat(getCurrentView().getElement().getTextRecursively()).contains("€79.00");
    }

    @Test
    void aSuppressedKindIsAnnotatedInThePreviewThatNoLongerListsIt() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);
        overrideCount("Per diem allowance (partial day)", 0, "the leftover was personal");
        findButton().withText("Save").click();

        clickRowAction(TRIP, "Edit");

        // The calculated preview still lists the partial day (the rules award it), and
        // the note says the report does not.
        assertThat(tripPreviewText()).contains(
                "Per diem allowance (partial day)", "€25.00",
                "Overridden: no line on the report (the leftover was personal)");
    }

    /**
     * Seeds a DRAFT report with one domestic trip of the given return time, corrects
     * its full-day count down to 1, and saves — the state every clearing case below
     * starts from: a persisted override standing on a persisted trip. Returns the
     * report id, with the view left open on it.
     */
    private Long seedOverriddenTravel(LocalDateTime returnAt) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, returnAt);
        navigate(ReportDetailView.class, id);
        overrideCount("Per diem allowance (full day)", 1, "the Wednesday was personal");
        findButton().withText("Save").click();
        return id;
    }

    /**
     * The clearing confirm a trip save opened (issue #133), identified by a phrase
     * only it uses — the trip editor is still open behind it, so "the dialog" is
     * ambiguous.
     */
    private Dialog clearingConfirm() {
        return openDialogSaying("recalculates its allowances");
    }

    /** The trip editor's live allowance preview, as text. */
    private String tripPreviewText() {
        return dialogText(openDialogSaying("Trip total"));
    }

    /**
     * The open dialog whose content includes {@code marker}.
     *
     * <p>Read a dialog's words from the dialog, never from
     * {@code UI.getCurrent().getElement().getTextRecursively()} (F-057): the UI's own
     * text never carries the view's text and does not carry a <em>just-opened</em>
     * dialog's either, so asserting there silently reads as "the dialog says nothing".
     */
    private Dialog openDialogSaying(String marker) {
        // Innermost match: since Vaadin 25.3 a dialog opened while a modal dialog is
        // open attaches *inside* it (F-082), so the outer editor's recursive text also
        // contains the confirm's marker. Skip any match that encloses another.
        var matches = findDialog().components().stream().filter(Dialog::isOpened)
                .filter(dialog -> dialogText(dialog).contains(marker)).toList();
        return matches.stream()
                .filter(outer -> matches.stream().noneMatch(inner -> inner != outer
                        && isAncestor(outer, inner)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No open dialog says \"" + marker + "\""));
    }

    private static boolean isAncestor(Dialog outer, Dialog inner) {
        for (var e = inner.getElement().getParent(); e != null; e = e.getParent()) {
            if (e.equals(outer.getElement())) {
                return true;
            }
        }
        return false;
    }

    private static String dialogText(Dialog dialog) {
        return dialog.getElement().getTextRecursively();
    }

    /** Re-costs the open report's only trip by moving its return time. */
    private void editTripReturn(LocalDateTime returnAt) {
        clickRowAction(TRIP, "Edit");
        findDateTimePicker().withLabel("Return").setValue(returnAt);
        saveTrip();
    }

    /**
     * Seeds a DRAFT report with one meal-allowance trip carrying an override on
     * <em>both</em> the meal and the full-day per-diem kind. The per-diem one is
     * dormant — the trip is not eligible, and an override never conjures a line — which
     * is precisely what makes it the control in a per-kind clearing test. Seeded
     * through the service because a dormant override has no row to be made on.
     */
    private Long seedMealTravelOverriddenOnBothKinds() {
        var zero = BigDecimal.ZERO.setScale(2);
        var trip = TravelDto.domestic(null, DEP, DEP.plusHours(11), "Helsinki",
                        "Client visit", true, false, false, zero, true, zero)
                .withQuantityOverride(GeneratedLineKind.MEAL,
                        QuantityOverride.of(GeneratedLineKind.MEAL,
                                new BigDecimal("2"), "two meals were taken"))
                .withQuantityOverride(GeneratedLineKind.PER_DIEM_FULL,
                        QuantityOverride.of(GeneratedLineKind.PER_DIEM_FULL,
                                BigDecimal.ONE, "one day was personal"));
        return service.create(new ReportDetailDto(null, LocalDate.of(2026, 7, 10),
                "seed", ReportStatus.DRAFT, 0L, List.of(), List.of(trip), zero, zero,
                zero, zero, zero, zero));
    }

    // --- Daily allowance: one radio group, three answers (issue #93, #179) ---
    //
    // The "not eligible" and "free lunch" checkboxes are one RadioButtonGroup now —
    // travel-editor-dialog.md § Eligibility — bound in the form layer to the same two
    // booleans the checkboxes wrote, so the saved trip is what it always was. A meal
    // allowance is still paid only when no per-diem applies, so "Pay meal allowance"
    // and the group keep their cascade (payMeal ⟹ Not eligible); nothing is disabled.

    @Test
    void theDailyAllowanceGroupOpensOnFullByDefaultAndSavesBothFlagsFalse() {
        navigate(ReportDetailView.class);
        findButton().withAriaLabel("Add travel").click();

        var group = dailyAllowance();
        assertThat(group.getComponent().getLabel()).isEqualTo("Daily allowance");
        assertThat(group.getSelected()).isEqualTo(TravelFormModel.DailyAllowance.FULL);
        assertThat(group.getComponent().getListDataView().getItems()
                .map(TravelFormModel.DailyAllowance::label))
                .containsExactly("Full daily allowance", "Halved, free lunch provided",
                        "Not eligible");
        // Vertical is Aura's default; the horizontal variant the frame draws is not set.
        assertThat(group.getComponent().getThemeNames()).isEmpty();
        // The two checkboxes it replaces are gone; the two that stay are these.
        assertThat(findCheckbox().inside(findDialog()).components())
                .extracting(Checkbox::getLabel)
                .containsExactly("Charge expenses from customer?", "Pay meal allowance?");

        fillMinimalTrip();
        saveTrip();
        findButton().withText("Save").click();

        var trip = service.findMine(service.listMine().getFirst().id()).travels().getFirst();
        assertThat(trip.notEligibleForAllowance()).isFalse();
        assertThat(trip.freeLunch()).isFalse();
    }

    @Test
    void choosingHalvedSavesEligibleWithAFreeLunch() {
        navigate(ReportDetailView.class);
        findButton().withAriaLabel("Add travel").click();
        fillMinimalTrip();

        dailyAllowance().selectItem("Halved, free lunch provided");
        // The per-diem previews halved: €54 → €27.
        assertThat(tripPreviewText()).contains("€27.00");
        saveTrip();
        findButton().withText("Save").click();

        var trip = service.findMine(service.listMine().getFirst().id()).travels().getFirst();
        assertThat(trip.notEligibleForAllowance()).isFalse();
        assertThat(trip.freeLunch()).isTrue();
    }

    @Test
    void choosingNotEligibleSavesNotEligibleWithoutAFreeLunch() {
        navigate(ReportDetailView.class);
        findButton().withAriaLabel("Add travel").click();
        fillMinimalTrip();

        dailyAllowance().selectItem("Not eligible");
        // Nothing earned: the no-allowances line stands in for the rows, and there is
        // no Trip total row to end on.
        assertThat(dialogText(openDialogSaying("No allowances for this trip")))
                .doesNotContain("Per diem allowance", "Trip total");
        saveTrip();
        findButton().withText("Save").click();

        var trip = service.findMine(service.listMine().getFirst().id()).travels().getFirst();
        assertThat(trip.notEligibleForAllowance()).isTrue();
        assertThat(trip.freeLunch()).isFalse();
    }

    @Test
    void editingATripReadsItsFlagsBackIntoTheGroup() {
        // freeLunch=true → Halved; notEligible=true → Not eligible — the mapping read
        // in the other direction, so an edit opens on what was saved.
        var zero = BigDecimal.ZERO.setScale(2);
        var halved = TravelDto.domestic(null, DEP, DEP.plusHours(11), "Helsinki",
                "Client visit", false, true, false, zero, false, zero);
        var id = service.create(new ReportDetailDto(null, LocalDate.of(2026, 7, 10),
                "seed", ReportStatus.DRAFT, 0L, List.of(), List.of(halved), zero, zero,
                zero, zero, zero, zero));
        navigate(ReportDetailView.class, id);
        clickRowAction(TRIP, "Edit");
        assertThat(dailyAllowance().getSelected())
                .isEqualTo(TravelFormModel.DailyAllowance.HALVED);
        findButton().withText("Cancel").inside(findDialog()).click();

        var mealId = seedReportWithMealTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        navigate(ReportDetailView.class, mealId);
        clickRowAction(TRIP, "Edit");
        assertThat(dailyAllowance().getSelected())
                .isEqualTo(TravelFormModel.DailyAllowance.NOT_ELIGIBLE);
        assertThat(checkboxValue("Pay meal allowance?")).isTrue();
    }

    @Test
    void checkingPayMealAllowanceSelectsNotEligible() {
        navigate(ReportDetailView.class);
        findButton().withAriaLabel("Add travel").click();
        dailyAllowance().selectItem("Halved, free lunch provided");

        findCheckbox().withLabel("Pay meal allowance?").click();

        // Meal allowance needs "no per-diem": the group moves to Not eligible, which
        // by construction drops the free lunch too.
        assertThat(checkboxValue("Pay meal allowance?")).isTrue();
        assertThat(dailyAllowance().getSelected())
                .isEqualTo(TravelFormModel.DailyAllowance.NOT_ELIGIBLE);
    }

    @Test
    void selectingFullClearsPayMealAllowance() {
        navigate(ReportDetailView.class);
        findButton().withAriaLabel("Add travel").click();
        findCheckbox().withLabel("Pay meal allowance?").click();
        assertThat(dailyAllowance().getSelected())
                .isEqualTo(TravelFormModel.DailyAllowance.NOT_ELIGIBLE);

        dailyAllowance().selectItem("Full daily allowance");

        // A per-diem applies again, so the meal allowance can't stand.
        assertThat(checkboxValue("Pay meal allowance?")).isFalse();
        assertThat(dailyAllowance().getSelected())
                .isEqualTo(TravelFormModel.DailyAllowance.FULL);
    }

    @Test
    void selectingHalvedClearsPayMealAllowance() {
        navigate(ReportDetailView.class);
        findButton().withAriaLabel("Add travel").click();
        findCheckbox().withLabel("Pay meal allowance?").click();

        dailyAllowance().selectItem("Halved, free lunch provided");

        assertThat(checkboxValue("Pay meal allowance?")).isFalse();
        assertThat(dailyAllowance().getSelected())
                .isEqualTo(TravelFormModel.DailyAllowance.HALVED);
    }

    @Test
    void travelEditorLaysItsFieldsOutAsTheDesignsTwoSectionGrid() {
        // travel-editor-dialog.md § Layout — #179 delta items 1–4, 7, 13: one
        // two-column grid with the line editor's gaps, two eyebrows and two rules as
        // colspan-2 rows, the design's field order, and the totals as the last row.
        navigate(ReportDetailView.class);
        findButton().withAriaLabel("Add travel").click();

        var dialog = findDialog().getComponent();
        assertThat(dialog.getWidth()).isEqualTo("35rem");
        assertThat(dialog.getHeaderTitle()).isEqualTo("Add travel info");

        var form = $(FormLayout.class).single();
        assertThat(form.getResponsiveSteps())
                .extracting(step -> step.toJson().toString())
                .containsExactly(
                        new FormLayout.ResponsiveStep("0", 1).toJson().toString(),
                        new FormLayout.ResponsiveStep("24rem", 2).toJson().toString());
        assertThat(form.getColumnSpacing()).isEqualTo("var(--em-section-gap)");
        assertThat(form.getRowSpacing()).isEqualTo("var(--em-card-padding)");

        // The design's order, read off the grid's children. Eyebrows are Spans with
        // the .section-label role, rules are Hr.form-rule, the totals a Div.trip-totals.
        var children = form.getChildren().toList();
        assertThat(children).extracting(ReportDetailViewUiTest::gridCell).containsExactly(
                "eyebrow:Destinations", "DateTimePicker:Departure", "DateTimePicker:Return",
                "ComboBox:Destination country", "TextField:Destinations",
                "TextField:Travel purpose", "rule", "eyebrow:Expenses",
                "Checkbox:Charge expenses from customer?",
                "BigDecimalField:Kilometre allowance (km)",
                "BigDecimalField:Parking fees (€)", "RadioButtonGroup:Daily allowance",
                "Checkbox:Pay meal allowance?", "rule", "totals");
        assertThat(children).extracting(form::getColspan)
                .containsExactly(2, 1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 2, 2, 2, 2);
        // The rules carry no margin of their own — the grid's row gap spaces them.
        assertThat(children.get(6).getStyle().get("margin")).isNull();

        // Until the dates are complete the totals and the rule above them hide.
        assertThat(children.get(13).isVisible()).isFalse();
        assertThat(children.get(14).isVisible()).isFalse();
        fillMinimalTrip();
        assertThat(children.get(13).isVisible()).isTrue();
        assertThat(children.get(14).isVisible()).isTrue();
        // Label and figure are separate spans in the totals card's classes; Trip
        // total is the LAST row, and the block paints no surface of its own.
        var totals = (Div) children.get(14);
        var rows = totals.getChildren().toList();
        assertThat(rows.getLast().getElement().getTextRecursively())
                .isEqualTo("Trip total€54.00");
        assertThat(findSpan().withText("Trip total").getComponent().getClassNames())
                .contains("totals-total-row");
        assertThat(findSpan().withText("€54.00").inside(findDialog()).components())
                .anySatisfy(span -> assertThat(span.getClassNames()).contains("totals-grand"));
        assertThat(totals.getClassNames()).contains("trip-totals")
                .doesNotContain("travel-preview");

        // Footer: Cancel and a primary "Save" — no "Save trip", no close button.
        assertThat(findButton().withText("Save").inside(findDialog()).exists()).isTrue();
        assertThat(findButton().withText("Save trip").exists()).isFalse();
        assertThat(dialog.isCloseOnEsc()).isTrue();
    }

    @Test
    void travelEditorTitlesTheEditStateEditTravelInfo() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        navigate(ReportDetailView.class, id);
        clickRowAction(TRIP, "Edit");
        assertThat(findDialog().getComponent().getHeaderTitle())
                .isEqualTo("Edit travel info");
    }

    /** "kind:label" for a field, or the grid row's role for a non-field child. */
    private static String gridCell(Component child) {
        if (child instanceof Span span && span.hasClassName("section-label")) {
            return "eyebrow:" + span.getText();
        }
        if (child instanceof Hr hr && hr.hasClassName("form-rule")) {
            return "rule";
        }
        if (child instanceof Div div && div.hasClassName("trip-totals")) {
            return "totals";
        }
        return child.getClass().getSimpleName() + ":"
                + ((HasLabel) child).getLabel();
    }

    /** The open trip editor's {@code Daily allowance} radio group. */
    private RadioButtonGroupLocator<TravelFormModel.DailyAllowance> dailyAllowance() {
        return findRadioButtonGroup(TravelFormModel.DailyAllowance.class)
                .withLabel("Daily allowance");
    }

    /** Clicks the trip editor's primary — "Save", scoped to the dialog because the
     *  report behind it has a Save of its own. */
    private void saveTrip() {
        findButton().withText("Save").inside(findDialog()).click();
    }

    /** Dates, country, destinations and purpose for an 11-hour domestic trip. */
    private void fillMinimalTrip() {
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(11));
        findComboBox(String.class).withLabel("Destination country")
                .selectItem("Finland (domestic)");
        findTextField().withLabel("Destinations").setValue("Helsinki");
        findTextField().withLabel("Travel purpose").setValue("Client visit");
    }

    /** The current value of the dialog checkbox carrying the given label. */
    private boolean checkboxValue(String label) {
        return ((Checkbox) findCheckbox().withLabel(label).getComponent()).getValue();
    }

    @Test
    void removingATripRemovesItsPerDiemOnSave() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        navigate(ReportDetailView.class, id);

        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Per diem allowance");

        clickRowAction(TRIP, "Remove");
        findButton().withText("Save").click();

        assertThat(service.findMine(id).travels()).isEmpty();
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void choosingADepartureConstrainsTheReturnPickerRangeAndViceVersa() {
        navigate(ReportDetailView.class);

        findButton().withAriaLabel("Add travel").click();
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(11));

        // The overlay can no longer offer an invalid range: the return can't reach
        // back to the departure, nor the departure forward to the return. The bounds
        // sit one 15-minute step clear of each other, not on the other's instant —
        // setMin/setMax are inclusive while the trip rule is strict, so an unshifted
        // bound still offers the same instant on both sides (issue #140).
        var ret = (DateTimePicker) findDateTimePicker().withLabel("Return")
                .getComponent();
        var dep = (DateTimePicker) findDateTimePicker().withLabel("Departure")
                .getComponent();
        assertThat(ret.getMin()).isEqualTo(DEP.plusMinutes(15));
        assertThat(dep.getMax()).isEqualTo(DEP.plusHours(11).minusMinutes(15));
    }

    @Test
    void aReturnEqualToTheDepartureIsRefusedAndSaysWhy() {
        // The reported case: same date *and* same time on both sides. The tester
        // refuses to type it at all now that the return's min sits a step past the
        // departure, so set it straight on the component — the way a crafted client
        // would — and prove the value still never reaches a save silently: the
        // binder's constraint validation lands the rule in the dialog's error
        // summary, not in the generic "something went wrong" dialog (issue #140).
        navigate(ReportDetailView.class);

        findButton().withAriaLabel("Add travel").click();
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findComboBox(String.class).withLabel("Destination country")
                .selectItem("Finland (domestic)");
        findTextField().withLabel("Destinations").setValue("Helsinki");
        findTextField().withLabel("Travel purpose").setValue("Client visit");

        var ret = (DateTimePicker) findDateTimePicker().withLabel("Return")
                .getComponent();
        ret.setValue(DEP);

        saveTrip();

        var text = UI.getCurrent().getElement().getTextRecursively();
        assertThat(text).contains("Return must be after the departure");
        assertThat(text).doesNotContain("Something went wrong");
        // The dialog stays open and the trip is not committed.
        assertThat(findButton().withText("Save").inside(findDialog()).exists()).isTrue();
        assertThat(findSpan().withText("Per diem allowance").exists()).isFalse();
    }

    @Test
    void anIncompleteTripShowsTheErrorSummaryAndGeneratesNothing() {
        // The reciprocal min/max on the pickers keeps the *overlay* from producing an
        // invalid range (see the constraint test above), but a hand-typed one still
        // reaches Save — covered by the range-rule tests above. Here we prove the
        // always-enabled Save + error-summary rule (ADR-0020) on the other invalid
        // case: missing required fields.
        navigate(ReportDetailView.class);

        findButton().withAriaLabel("Add travel").click();
        saveTrip();

        // The dialog overlay carries the reasons and stays open; nothing generated.
        assertThat(UI.getCurrent().getElement().getTextRecursively()).contains(
                "Departure date & time is required", "Destinations are required");
        assertThat(findButton().withText("Save").inside(findDialog()).exists()).isTrue();
        assertThat(findSpan().withText("Per diem allowance").exists()).isFalse();
    }

    @Test
    void travelPickersCarryIncompleteAndBadInputErrorMessages() {
        // Issue #85: entering only the date (not the time) in a departure/return
        // picker makes it invalid; without the incomplete-input message that error
        // reached the summary blank. The tester can't produce partial input, so we
        // assert the messages that prevent a blank error are configured.
        navigate(ReportDetailView.class);
        findButton().withAriaLabel("Add travel").click();

        for (String label : new String[] { "Departure", "Return" }) {
            var picker = (DateTimePicker) findDateTimePicker().withLabel(label)
                    .getComponent();
            assertThat(picker.getI18n()).as(label + " i18n").isNotNull();
            assertThat(picker.getI18n().getIncompleteInputErrorMessage())
                    .as(label + " incomplete-input message").isNotBlank();
            assertThat(picker.getI18n().getBadInputErrorMessage())
                    .as(label + " bad-input message").isNotBlank();
            // The range bounds are constraints too (issue #140) — unnamed, they'd
            // reach the summary as the same blank bullet issue #85 fixed.
            assertThat(picker.getI18n().getMinErrorMessage())
                    .as(label + " min message").isNotBlank();
            assertThat(picker.getI18n().getMaxErrorMessage())
                    .as(label + " max message").isNotBlank();
        }
    }

    @Test
    void aSubmittedReportShowsTheTripButHidesEveryTripAction() {
        var id = seedSubmittedReportWithTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        navigate(ReportDetailView.class, id);

        // The trip info and its per-diem are still visible (read path)…
        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("Client visit", "Per diem allowance", "€54.00");
        // …a trip-only read-only report must not prompt "add your first": the empty
        // state is hidden, so no visible span carries it (ADR-0020)…
        assertThat(findSpan().withText("No expenses yet — add your first.").exists())
                .isFalse();
        // …and every mutation surface is gone on a read-only report.
        assertThat(findButton().withAriaLabel("Add travel").exists()).isFalse();
        assertThat(rowActions(TRIP)).isEmpty();
        assertThat($(RowActionMenu.class).all()).isEmpty();
    }

    @Test
    void removingALinePersistsOnSave() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);

        assertThat(getCurrentView().getElement().getTextRecursively()).contains("€60.00");

        clickRowAction($(RowActionMenu.class).first(), "Remove");
        findButton().withText("Save").click();

        assertThat(service.findMine(id).lines()).isEmpty();
    }

    // ------------------------------------------------ the re-cut layout (#172)
    //
    // The design re-cut the line list rather than restyling it: one card per
    // SECTION holding its lines as rows, where the previous frame drew one bordered
    // card per line. These pin the shape, not the pixels — spacing, alignment and
    // colour are what /figma-visual-verification is for.

    @Test
    void eachSectionIsOneCardHoldingItsLinesAsRows() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(31));
        navigate(ReportDetailView.class, id);
        findButton().withAriaLabel("Add expense").click();
        addLineThroughEditor("40.00");

        // Two cards for the whole view, not one per line: the travel variant and the
        // plain Expenses one.
        var cards = $(Div.class).withClassName("expense-item-card").all();
        assertThat(cards).hasSize(2);
        assertThat(cards.getFirst().getClassNames()).contains("expense-item-card--travel");
        assertThat(cards.get(1).getClassNames())
                .doesNotContain("expense-item-card--travel");

        // The travel card holds the trip row and both per-diem rows it generated —
        // nested INSIDE it, indented, rather than in a wrapper beside it.
        assertThat(rowsIn(cards.getFirst())).hasSize(3);
        assertThat(rowsIn(cards.getFirst()).get(1).getClassNames())
                .contains("travel-line-row");
        assertThat(rowsIn(cards.get(1))).hasSize(1);

        // And nothing is a .line-card any more.
        assertThat($(HorizontalLayout.class).withClassName("line-card").all()).isEmpty();
    }

    @Test
    void aSectionWithNoRowsRendersNoCard() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "empty");
        navigate(ReportDetailView.class, id);

        // A fresh draft has no trip and no line, so both cards are absent while both
        // headings and their Add actions stand.
        assertThat($(Div.class).withClassName("expense-item-card").all()).isEmpty();
        assertThat(findSpan().withText("Travel info").exists()).isTrue();
        assertThat(findSpan().withText("Expenses").exists()).isTrue();
        assertThat(findButton().withAriaLabel("Add travel").exists()).isTrue();
        assertThat(findButton().withAriaLabel("Add expense").exists()).isTrue();
    }

    @Test
    void aReadOnlyReportWithNothingInASectionRendersNoSectionAtAll() {
        // No trip, and read-only: a "TRAVEL INFO" heading over nothing, beside an Add
        // the report cannot offer, is worse than no section (ADR-0020).
        var id = seedSubmittedReport(LocalDate.of(2026, 7, 1), "80.00");
        navigate(ReportDetailView.class, id);

        assertThat(findSpan().withText("Travel info").exists()).isFalse();
        assertThat(findSpan().withText("Expenses").exists()).isTrue();
    }

    @Test
    void anExpenseRowDrawsItsTypesOwnGlyphAndNoColourSwatch() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);

        // The glyph is the type's persisted icon (ADR-0026), not a colour hashed off
        // its name. seedReportWithLine files the line under the first active type,
        // Travel allowance, whose seeded glyph is `plane` (V15).
        var glyph = $(Span.class).withClassName("expense-row-icon").single();
        assertThat(find(SvgIcon.class, glyph).single().getSymbol()).isEqualTo("plane");
        // The hashed swatch is gone, along with the four-way --aura-red collision.
        assertThat($(Div.class).withClassName("category-dot").all()).isEmpty();
    }

    /**
     * The glyph is optional on {@code ExpenseType}, so a line can legitimately reach
     * the row with none — and must then render none rather than falling back to a
     * wrong one. Nothing rests on it: the type name always renders as text beside it
     * (ADR-0020).
     *
     * <p>Runs as the admin because creating a type is {@code @RolesAllowed("ADMIN")},
     * and a glyphless type is the only honest way to reach this state: the icon comes
     * off the persisted <em>type</em>, so a null in an input DTO does not survive the
     * save-and-reload the view actually renders from.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void aTypeWithNoGlyphChosenRendersNoneAndStillNamesItself() {
        var rate = firstActiveRate();
        var type = referenceData.createExpenseType("Glyphless", rate.id(), null);
        var line = ExpenseLineDto.of(null, type.id(), type.name(), type.icon(),
                rate.id(), rate.value(), new BigDecimal("12.00"), null);
        var zero = BigDecimal.ZERO.setScale(2);
        var id = service.create(new ReportDetailDto(null, LocalDate.of(2026, 7, 1),
                "seed", ReportStatus.DRAFT, 0L, List.of(line), zero, zero, zero));

        navigate(ReportDetailView.class, id);

        assertThat(find(SvgIcon.class,
                $(Span.class).withClassName("expense-row-icon").single()).all())
                .isEmpty();
        assertThat(findSpan().withText("Glyphless").exists()).isTrue();
    }

    @Test
    void aRowIsNotClickableAndTheMenuIsTheOnlyRouteToEditing() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);

        // Withdrawn with the card the row used to be: no .clickable anywhere, and the
        // row's own actions are exactly Edit and Remove behind the ⋮.
        assertThat($(HorizontalLayout.class).withClassName("clickable").all()).isEmpty();
        assertThat(rowActions("Travel allowance")).containsExactly("Edit", "Remove");
    }

    @Test
    void everyRowMenuIsNamedAfterItsRowAndEveryActionCarriesText() {
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00"));
        navigate(ReportDetailView.class, id);

        // "Actions" alone is useless on a page holding five of them, so each names
        // its row (ADR-0020, row-action-menu.md).
        // A per-diem-eligible trip earns no meal allowance — the two are mutually
        // exclusive under the Finnish rule (issue #93) — so this trip's four rows are
        // the trip itself plus per diem, kilometre and parking.
        assertThat($(RowActionMenu.class).all())
                .extracting(ReportDetailViewUiTest::triggerName)
                .containsExactlyInAnyOrder("Actions for " + TRIP,
                        "Actions for Per diem allowance (full day)",
                        "Actions for Kilometre allowance",
                        "Actions for Parking");
        // And every item in every menu is text-labelled, never an icon alone.
        assertThat($(RowActionMenu.class).all()).allSatisfy(menu ->
                assertThat(menu.getItems().get(0).getSubMenu().getItems())
                        .isNotEmpty()
                        .allSatisfy(item -> assertThat(item.getText()).isNotBlank()));
    }

    @Test
    void aRowMenusNameFollowsTheTypeTheEditorChanges() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);
        assertThat(triggerName($(RowActionMenu.class).single()))
                .isEqualTo("Actions for Travel allowance");

        // The row keeps its component and re-reads the signal, so the accessible name
        // has to follow or it goes quietly stale.
        openLineCardEditor();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Accommodation");
        saveExpense();

        assertThat(triggerName($(RowActionMenu.class).single()))
                .isEqualTo("Actions for Accommodation");
    }

    @Test
    void bothAddActionsReadAddAndAreToldApartByTheirAccessibleNames() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "empty");
        navigate(ReportDetailView.class, id);

        // The design labels both simply "Add", so the page carries two controls with
        // the same visible name and the aria-label is what names the section.
        assertThat(findButton().withAriaLabel("Add travel").exists()).isTrue();
        assertThat(findButton().withAriaLabel("Add expense").exists()).isTrue();
        assertThat($(Button.class).withCondition(
                button -> "Add".equals(button.getText())).all()).hasSize(2);
        // And the back link keeps its own name.
        assertThat(findButton().withAriaLabel("Back to reports").exists()).isTrue();
    }

    @Test
    void onlyTheVatBearingGeneratedLineShowsANetVatSplit() {
        // Parking is VAT-bearing at 25.5 %; the per-diem, kilometre and meal lines are
        // statutory tax-free allowances. The design draws the same net/VAT string on
        // every generated row including the per-diem one, which is mock text rather
        // than a specification (#173).
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00"));
        navigate(ReportDetailView.class, id);

        assertThat(splitOn("Parking")).startsWith("net ").contains("(25.5 %)");
        assertThat(splitOn("Per diem allowance (full day)")).isNull();
        assertThat(splitOn("Kilometre allowance")).isNull();

        // …and the meal allowance, which this trip does not earn, on one that does.
        navigate(ReportDetailView.class, seedReportWithMealTravel(
                LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11)));
        assertThat(splitOn("Meal allowance")).isNull();
    }

    @Test
    void aReadOnlyReportRendersNoRowMenusAndNoAddActionsButKeepsItsReceipts() {
        var id = seedReportWithReceipt(LocalDate.of(2026, 7, 1), "70.00", "hotel.jpg");
        service.submit(id, service.findMine(id).version());
        navigate(ReportDetailView.class, id);

        // No menu at all — not a disabled trigger — and neither Add action.
        assertThat($(RowActionMenu.class).all()).isEmpty();
        assertThat(findButton().withAriaLabel("Add expense").exists()).isFalse();
        assertThat(findButton().withAriaLabel("Add travel").exists()).isFalse();
        // The receipt stays activatable, so a submitted report's receipts remain
        // viewable (ADR-0021).
        assertThat(findButton().withAriaLabel("Preview receipt: hotel.jpg").exists())
                .isTrue();
    }

    @Test
    void anAttachmentRendersAsAPaperclipChipThatStillOpensThePreview() {
        var id = seedReportWithReceipt(LocalDate.of(2026, 7, 1), "70.00", "hotel.jpg");
        navigate(ReportDetailView.class, id);

        // The resting state is the design's chip — a paperclip and the filename — and
        // only the resting state changed: activating it opens the enlarge dialog.
        var chip = $(Button.class).withClassName("expense-row-attachment").single();
        assertThat(chip.getText()).isEqualTo("hotel.jpg");
        assertThat(find(SvgIcon.class, chip).single().getSymbol()).isEqualTo("paperclip");

        test(chip).click();
        assertThat($(Dialog.class).all()).anySatisfy(dialog ->
                assertThat(dialog.isOpened()).isTrue());
        assertThat($(Image.class).exists()).isTrue();
    }

    @Test
    void theStatusHistoryIsOneBoxWithItsSharedSectionHeading() {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Missing receipt");
        navigate(ReportDetailView.class, id);

        // One box holding every entry, not one bordered box per entry, under the same
        // uppercase .section-label role the other two section headings use.
        var box = $(Div.class).withClassName("status-history-box").single();
        assertThat(box.getChildren().count()).isEqualTo(2);       // Submitted, Rejected
        assertThat($(Span.class)
                .withCondition(span -> "Status history".equals(span.getText()))
                .single().getClassNames())
                .contains("section-label");
        assertThat($(VerticalLayout.class).withClassName("status-history-entry").all())
                .hasSize(2);
    }

    /** Adds one expense line through the (already open) editor at the given amount. */
    private void addLineThroughEditor(String amount) {
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem(firstActiveType().name());
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price")
                .setValue(new BigDecimal(amount));
        saveExpense();
    }

    /** The .expense-row children of one section card, in order. */
    private static List<Component> rowsIn(Div card) {
        return card.getChildren()
                .filter(child -> child.getElement().getClassList()
                        .contains("expense-row"))
                .toList();
    }

    /**
     * The {@code net … · VAT …} sub-line on the named row, or {@code null} if it has
     * none. Read off the row rather than the whole view, since a manual line beside
     * it would otherwise satisfy the assertion.
     */
    private String splitOn(String rowLabel) {
        var row = $(HorizontalLayout.class)
                .withCondition(candidate -> candidate.getElement().getClassList()
                        .contains("expense-row")
                        && candidate.getElement().getTextRecursively()
                                .startsWith(rowLabel))
                .single();
        return find(Span.class, row).withCondition(
                        span -> span.getText().startsWith("net ")).all().stream()
                .map(Span::getText).findFirst().orElse(null);
    }

    /**
     * Opens the line editor for the report's first expense row. The row is no longer
     * clickable — the ⋮ menu is the only route to editing (#172) — so this opens the
     * first row's menu and chooses Edit.
     */
    private void openLineCardEditor() {
        clickRowAction($(RowActionMenu.class).first(), "Edit");
    }

    /**
     * Clicks the line editor's primary button. It reads {@code Save} as of the
     * design's frame (358:3267), which is also the report's own save button, so it
     * has to be located <em>inside</em> the dialog rather than by text alone.
     */
    private void saveExpense() {
        findButton().withText("Save").inside(findDialog()).click();
    }

    /** The line editor's ComboBox with the given label. */
    private ComboBox<?> comboBox(String label) {
        return $(ComboBox.class).withLabel(label).single();
    }

    /** The line editor's BigDecimalField with the given label. */
    private BigDecimalField bigDecimalField(String label) {
        return findBigDecimalField().withLabel(label).getComponent();
    }

    /** The ⋮ trigger's accessible name, which must identify its row. */
    private static String triggerName(RowActionMenu menu) {
        return menu.getItems().get(0).getElement().getAttribute("aria-label");
    }

    /** The one row menu whose trigger names {@code rowLabel}. */
    private RowActionMenu rowMenu(String rowLabel) {
        return $(RowActionMenu.class)
                .withCondition(menu -> ("Actions for " + rowLabel)
                        .equals(triggerName(menu)))
                .single();
    }

    /** Opens the named row's ⋮ and activates the named action. */
    private void clickRowAction(String rowLabel, String action) {
        clickRowAction(rowMenu(rowLabel), action);
    }

    /** Opens a row's ⋮ and activates the named action. */
    private void clickRowAction(RowActionMenu menu, String action) {
        List<MenuItem> items = menu.getItems().get(0).getSubMenu().getItems();
        int index = -1;
        for (int i = 0; i < items.size(); i++) {
            if (action.equals(items.get(i).getText())) {
                index = i;
            }
        }
        assertThat(index).as("menu item '%s' is present", action).isNotNegative();
        // Index rather than caption path: the top-level trigger is icon-only, so
        // clickItem(String...) has no root caption to match against.
        // The tester, not use(menu): 25.3's MenuBarLocator re-finds the menu from
        // the UI, and a Grid cell component is not in that tree (F-082).
        new MenuBarTester<>(menu).clickItem(0, index);
    }

    /** The text labels a row's ⋮ offers, in order — empty if it has no menu at all. */
    private List<String> rowActions(String rowLabel) {
        var menus = $(RowActionMenu.class)
                .withCondition(menu -> ("Actions for " + rowLabel)
                        .equals(triggerName(menu)))
                .all();
        if (menus.isEmpty()) {
            return List.of();
        }
        return menus.getFirst().getItems().get(0).getSubMenu().getItems().stream()
                .map(MenuItem::getText).toList();
    }
}
