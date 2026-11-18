package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.QuantityOverride;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseLineDto;
import com.vaadin.expensemanager.report.service.ReportDetailDto;
import com.vaadin.expensemanager.report.service.TravelDto;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedBigDecimalField;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedCheckbox;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedDateTimePicker;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.component.UnifiedIntegerField;
import com.vaadin.testing.unified.component.UnifiedMenuBar;
import com.vaadin.testing.unified.component.UnifiedRadioButtonGroup;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.component.UnifiedTextArea;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.component.UnifiedUpload;
import com.vaadin.expensemanager.base.ui.UnifiedHorizontalLayout;
import com.vaadin.expensemanager.base.ui.UnifiedVerticalLayout;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified view test (pyramid layer 3, ADR-0012) for {@link ReportDetailView}
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

    @Test
    void newReportSaveWithDefaultsPersistsTodayDatedDraftAndRoutesToId(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        // No field touched: the date defaults to today; first save persists.
        page.find(UnifiedButton.class).withText("Save").click();

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        assertThat(mine.getFirst().reportDate()).isEqualTo(LocalDate.now());
        // Still on the detail view — now the persisted /report/{id} form.
        assertThat(page).isAt(ReportDetailView.class);
    }

    @Test
    void missingRequiredDateShowsErrorSummaryAndPersistsNothing(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        // A user emptying the required date, then saving. (The Browserless tester
        // refused to *set* null on a required field, F-021; the Unified API clears it
        // as a user does, on every Testing Tool.)
        page.find(UnifiedDatePicker.class).withLabel("Report date").clear();
        page.find(UnifiedButton.class).withText("Save").click();

        // Always-enabled Save + top-of-form error summary (ADR-0020).
        assertSays(page, "Report date is required");
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void reportDateCarriesABadInputErrorMessage(UnifiedPage page) {
        // Issue #85: typing an unparseable date (e.g. "dsdds") makes the picker
        // invalid; without a bad-input message the error reaches the summary blank.
        // The Unified API types the text as a user would, so the summary is
        // asserted directly rather than the i18n configuration behind it.
        page.navigate(ReportDetailView.class);

        page.find(UnifiedDatePicker.class).withLabel("Report date").typeText("dsdds");
        page.find(UnifiedButton.class).withText("Save").click();

        assertSays(page, "Enter a valid date");
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void editingExistingReportUpdatesItsFields(UnifiedPage page) {
        var id = seedReport(LocalDate.of(2026, 7, 1), "before");
        page.navigate(ReportDetailView.class, id);

        page.find(UnifiedTextArea.class).withLabel("Additional Info").setValue("after");
        page.find(UnifiedDatePicker.class).withLabel("Report date").setValue(LocalDate.of(2026, 7, 20));
        page.find(UnifiedButton.class).withText("Save").click();

        var reloaded = service.findMine(id);
        assertThat(reloaded.additionalInformation()).isEqualTo("after");
        assertThat(reloaded.reportDate()).isEqualTo(LocalDate.of(2026, 7, 20));
    }

    @Test
    void deletingADraftRemovesItAndReturnsToTheList(UnifiedPage page) {
        var id = seedReport(LocalDate.of(2026, 7, 1), "scratch");
        page.navigate(ReportDetailView.class, id);

        page.find(UnifiedButton.class).withText("Delete").click();          // opens the confirm dialog
        page.find(UnifiedButton.class).withText("Delete report").click();   // confirms

        assertThat(page).isAt(MyReportsView.class);
        assertThat(service.listMine()).noneMatch(r -> r.id().equals(id));
    }

    @Test
    void newReportDoesNotOfferDelete(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        // Delete is hidden until the report is a persisted DRAFT.
        assertThat(page.find(UnifiedButton.class).withText("Delete").visible()).doesNotExist();
    }

    @Test
    void addingALineViaTheDialogUpdatesLiveTotalsAndPersistsOnSave(UnifiedPage page) {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();
        page.find(UnifiedComboBox.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        page.find(UnifiedComboBox.class).withLabel("VAT rate").selectItem("25.5 %");
        page.find(UnifiedBigDecimalField.class).withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        saveExpense(page);

        // Live total bar reflects the added line before the report is saved.
        assertSays(page, "€100.00");

        page.find(UnifiedButton.class).withText("Save").click();

        var reloaded = service.findMine(id);
        assertThat(reloaded.lines()).hasSize(1);
        assertThat(reloaded.lines().getFirst().amount()).isEqualByComparingTo("100.00");
        assertThat(reloaded.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void lineEditorDefaultsQuantityToOneAndShowsTheLineTotalLive(UnifiedPage page) {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();

        // Quantity starts at 1, so the line total is the unit price itself.
        assertThat(page.find(UnifiedBigDecimalField.class).withLabel("Quantity").getValue())
                .isEqualByComparingTo("1");
        page.find(UnifiedBigDecimalField.class).withLabel("Unit price")
                .setValue(new BigDecimal("12.50"));
        assertUiSays(page, "Total");
        assertUiSays(page, "€12.50");

        // Raising the quantity recomputes the total before anything is saved.
        page.find(UnifiedBigDecimalField.class).withLabel("Quantity").setValue(new BigDecimal("3"));
        assertUiSays(page, "€37.50");
    }

    @Test
    void lineEditorLaysItsFieldsOutAsTheDesignsTwoColumnGrid(UnifiedPage page) {
        // docs/design/components/line-editor-dialog.md § Layout. Everything spans
        // both columns except unit price/quantity, which pair, and VAT rate, which
        // is drawn half width with the cell beside it deliberately empty.
        page.navigate(ReportDetailView.class);
        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();

        UnifiedDialog dialog = page.find(UnifiedDialog.class);
        assertThat(dialog.element().getProperty("width")).isEqualTo("26rem");

        UnifiedGenericComponent form = dialog.findComponent(FormLayout.class);
        assertThat(form).exists();
        // The steps and spacings are server-side configuration with no stable DOM
        // reading across Testing Tools; checked where the component is at hand.
        form.tryUnwrap(FormLayout.class).ifPresent(layout -> {
            // ResponsiveStep exposes no getters, only toJson() — so compare the
            // serialised steps against the two the spec prescribes.
            assertThat(layout.getResponsiveSteps())
                    .extracting(step -> step.toJson().toString())
                    .containsExactly(
                            new FormLayout.ResponsiveStep("0", 1).toJson().toString(),
                            new FormLayout.ResponsiveStep("24rem", 2).toJson().toString());
            assertThat(layout.getColumnSpacing()).isEqualTo("var(--em-section-gap)");
            assertThat(layout.getRowSpacing()).isEqualTo("var(--em-card-padding)");
        });

        assertThat(colspan(dialog.find(UnifiedComboBox.class).withLabel("Expense type"))).isEqualTo(2);
        assertThat(colspan(bigDecimalField(page, "Unit price"))).isEqualTo(1);
        assertThat(colspan(bigDecimalField(page, "Quantity"))).isEqualTo(1);
        assertThat(colspan(dialog.find(UnifiedComboBox.class).withLabel("VAT rate"))).isEqualTo(1);
        assertThat(colspan(dialog.find(UnifiedTextArea.class).withLabel("Comment"))).isEqualTo(2);
        // The total row and the receipt block are grid children too, both full width.
        assertThat(colspan(dialog.find(UnifiedHorizontalLayout.class)
                .withClassName("line-total-row"))).isEqualTo(2);
        assertThat(colspan(dialog.find(UnifiedVerticalLayout.class)
                .where(block -> !block.findChildren("vaadin-upload").isEmpty()))).isEqualTo(2);
    }

    /** A form-layout child's column span: its {@code colspan} attribute, 1 when unset. */
    private static int colspan(com.vaadin.testing.unified.UnifiedWrapper child) {
        String value = child.element().getAttribute("colspan");
        return value == null || value.isBlank() ? 1 : Integer.parseInt(value.strip());
    }

    @Test
    void lineEditorUsesTheDesignsFieldTypesAndCopy(UnifiedPage page) {
        // Delta items 4, 9, 10, 11 of #175, plus the component mapping: the frame
        // hides the header close button, a third footer button and a checkbox.
        page.navigate(ReportDetailView.class);
        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();

        var comment = page.find(UnifiedTextArea.class).withLabel("Comment");
        assertThat(comment).hasProperty("minRows", 2);
        assertThat(comment).hasProperty("maxlength", 500);

        assertThat(bigDecimalField(page, "Unit price").getHelperText()).isEqualTo("Gross, each");
        assertUiSays(page, "Total");
        assertUiDoesNotSay(page, "Line total");

        assertThat(page.find(UnifiedDialog.class).find(UnifiedButton.class).withText("Save")).exists();
        assertThat(page.find(UnifiedButton.class).withText("Save expense").visible()).doesNotExist();
        assertThat(page.find(UnifiedDialog.class).find(UnifiedCheckbox.class).visible()).doesNotExist();
    }

    @Test
    void lineEditorShowsNoReceiptStatusUntilOneIsAttached(UnifiedPage page) {
        // Delta item 12: the empty state carries no status text — the Upload control
        // already says it, and the design draws none.
        page.navigate(ReportDetailView.class);
        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();

        assertUiDoesNotSay(page, "No receipt attached");
        assertThat(page.find(UnifiedButton.class).withText("Remove").visible()).doesNotExist();

        page.find(UnifiedUpload.class).upload("taxi.jpg", "image/jpeg", jpegBytes());

        assertUiSays(page, "taxi.jpg");
        assertThat(page.find(UnifiedButton.class).withText("Remove")).exists();
    }

    @Test
    void lineEditorPutsTheUploadGlyphInTheDropLabelNotOnTheButton(UnifiedPage page) {
        // Delta item 13, and iconography.md's Lucide-only rule (#163): the drawn
        // anatomy slots the glyph beside the drop label and leaves the button plain.
        page.navigate(ReportDetailView.class);
        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();

        UnifiedUpload upload = page.find(UnifiedDialog.class).find(UnifiedUpload.class);
        UnifiedButton button = page.find(UnifiedDialog.class).find(UnifiedButton.class)
                .withText("Upload receipt…");
        assertThat(button).exists();
        // Plain text: the label's Text node and nothing else — no icon slot.
        assertThat(button.element().findDescendants("vaadin-icon")).isEmpty();
        // The one glyph in the upload is the drop label's, and it is Lucide's upload.
        assertThat(upload.element().findDescendants("vaadin-icon"))
                .extracting(icon -> icon.getProperty("symbol"))
                .containsExactly(LucideIcon.UPLOAD.glyph());
    }

    @Test
    void aQuantityLinePersistsItsUnitPriceAndTotalsTheProduct(UnifiedPage page) {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();
        page.find(UnifiedComboBox.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        page.find(UnifiedComboBox.class).withLabel("VAT rate").selectItem("25.5 %");
        page.find(UnifiedBigDecimalField.class).withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        page.find(UnifiedBigDecimalField.class).withLabel("Quantity").setValue(new BigDecimal("3"));
        saveExpense(page);

        // The card shows the qty × unit = gross breakdown and the live total bar
        // reflects the product, both before the report is saved.
        assertSays(page, "3 × €100.00 = €300.00");
        assertSays(page, "€300.00");

        page.find(UnifiedButton.class).withText("Save").click();

        var line = service.findMine(id).lines().getFirst();
        assertThat(line.amount()).isEqualByComparingTo("100.00");     // unit price
        assertThat(line.quantity()).isEqualByComparingTo("3.00");
        assertThat(service.findMine(id).total()).isEqualByComparingTo("300.00");
    }

    @Test
    void aQuantityOneCardShowsNoQuantityBreakdown(UnifiedPage page) {
        // The feature is invisible until used (ADR-0023).
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "100.00");
        page.navigate(ReportDetailView.class, id);

        assertSays(page, "€100.00");
        assertDoesNotSay(page, "1 × €100.00");
    }

    @Test
    void aNonPositiveQuantityIsBlockedWithAnErrorSummary(UnifiedPage page) {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();
        page.find(UnifiedComboBox.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        page.find(UnifiedComboBox.class).withLabel("VAT rate").selectItem("25.5 %");
        page.find(UnifiedBigDecimalField.class).withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        page.find(UnifiedBigDecimalField.class).withLabel("Quantity").setValue(BigDecimal.ZERO);
        // Always-enabled Save (ADR-0020): the click is allowed, the reason shows.
        saveExpense(page);

        assertUiSays(page, "Quantity must be greater than zero");
        assertThat(service.findMine(id).lines()).isEmpty();
    }

    @Test
    void editingALineLoadsAndUpdatesItsQuantity(UnifiedPage page) {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "50.00");
        page.navigate(ReportDetailView.class, id);

        // Open the existing (quantity-1) line and give it a quantity. The card body
        // itself carries the click listener (no Edit button), so click that.
        openLineCardEditor(page);
        assertThat(page.find(UnifiedBigDecimalField.class).withLabel("Quantity").getValue())
                .isEqualByComparingTo("1");
        page.find(UnifiedBigDecimalField.class).withLabel("Quantity").setValue(new BigDecimal("4"));
        saveExpense(page);
        page.find(UnifiedButton.class).withText("Save").click();

        var line = service.findMine(id).lines().getFirst();
        assertThat(line.amount()).isEqualByComparingTo("50.00");
        assertThat(line.quantity()).isEqualByComparingTo("4.00");
        assertThat(service.findMine(id).total()).isEqualByComparingTo("200.00");
    }

    @Test
    void lineEditorKeepsSaveEnabledAndBlocksAnEmptyLine(UnifiedPage page) {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();
        // Always-enabled Save (ADR-0020): clicking it with nothing filled must
        // not add a line — validation blocks it and the dialog stays open.
        saveExpense(page);

        // Dialog is still open (validation blocked the save) and its Save is still
        // enabled — never a disabled submit.
        assertThat(page.find(UnifiedDialog.class)).exists();
        assertThat(page.find(UnifiedDialog.class).find(UnifiedButton.class).withText("Save")).isEnabled();
        // … and no line was added: the empty state still shows, nothing persisted.
        assertSays(page, "No expenses yet");
        assertThat(service.findMine(id).lines()).isEmpty();
    }

    @Test
    void submittingADraftWithALineLocksItReadOnly(UnifiedPage page) {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        page.find(UnifiedButton.class).withText("Submit for approval").click(); // opens the confirm dialog
        page.find(UnifiedButton.class).withText("Submit report").click();       // confirms

        // The report is now SUBMITTED and read-only to the owner: the editing
        // affordances are gone and the status is shown as text (ADR-0020).
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.SUBMITTED);
        assertSays(page, "Submitted");
        assertThat(page.find(UnifiedButton.class).withText("Save").visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withText("Submit for approval").visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Add expense").visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withText("Delete").visible()).doesNotExist();
    }

    @Test
    void submittingAnEmptyDraftShowsTheReasonAndStaysEnabled(UnifiedPage page) {
        var id = seedReport(LocalDate.of(2026, 7, 1), "empty");
        page.navigate(ReportDetailView.class, id);

        // Always-enabled Submit (ADR-0020): a zero-line submit is blocked with the
        // reason in the top-of-form error summary, not a silent no-op. The report
        // date is valid, so the confirm dialog opens; the domain guard fires on
        // confirm and the atomic save+submit rolls back (issue #81).
        page.find(UnifiedButton.class).withText("Submit for approval").click();
        page.find(UnifiedButton.class).withText("Submit report").click();

        assertSays(page, "at least one line");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
        // Submit button is still there (never disabled) so the owner can retry.
        assertThat(page.find(UnifiedButton.class).withText("Submit for approval")).exists();
    }

    // --- Submit saves the current state + confirmation dialog (issue #81) ---

    @Test
    void submittingPersistsAReportLevelEditThatWasNotSavedFirst(UnifiedPage page) {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        // Edit a report-level field but never click Save — the edit lives only in
        // the working copy until the submit persists it (issue #81).
        page.find(UnifiedTextArea.class).withLabel("Additional Info")
                .setValue("edited but not saved");

        page.find(UnifiedButton.class).withText("Submit for approval").click(); // opens the confirm dialog
        page.find(UnifiedButton.class).withText("Submit report").click();       // confirms

        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.additionalInformation()).isEqualTo("edited but not saved");
    }

    @Test
    void submittingPersistsALineAddedButNotSavedFirst(UnifiedPage page) {
        // A seed with no lines: before issue #81 a line added in the dialog but not
        // Saved was lost on submit, so the submit failed the "≥1 line" guard. Now
        // the submit saves the pending line first, so it succeeds.
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();
        page.find(UnifiedComboBox.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        page.find(UnifiedComboBox.class).withLabel("VAT rate").selectItem("25.5 %");
        page.find(UnifiedBigDecimalField.class).withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        saveExpense(page);

        page.find(UnifiedButton.class).withText("Submit for approval").click();
        page.find(UnifiedButton.class).withText("Submit report").click();

        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.lines()).hasSize(1);
        assertThat(reloaded.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void cancellingTheSubmitConfirmationLeavesItAnEditableDraft(UnifiedPage page) {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        page.find(UnifiedButton.class).withText("Submit for approval").click(); // opens the dialog
        page.find(UnifiedButton.class).withText("Cancel").click();              // backs out

        // Nothing submitted: still an editable DRAFT with both actions offered.
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
        assertThat(page.find(UnifiedButton.class).withText("Submit for approval")).exists();
        assertThat(page.find(UnifiedButton.class).withText("Save")).exists();
    }

    @Test
    void submittingWithNoReportDateBlocksBeforeConfirming(UnifiedPage page) {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        // Clearing the required date must block the submit at validation — the
        // confirm dialog never opens and nothing transitions.
        page.find(UnifiedDatePicker.class).withLabel("Report date").clear();
        page.find(UnifiedButton.class).withText("Submit for approval").click();

        assertThat(page.find(UnifiedButton.class).withText("Submit report").visible()).doesNotExist();
        assertSays(page, "Report date is required");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
    }

    @Test
    void aRejectedReportShowsTheRealReasonRejecterAndDateToTheOwner(UnifiedPage page) {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100",
                "Please attach the hotel receipt.");
        page.navigate(ReportDetailView.class, id);

        // The owner's callout carries the real reason, who rejected it (the seeded
        // admin), and when — replacing the placeholder note. The reason and the
        // rejecter also appear in the status-history audit trail below.
        assertSays(page, "Rejected — changes requested", "Please attach the hotel receipt.", "Expense Admin", "14 Jul 2026");
        assertSays(page, "Status history");
        // A rejected report is editable again (owner can resubmit), so Save shows.
        assertThat(page.find(UnifiedButton.class).withText("Save")).exists();
    }

    // --- Resubmit a rejected report (Phase 5.5) ---

    @Test
    void aRejectedReportOffersResubmitNotSubmit(UnifiedPage page) {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Fix the total.");
        page.navigate(ReportDetailView.class, id);

        // The forward action is relabelled "Resubmit" on a REJECTED report; the
        // first-submit label is gone (same button, flipped by status).
        assertThat(page.find(UnifiedButton.class).withText("Resubmit")).exists();
        assertThat(page.find(UnifiedButton.class).withText("Submit for approval").visible()).doesNotExist();
    }

    @Test
    void resubmitIsNotOfferedOnDraftSubmittedOrApproved(UnifiedPage page) {
        // DRAFT offers the first-submit label, never "Resubmit".
        var draft = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, draft);
        assertThat(page.find(UnifiedButton.class).withText("Submit for approval")).exists();
        assertThat(page.find(UnifiedButton.class).withText("Resubmit").visible()).doesNotExist();

        // SUBMITTED is read-only — no forward action of either label.
        var submitted = seedSubmittedReport(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, submitted);
        assertThat(page.find(UnifiedButton.class).withText("Resubmit").visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withText("Submit for approval").visible()).doesNotExist();

        // APPROVED is terminal and read-only — likewise no forward action.
        var approved = seedApprovedReport(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, approved);
        assertThat(page.find(UnifiedButton.class).withText("Resubmit").visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withText("Submit for approval").visible()).doesNotExist();
    }

    @Test
    void resubmittingARejectedReportSendsItBackToSubmittedAndLocksItReadOnly(UnifiedPage page) {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Fix the total.");
        page.navigate(ReportDetailView.class, id);

        page.find(UnifiedButton.class).withText("Resubmit").click();         // opens the confirm dialog
        page.find(UnifiedButton.class).withText("Resubmit report").click();  // confirms

        // Back through the queue: REJECTED → SUBMITTED, and read-only to the owner
        // again (the editing affordances and the forward action are gone).
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.SUBMITTED);
        assertSays(page, "Submitted");
        assertThat(page.find(UnifiedButton.class).withText("Resubmit").visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withText("Save").visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Add expense").visible()).doesNotExist();
    }

    @Test
    void resubmittingPersistsAnEditThatWasNotSavedFirst(UnifiedPage page) {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Add detail.");
        page.navigate(ReportDetailView.class, id);

        // Address the feedback but don't click Save — the resubmit must save it
        // as part of the transition (issue #81).
        page.find(UnifiedTextArea.class).withLabel("Additional Info")
                .setValue("addressed the feedback");

        page.find(UnifiedButton.class).withText("Resubmit").click();
        page.find(UnifiedButton.class).withText("Resubmit report").click();

        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.additionalInformation()).isEqualTo("addressed the feedback");
    }

    @Test
    void resubmittingAnEmptyRejectedReportShowsTheReasonAndStaysEnabled(UnifiedPage page) {
        var id = seedEmptyRejectedReport(LocalDate.of(2026, 7, 1), "Fix the total.");
        page.navigate(ReportDetailView.class, id);

        // Always-enabled Resubmit (ADR-0020): a zero-line resubmit is blocked with
        // the reason in the top-of-form error summary, not a silent no-op. The
        // confirm dialog opens (the date is valid); the domain guard fires on
        // confirm and the atomic save+resubmit rolls back (issue #81).
        page.find(UnifiedButton.class).withText("Resubmit").click();
        page.find(UnifiedButton.class).withText("Resubmit report").click();

        assertSays(page, "at least one line");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.REJECTED);
        // The button is still there (never disabled) so the owner can add a line and retry.
        assertThat(page.find(UnifiedButton.class).withText("Resubmit")).exists();
    }

    @Test
    void anAlreadySubmittedReportOpensReadOnly(UnifiedPage page) {
        var id = seedSubmittedReport(LocalDate.of(2026, 7, 1), "80.00");
        page.navigate(ReportDetailView.class, id);

        assertSays(page, "Submitted");
        assertThat(page.find(UnifiedButton.class).withText("Save").visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withText("Submit for approval").visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withText("Delete").visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Add expense").visible()).doesNotExist();
        // The line is shown but carries no ⋮ menu at all — not a disabled one.
        assertThat(page.find(UnifiedMenuBar.class).withClassName("row-action-menu").visible()).doesNotExist();
    }

    @Test
    void uploadingAReceiptOnAnUnsavedReportPersistsItOnFirstSave(UnifiedPage page) {
        // Brand-new, never-saved report: the receipt attaches at any time, no
        // "save first" gate (ADR-0021 overrides ADR-0019's save-before-attach).
        page.navigate(ReportDetailView.class);

        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();
        page.find(UnifiedComboBox.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        page.find(UnifiedComboBox.class).withLabel("VAT rate").selectItem("25.5 %");
        page.find(UnifiedBigDecimalField.class).withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        // Drive the real UploadHandler: bytes are validated server-side by magic
        // bytes and buffered in memory (the browser mime is not trusted).
        page.find(UnifiedUpload.class).upload("taxi.jpg", "image/jpeg", jpegBytes());
        saveExpense(page);

        // The card reflects the buffered receipt before the report is even saved —
        // as an image thumbnail (its filename is the accessible name), matching how
        // a persisted receipt renders (issue #89).
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Preview receipt: taxi.jpg")).exists();

        page.find(UnifiedButton.class).withText("Save").click();

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        var line = service.findMine(mine.getFirst().id()).lines().getFirst();
        assertThat(line.hasReceipt()).isTrue();
        assertThat(line.receiptFilename()).isEqualTo("taxi.jpg");
        assertThat(line.receiptContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void aMislabeledUploadIsRejectedAndNothingIsBuffered(UnifiedPage page) {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();
        page.find(UnifiedComboBox.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        page.find(UnifiedComboBox.class).withLabel("VAT rate").selectItem("25.5 %");
        page.find(UnifiedBigDecimalField.class).withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        // A text file renamed to .jpg: magic-byte check rejects it at upload; the
        // control is never disabled, the reason is surfaced instead (ADR-0020).
        page.find(UnifiedUpload.class).upload("fake.jpg", "image/jpeg", "not an image".getBytes());
        saveExpense(page);
        page.find(UnifiedButton.class).withText("Save").click();

        // The line saved, but with no receipt — the rejected bytes were dropped.
        assertThat(service.findMine(id).lines().getFirst().hasReceipt()).isFalse();
    }

    @Test
    void aSubmittedReportShowsTheReceiptSummaryButOffersNoUpload(UnifiedPage page) {
        var id = seedSubmittedReportWithReceipt(LocalDate.of(2026, 7, 1), "50.00",
                "hotel.jpg");
        page.navigate(ReportDetailView.class, id);

        // The receipt is viewable (read path, ADR-0021): its image preview shows
        // even on a read-only report. But every mutation surface is gone — no Add
        // expense to open the editor, so the receipt cannot be changed.
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Preview receipt: hotel.jpg")).exists();
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Add expense").visible()).doesNotExist();
        assertThat(page.find(UnifiedMenuBar.class).withClassName("row-action-menu").visible()).doesNotExist();
    }

    @Test
    void aSavedImageReceiptRendersAnEnlargeablePreview(UnifiedPage page) {
        // A persisted image receipt: the card shows a keyboard-operable thumbnail
        // button (accessible name) that streams from the DB and enlarges in a
        // dialog (ADR-0021 / ADR-0020) — visible even on a read-only report.
        var id = seedSubmittedReportWithReceipt(LocalDate.of(2026, 7, 1), "50.00",
                "hotel.jpg");
        page.navigate(ReportDetailView.class, id);

        assertThat(page.find(UnifiedButton.class).withAccessibleName("Preview receipt: hotel.jpg")).exists();
        assertThat(page.findComponent(Image.class)).exists();
    }

    @Test
    void aSavedPdfReceiptOffersAnOpenAffordance(UnifiedPage page) {
        var id = seedReportWithReceipt(LocalDate.of(2026, 7, 1), "70.00",
                "invoice.pdf", pdfBytes());
        page.navigate(ReportDetailView.class, id);

        // A PDF is not thumbnailed. On a row it is the design's chip — a paperclip
        // and the filename — and activating it still opens the browser's PDF viewer
        // in a new tab (ADR-0021); the "open" verb moved to the accessible name.
        assertSays(page, "invoice.pdf");
        assertThat(page.findComponent(Anchor.class).withClassName("expense-row-attachment")
                .withAccessibleName("Open receipt: invoice.pdf")).exists();
    }

    @Test
    void anUnsavedImageAttachmentPreviewsFromBufferedBytes(UnifiedPage page) {
        // Brand-new report, receipt attached but nothing saved yet: the editor
        // previews the buffered bytes directly (no DB round-trip, no receipt id).
        page.navigate(ReportDetailView.class);

        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();
        page.find(UnifiedComboBox.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        page.find(UnifiedComboBox.class).withLabel("VAT rate").selectItem("25.5 %");
        page.find(UnifiedBigDecimalField.class).withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        page.find(UnifiedUpload.class).upload("taxi.jpg", "image/jpeg", jpegBytes());

        // The preview affordance appears in the still-open dialog, before any save.
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Preview receipt: taxi.jpg")).exists();
    }

    @Test
    void anUnsavedImageAttachmentPreviewsOnTheCardBeforeSave(UnifiedPage page) {
        // Issue #89: after the line editor closes, the card must show the image
        // thumbnail from the buffered bytes right away — not only after the report
        // is saved (previously the card showed just a "📎 filename" chip until the
        // receipt was persisted and had an id).
        page.navigate(ReportDetailView.class);

        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();
        page.find(UnifiedComboBox.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        page.find(UnifiedComboBox.class).withLabel("VAT rate").selectItem("25.5 %");
        page.find(UnifiedBigDecimalField.class).withLabel("Unit price")
                .setValue(new BigDecimal("100"));
        page.find(UnifiedUpload.class).upload("taxi.jpg", "image/jpeg", jpegBytes());
        saveExpense(page);

        // Dialog is closed; the preview button now belongs to the line card, and it
        // renders before the report has ever been saved (no receipt id yet).
        assertThat(page.find(UnifiedDialog.class).visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Preview receipt: taxi.jpg")).exists();
        assertThat(page.findComponent(Image.class)).exists();
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
    void insertingADomesticTripPreviewsGeneratesAndPersistsThePerDiem(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();
        page.find(UnifiedDateTimePicker.class).withLabel("Departure").setValue(DEP);
        page.find(UnifiedDateTimePicker.class).withLabel("Return").setValue(DEP.plusHours(11));
        page.find(UnifiedComboBox.class).withLabel("Destination country")
                .selectItem("Finland (domestic)");
        page.find(UnifiedTextField.class).withLabel("Destinations").setValue("Helsinki");
        page.find(UnifiedTextField.class).withLabel("Travel purpose").setValue("Client visit");

        // The per-diem previews live as the dates are filled — no separate compute
        // step (11 h → one full day €54). The preview lives in the dialog overlay
        // (attached to the UI, not the view), so assert against the whole UI tree.
        assertUiSays(page, "Per diem allowance (full day)", "€54.00", "Trip total");

        saveTrip(page);

        // The trip card + the live "Per diem allowance" subtotal row appear before
        // the report is even saved.
        assertSays(page, "Client visit", "Helsinki, Finland", "Per diem allowance", "€54.00");

        page.find(UnifiedButton.class).withText("Save").click();

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        var loaded = service.findMine(mine.getFirst().id());
        assertThat(loaded.travels()).hasSize(1);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(loaded.total()).isEqualByComparingTo("54.00");
    }

    @Test
    void insertingAForeignTripCostsThePerDiemAgainstTheDestinationCountry(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();
        page.find(UnifiedDateTimePicker.class).withLabel("Departure").setValue(DEP);
        page.find(UnifiedDateTimePicker.class).withLabel("Return").setValue(DEP.plusHours(11));
        // The picker lists the seeded foreign countries alongside Finland; pick one.
        var country = page.find(UnifiedComboBox.class).withLabel("Destination country");
        assertThat(country.getItems())
                .contains("Finland (domestic)", "Germany", "Sweden");
        country.selectItem("Germany");
        page.find(UnifiedTextField.class).withLabel("Destinations").setValue("Berlin");
        page.find(UnifiedTextField.class).withLabel("Travel purpose").setValue("Conference");

        // Costed against Germany's €71.00/day, not the Finnish €54.00.
        assertUiSays(page, "Per diem allowance (full day)", "€71.00", "Germany");

        saveTrip(page);
        // The chosen country shows on the Trip & Allowance card ("destinations, country").
        assertSays(page, "Berlin, Germany", "€71.00");

        page.find(UnifiedButton.class).withText("Save").click();

        var loaded = service.findMine(service.listMine().getFirst().id());
        assertThat(loaded.travels().getFirst().country()).isEqualTo("Germany");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("71.00");
    }

    @Test
    void aTripCannotBeSavedWithNoDestinationCountryChosen(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();
        page.find(UnifiedDateTimePicker.class).withLabel("Departure").setValue(DEP);
        page.find(UnifiedDateTimePicker.class).withLabel("Return").setValue(DEP.plusHours(11));
        page.find(UnifiedTextField.class).withLabel("Destinations").setValue("Helsinki");
        page.find(UnifiedTextField.class).withLabel("Travel purpose").setValue("Client visit");
        // Country left unchosen: Save surfaces a clear message and generates nothing.
        saveTrip(page);

        assertUiSays(page, "Destination country is required");
        assertThat(page.find(UnifiedDialog.class).find(UnifiedButton.class).withText("Save")).exists();
        assertThat(page.find(UnifiedSpan.class).withText("Per diem allowance").visible()).doesNotExist();
    }

    @Test
    void insertingATripWithKmMealParkingShowsEachSubtotalAndFoldsParkingIntoNetVat(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();
        page.find(UnifiedDateTimePicker.class).withLabel("Departure").setValue(DEP);
        page.find(UnifiedDateTimePicker.class).withLabel("Return").setValue(DEP.plusHours(11));
        page.find(UnifiedComboBox.class).withLabel("Destination country")
                .selectItem("Finland (domestic)");
        page.find(UnifiedTextField.class).withLabel("Destinations").setValue("Helsinki");
        page.find(UnifiedTextField.class).withLabel("Travel purpose").setValue("Client visit");
        page.find(UnifiedBigDecimalField.class).withLabel("Kilometre allowance (km)")
                .setValue(new BigDecimal("120"));
        // A meal allowance is only paid when no per-diem applies (issue #93), so
        // clicking it marks the trip not eligible — the per-diem line drops out and
        // the meal-allowance line takes its place.
        page.find(UnifiedCheckbox.class).withLabel("Pay meal allowance?").check();
        page.find(UnifiedBigDecimalField.class).withLabel("Parking fees (€)")
                .setValue(new BigDecimal("12.00"));

        // The remaining outputs preview live in the dialog (120 km × €0.55 = €66.00,
        // etc.); no per-diem, since the trip is now not eligible for one.
        assertUiSays(page, "Kilometre allowance", "€66.00", "Meal allowance", "€13.50", "Parking", "€12.00");
        assertThat(page.find(UnifiedDialog.class).find(UnifiedSpan.class)
                .withText("Per diem allowance").visible()).doesNotExist();

        saveTrip(page);

        // The two new tax-free subtotal rows are visible; parking is not a subtotal.
        assertThat(page.find(UnifiedSpan.class).withText("Kilometre allowance")).exists();
        assertThat(page.find(UnifiedSpan.class).withText("Meal allowance")).exists();
        assertThat(page.find(UnifiedSpan.class).withText("Per diem allowance").visible()).doesNotExist();
        assertSays(page, "€66.00", "€13.50");

        page.find(UnifiedButton.class).withText("Save").click();

        var loaded = service.findMine(service.listMine().getFirst().id());
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("0.00");
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("66.00");
        assertThat(loaded.mealTotal()).isEqualByComparingTo("13.50");
        // Parking folds into Net/VAT; grand total sums it all.
        assertThat(loaded.netTotal()).isEqualByComparingTo("9.56");
        assertThat(loaded.total()).isEqualByComparingTo("91.50");
    }

    @Test
    void theKilometreLineCardShowsTheKmTimesRateBreakdown(UnifiedPage page) {
        // 12.5 km × the seeded 2026 rate (€0.550/km) = €6.88 — the km line reads like
        // a multi-unit manual card (ADR-0023), and the euros are unchanged.
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("12.5"), false, BigDecimal.ZERO);
        page.navigate(ReportDetailView.class, id);

        assertSays(page, "12.5 × €0.55 = €6.88");
        // The flat per-diem line on the same trip carries no breakdown (quantity 1).
        assertSays(page, "€54.00");
        assertDoesNotSay(page, "1 × €54.00");

        // Totals are untouched: the km subtotal is the product, the grand total sums.
        var loaded = service.findMine(id);
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("6.88");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(loaded.total()).isEqualByComparingTo("60.88");
        assertThat(loaded.netTotal()).isEqualByComparingTo("0.00");
        assertThat(loaded.vatTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void reCostingATripUpdatesItsKilometreCardWithoutLeavingAStaleOne(UnifiedPage page) {
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("12.5"), false, BigDecimal.ZERO);
        page.navigate(ReportDetailView.class, id);

        // Re-cost the trip through its editor: 12.5 km → 120 km.
        clickRowAction(page, TRIP, "Edit");
        page.find(UnifiedBigDecimalField.class).withLabel("Kilometre allowance (km)")
                .setValue(new BigDecimal("120"));
        saveTrip(page);

        assertSays(page, "120 × €0.55 = €66.00");
        assertDoesNotSay(page, "12.5 × €0.55");

        page.find(UnifiedButton.class).withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("66.00");
        assertThat(loaded.travels().getFirst().generatedLines()).hasSize(2);
    }

    @Test
    void aTripWithoutKmOrMealHidesThoseSubtotalRows(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);

        // Per-diem-only trip: its row shows, but the km/meal rows stay hidden.
        assertThat(page.find(UnifiedSpan.class).withText("Per diem allowance")).exists();
        assertThat(page.find(UnifiedSpan.class).withText("Kilometre allowance").visible()).doesNotExist();
        assertThat(page.find(UnifiedSpan.class).withText("Meal allowance").visible()).doesNotExist();
    }

    @Test
    void aTripListsItsGeneratedLinesAndAReceiptCanBeAttachedToOne(UnifiedPage page) {
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00"));
        page.navigate(ReportDetailView.class, id);

        // Each earned line is listed under the trip with an attach affordance.
        assertThat(rowActions(page, "Per diem allowance (full day)"))
                .contains("Add receipt");
        assertThat(rowActions(page, "Parking")).contains("Add receipt");

        // Attach a receipt to the parking line via its focused editor.
        clickRowAction(page, "Parking", "Add receipt");
        page.find(UnifiedUpload.class).upload("parking.jpg", "image/jpeg", jpegBytes());
        page.find(UnifiedButton.class).withText("Save receipt").click();

        // Issue #89: the buffered receipt previews as a thumbnail on the row right
        // away — before the report is even saved — not just as a filename chip.
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Preview receipt: parking.jpg")).exists();
        assertThat(page.findComponent(Image.class)).exists();

        page.find(UnifiedButton.class).withText("Save").click();

        var parking = service.findMine(id).travels().getFirst()
                .generatedLine(com.vaadin.expensemanager.report.domain.GeneratedLineKind
                        .PARKING).orElseThrow();
        assertThat(parking.hasReceipt()).isTrue();
        assertThat(parking.receiptFilename()).isEqualTo("parking.jpg");
        assertThat(parking.receiptId()).isNotNull();
    }

    @Test
    void editingATripWithFreeLunchRegeneratesAHalvedPerDiem(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);

        clickRowAction(page, TRIP, "Edit");                    // the trip row's menu
        dailyAllowance(page).selectItem("Halved, free lunch provided");
        saveTrip(page);

        // Live per-diem halves to €27.00; a save regenerates the line.
        assertSays(page, "€27.00");
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("27.00");
    }

    @Test
    void aTripEarningAPartialDayListsTwoPerDiemCardsUnderOneSubtotal(UnifiedPage page) {
        // Issue #124: 31 h → a full-day card (1 × €54.00) and a partial-day card
        // (1 × €25.00), each labelled by the day it prices, both folded into the single
        // "Per diem allowance" subtotal row (€79.00).
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(31));
        page.navigate(ReportDetailView.class, id);

        assertSays(page, "Per diem allowance (full day)", "€54.00", "Per diem allowance (partial day)", "€25.00");
        // One subtotal row for both lines, and it is the sum.
        assertThat(page.find(UnifiedSpan.class).withText("Per diem allowance")).exists();
        assertSays(page, "€79.00");

        var loaded = service.findMine(id);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("79.00");
        assertThat(loaded.total()).isEqualByComparingTo("79.00");
        assertThat(loaded.travels().getFirst().generatedLines()).hasSize(2);
    }

    @Test
    void aMultiDayPerDiemCardShowsTheDaysTimesRateBreakdown(UnifiedPage page) {
        // 55 h → 2 full days + 1 partial: the full-day card reads like an invoice line
        // (ADR-0023), the partial-day card stays a plain quantity-1 card.
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);

        assertSays(page, "2 × €54.00 = €108.00");
        assertDoesNotSay(page, "1 × €25.00");
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void reCostingATripDropsThePerDiemCardItNoLongerEarns(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(31));
        page.navigate(ReportDetailView.class, id);
        assertSays(page, "Per diem allowance (partial day)");

        // Shorten the trip to a whole 24 h through its editor: the partial-day card
        // disappears, the full-day one stays, and the subtotal follows live.
        clickRowAction(page, TRIP, "Edit");
        page.find(UnifiedDateTimePicker.class).withLabel("Return").setValue(DEP.plusHours(24));
        saveTrip(page);

        assertSays(page, "Per diem allowance (full day)");
        assertDoesNotSay(page, "Per diem allowance (partial day)");

        page.find(UnifiedButton.class).withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(loaded.travels().getFirst().generatedLines()).hasSize(1);
    }

    // --- Quantity Override on a generated line (ADR-0024, issue #131) ---
    //
    // The user corrects a travel-generated allowance line by changing its COUNT,
    // with a mandatory reason; the unit price stays statutory and server-computed.

    @Test
    void overridingTheFullDayCountRescalesTheLineTheSubtotalAndTheTotal(UnifiedPage page) {
        // 55 h → 2 full days (€108.00) + 1 partial day (€25.00) = €133.00.
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        assertSays(page, "€133.00");

        overrideCount(page, "Per diem allowance (full day)", 1, "the Wednesday was personal");

        // The row now reads the effective figures, badges the correction, gives the
        // reason and names what the calculator produced — before any save.
        assertSays(page, "Overridden", "Reason: the Wednesday was personal", "Calculated: 2 × €54.00 = €108.00");
        // 1 full day + the untouched partial day, live in the subtotal and total.
        assertSays(page, "€79.00");

        page.find(UnifiedButton.class).withText("Save").click();

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
    void anOverrideSurvivesSavingAndReloadingWithItsReason(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (full day)", 1, "the Wednesday was personal");
        page.find(UnifiedButton.class).withText("Save").click();

        // Reload the persisted report from scratch: the badge, the reason and the
        // statutory baseline are all still there.
        page.navigate(ReportDetailView.class, id);

        assertSays(page, "Overridden", "Reason: the Wednesday was personal", "Calculated: 2 × €54.00 = €108.00", "€79.00");
    }

    @Test
    void theMealAllowanceCountIsOverridableToo(UnifiedPage page) {
        // A not-eligible trip paying a meal allowance: one flat €13.50 line.
        var id = seedReportWithMealTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);

        overrideCount(page, "Meal allowance", 2, "two meals were taken");
        page.find(UnifiedButton.class).withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.mealTotal()).isEqualByComparingTo("27.00");
        assertThat(loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.MEAL).orElseThrow()
                .overrideReason()).isEqualTo("two meals were taken");
    }

    @Test
    void aBlankReasonIsRejectedInTheDialogsErrorSummaryAndChangesNothing(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);

        clickRowAction(page, "Per diem allowance (full day)", "Override");
        page.find(UnifiedIntegerField.class).withLabel("Count").setValue(1);
        page.find(UnifiedTextArea.class).withLabel("Reason for the override").setValue("   ");
        // Always-enabled submit (ADR-0020): the click is allowed, the reason shows.
        page.find(UnifiedButton.class).withText("Save override").click();

        assertUiSays(page, "A reason for the override is required.");
        // The dialog stays open and nothing was corrected.
        assertThat(page.find(UnifiedButton.class).withText("Save override")).exists();
        assertThat(page.find(UnifiedSpan.class).withText("Overridden").visible()).doesNotExist();
    }

    @Test
    void thePartialDayCountIsCappedAtOneWithTheReasonInTheErrorSummary(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);

        clickRowAction(page, "Per diem allowance (partial day)", "Override");
        page.find(UnifiedIntegerField.class).withLabel("Count").setValue(2);
        page.find(UnifiedTextArea.class).withLabel("Reason for the override").setValue("two leftovers");
        page.find(UnifiedButton.class).withText("Save override").click();

        assertUiSays(page, "one partial day");
        assertThat(page.find(UnifiedButton.class).withText("Save override")).exists();
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void theCountFieldTakesWholeNumbersAtOrAboveTheFloor(UnifiedPage page) {
        // Integrality is enforced at the widget (an IntegerField) and the floor with
        // it; the domain enforces both again on save. The tester refuses to set an
        // out-of-range value, so assert the constraint that makes that so (F-021).
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);

        clickRowAction(page, "Per diem allowance (full day)", "Override");

        assertThat(page.find(UnifiedIntegerField.class).withLabel("Count")).hasProperty("min", 0);
    }

    @Test
    void kilometreAndParkingLinesOfferNoOverride(UnifiedPage page) {
        // Their numbers are trip inputs with a single home — edited on the trip.
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), false,
                new BigDecimal("12.00"));
        page.navigate(ReportDetailView.class, id);

        assertThat(rowActions(page, "Per diem allowance (full day)")).contains("Override");
        assertThat(rowActions(page, "Kilometre allowance")).doesNotContain("Override");
        assertThat(rowActions(page, "Parking")).doesNotContain("Override");
    }

    @Test
    void resetToCalculatedRemovesTheOverrideWithoutTouchingTheTrip(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (full day)", 1, "the Wednesday was personal");
        page.find(UnifiedButton.class).withText("Save").click();

        clickRowAction(page, "Per diem allowance (full day)", "Reset to calculated");

        // Back to the statutory figure, live, and the badge is gone.
        assertSays(page, "€133.00");
        assertDoesNotSay(page, "Overridden");

        page.find(UnifiedButton.class).withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("133.00");
        assertThat(loaded.travels().getFirst().quantityOverrides()).isEmpty();
        // The trip itself was never edited.
        assertThat(loaded.travels().getFirst().returnAt()).isEqualTo(DEP.plusHours(55));
    }

    @Test
    void aSubmittedReportShowsAnOverrideReadOnlyWithNoWayToChangeIt(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (full day)", 1, "the Wednesday was personal");
        page.find(UnifiedButton.class).withText("Save").click();
        service.submit(id, service.findMine(id).version());

        page.navigate(ReportDetailView.class, id);

        // Editability falls out of the existing DRAFT/REJECTED gating — the badge,
        // reason and baseline still read, but every mutation surface is gone.
        assertSays(page, "Overridden", "Reason: the Wednesday was personal", "Calculated: 2 × €54.00 = €108.00");
        // A read-only report builds no menu at all, rather than a disabled trigger.
        assertThat(rowActions(page, "Per diem allowance (full day)")).isEmpty();
    }

    @Test
    void aRejectedReportCanStillBeCorrectedWithAnOverride(UnifiedPage page) {
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
        page.navigate(ReportDetailView.class, id);

        overrideCount(page, "Per diem allowance (full day)", 1, "the Wednesday was personal");
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("79.00");
    }

    /**
     * Drives the whole override flow for one generated line: opens the row's
     * override dialog, enters a count and a reason, and confirms.
     */
    private void overrideCount(UnifiedPage page, String lineLabel, int count, String reason) {
        clickRowAction(page, lineLabel, "Override");
        page.find(UnifiedIntegerField.class).withLabel("Count").setValue(count);
        page.find(UnifiedTextArea.class).withLabel("Reason for the override").setValue(reason);
        page.find(UnifiedButton.class).withText("Save override").click();
    }

    // --- Zero suppresses the line, with the receipt-destruction confirm (issue #132) ---
    //
    // A count of 0 drops the line — the only way to say "keep the full days, lose the
    // partial leftover". Because a dropped line takes its receipt with it (a DB-level
    // cascade, ADR-0024), the user is warned first and the file is named.

    @Test
    void aZeroCountRemovesTheLineWithoutPromptingWhenItCarriesNoReceipt(UnifiedPage page) {
        // 55 h → 2 full days (€108.00) + 1 partial day (€25.00) = €133.00.
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);

        overrideCount(page, "Per diem allowance (partial day)", 0, "the leftover was personal");

        // Nothing to destroy, so nothing to confirm — the correction applied at once.
        assertThat(page.find(UnifiedButton.class).withText("Remove line and receipt").visible()).doesNotExist();
        // The line is gone from the report; the full days are untouched and the
        // per-diem subtotal and total drop to just them.
        assertSays(page, "€108.00");
        assertDoesNotSay(page, "€133.00", "1 × €25.00");
        // What is left in its place says what was dropped and why, and offers the way
        // back — without which a zero override would be unreachable once made.
        assertSays(page, "Removed", "Removed from the report. Reason: the leftover was personal");
        assertThat(rowActions(page, "Per diem allowance (partial day)"))
                .containsExactly("Reset to calculated");

        page.find(UnifiedButton.class).withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL)).isEmpty();
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("108.00");
        assertThat(loaded.total()).isEqualByComparingTo("108.00");
    }

    @Test
    void suppressingALineCarryingAReceiptConfirmsFirstAndNamesTheFile(UnifiedPage page) {
        var id = seedMealTravelWithSavedReceipt(page, "lunch.jpg");
        page.navigate(ReportDetailView.class, id);

        overrideCount(page, "Meal allowance", 0, "no meal was taken after all");

        // Nothing has happened yet: the dialog names the line, names the file, and says
        // the receipt goes with it.
        assertUiSays(page, "removes the Meal allowance line", "lunch.jpg", "deleted with the line and cannot be recovered");
        assertThat(service.findMine(id).mealTotal()).isEqualByComparingTo("13.50");

        page.find(UnifiedButton.class).withText("Remove line and receipt").click();
        page.find(UnifiedButton.class).withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.travels().getFirst().generatedLine(GeneratedLineKind.MEAL))
                .isEmpty();
        assertThat(loaded.mealTotal()).isEqualByComparingTo("0.00");
        assertThat(loaded.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void cancellingTheReceiptWarningLeavesBothTheLineAndItsReceiptAlone(UnifiedPage page) {
        var id = seedMealTravelWithSavedReceipt(page, "lunch.jpg");
        page.navigate(ReportDetailView.class, id);

        overrideCount(page, "Meal allowance", 0, "no meal was taken after all");
        page.find(UnifiedButton.class).withText("Cancel").click();

        // The line still stands at its statutory figure, with its receipt attached and
        // no override recorded — cancelling is a full retreat, not a half-applied one.
        assertSays(page, "Meal allowance", "€13.50");
        assertDoesNotSay(page, "Removed");
        assertThat(rowActions(page, "Meal allowance")).contains("Edit receipt");

        var stillThere = service.findMine(id).travels().getFirst()
                .generatedLine(GeneratedLineKind.MEAL).orElseThrow();
        assertThat(stillThere.amount()).isEqualByComparingTo("13.50");
        assertThat(stillThere.hasReceipt()).isTrue();
        assertThat(stillThere.receiptFilename()).isEqualTo("lunch.jpg");
        assertThat(service.findMine(id).travels().getFirst().quantityOverrides())
                .isEmpty();
    }

    @Test
    void suppressingALineDropsItsBufferedReceiptSoNoStaleReferenceIsSaved(UnifiedPage page) {
        // The receipt is attached but the report not yet saved (ADR-0021 buffering), so
        // the doomed file lives only in the view's pending map. Suppressing the line
        // must prune it: on save the service would otherwise be handed a
        // GeneratedLineRef for a kind the trip no longer generates.
        var id = seedReportWithMealTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);
        clickRowAction(page, "Meal allowance", "Add receipt");
        page.find(UnifiedUpload.class).upload("lunch.jpg", "image/jpeg", jpegBytes());
        page.find(UnifiedButton.class).withText("Save receipt").click();

        overrideCount(page, "Meal allowance", 0, "no meal was taken");
        // A buffered receipt is just as destructible as a saved one — same warning.
        assertUiSays(page, "lunch.jpg");
        page.find(UnifiedButton.class).withText("Remove line and receipt").click();

        page.find(UnifiedButton.class).withText("Save").click();

        // The save went through (a stale ref would have failed it) and left nothing
        // behind: no line, no receipt, no error.
        assertThat(page.find(UnifiedSpan.class).withText("Something went wrong").visible()).doesNotExist();
        var loaded = service.findMine(id);
        assertThat(loaded.travels().getFirst().generatedLines()).isEmpty();
        assertThat(loaded.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void resetToCalculatedRestoresASuppressedLineAtItsStatutoryCount(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (partial day)", 0, "the leftover was personal");
        page.find(UnifiedButton.class).withText("Save").click();
        // Reload, so the reset acts on a persisted suppression rather than a live one.
        page.navigate(ReportDetailView.class, id);
        assertSays(page, "Removed from the report. Reason: the leftover was personal");

        clickRowAction(page, "Per diem allowance (partial day)", "Reset to calculated");

        assertSays(page, "Per diem allowance (partial day)", "€25.00", "€133.00");
        assertDoesNotSay(page, "Removed");

        page.find(UnifiedButton.class).withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.travels().getFirst().quantityOverrides()).isEmpty();
        assertThat(loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL).orElseThrow()
                .quantity()).isEqualByComparingTo("1.00");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void aSuppressedLineIsShownReadOnlyOnASubmittedReport(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (partial day)", 0, "the leftover was personal");
        page.find(UnifiedButton.class).withText("Save").click();
        service.submit(id, service.findMine(id).version());

        page.navigate(ReportDetailView.class, id);

        // The record of what was dropped and why still reads — an approver's question
        // about a missing partial day has an answer on the report — but the way back is
        // gone with every other mutation surface (the existing DRAFT/REJECTED gating).
        assertSays(page, "Removed from the report. Reason: the leftover was personal");
        assertThat(rowActions(page, "Per diem allowance (partial day)")).isEmpty();
    }

    /**
     * A DRAFT report with one meal-allowance trip whose line carries a <em>saved</em>
     * receipt — the state the receipt-destruction warning is about. Every click is
     * its own request with its own session, as in production (the class no longer
     * holds one session across the test, so no detaching is needed).
     */
    private Long seedMealTravelWithSavedReceipt(UnifiedPage page, String filename) {
        var id = seedReportWithMealTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);
        clickRowAction(page, "Meal allowance", "Add receipt");
        page.find(UnifiedUpload.class).upload(filename, "image/jpeg", jpegBytes());
        page.find(UnifiedButton.class).withText("Save receipt").click();
        page.find(UnifiedButton.class).withText("Save").click();
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
    void aTripEditThatMovesTheCalculatedCountConfirmsAndClearsTheOverride(UnifiedPage page) {
        // 55 h → 2 full days + 1 partial; the full days are overridden down to 1.
        var id = seedOverriddenTravel(page, DEP.plusHours(55));

        // Lengthen the trip to 79 h → 3 full days: the number the override corrected
        // has moved, so the correction cannot stand.
        editTripReturn(page, DEP.plusHours(79));

        assertThat(clearingConfirm(page).getHeaderTitle()).isEqualTo("Clear your override?");
        assertThat(dialogText(clearingConfirm(page))).contains(
                "Per diem allowance (full day): calculated 2 → 3 days.",
                "Your override (1 day — \"the Wednesday was personal\") "
                        + "will be cleared.");
        page.find(UnifiedButton.class).withText("Clear and save trip").click();

        // The line is back to the statutory count, live: 3 × €54.00 + the partial day.
        assertSays(page, "3 × €54.00 = €162.00", "€187.00");
        assertDoesNotSay(page, "Overridden", "the Wednesday was personal");

        page.find(UnifiedButton.class).withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.quantityOverrides()).isEmpty();
        assertThat(trip.returnAt()).isEqualTo(DEP.plusHours(79));
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("187.00");
    }

    @Test
    void cancellingTheClearingConfirmAbandonsTheTripSaveAndKeepsTheOverride(UnifiedPage page) {
        var id = seedOverriddenTravel(page, DEP.plusHours(55));

        editTripReturn(page, DEP.plusHours(79));
        page.find(UnifiedButton.class).withText("Keep editing").click();

        // A full retreat: the trip editor is still open on the user's edit, and behind
        // it the trip and its override are untouched (1 × €54.00 + €25.00 = €79.00).
        assertThat(page.find(UnifiedDialog.class).find(UnifiedButton.class).withText("Save")).exists();
        assertSays(page, "Overridden", "Reason: the Wednesday was personal", "€79.00");

        // Abandon the trip edit, then save the report: nothing of the edit survived.
        page.find(UnifiedButton.class).withText("Cancel").click();
        page.find(UnifiedButton.class).withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.returnAt()).isEqualTo(DEP.plusHours(55));
        assertThat(trip.quantityOverrides()).containsKey(GeneratedLineKind.PER_DIEM_FULL);
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("79.00");
    }

    @Test
    void editingOnlyThePurposeOrDestinationsNeverPromptsAndNeverClears(UnifiedPage page) {
        var id = seedOverriddenTravel(page, DEP.plusHours(55));

        clickRowAction(page, TRIP, "Edit");
        page.find(UnifiedTextField.class).withLabel("Travel purpose").setValue("Client visit (Acme)");
        page.find(UnifiedTextField.class).withLabel("Destinations").setValue("Helsinki, Espoo");
        saveTrip(page);

        // Nothing the calculation can see changed, so nothing is asked and nothing lost.
        assertThat(page.find(UnifiedButton.class).withText("Clear and save trip").visible()).doesNotExist();
        assertSays(page, "Overridden", "Reason: the Wednesday was personal", "€79.00");

        page.find(UnifiedButton.class).withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.purpose()).isEqualTo("Client visit (Acme)");
        assertThat(trip.quantityOverrides()
                .get(GeneratedLineKind.PER_DIEM_FULL).quantity())
                .isEqualByComparingTo("1.00");
    }

    @Test
    void aTimeChangeTheDayCountAbsorbsNeverPromptsAndNeverClears(UnifiedPage page) {
        var id = seedOverriddenTravel(page, DEP.plusHours(55));

        // 55 h → 55 h 15 min: still 2 full days and one leftover over the 6 h partial
        // threshold. The inputs moved; the counts the override corrects did not.
        editTripReturn(page, DEP.plusHours(55).plusMinutes(15));

        assertThat(page.find(UnifiedButton.class).withText("Clear and save trip").visible()).doesNotExist();
        assertSays(page, "Overridden", "Reason: the Wednesday was personal", "€79.00");

        page.find(UnifiedButton.class).withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.returnAt()).isEqualTo(DEP.plusHours(55).plusMinutes(15));
        assertThat(trip.quantityOverrides())
                .containsKey(GeneratedLineKind.PER_DIEM_FULL);
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("79.00");
    }

    @Test
    void clearingIsPerKindSoAnUnaffectedOverrideStands(UnifiedPage page) {
        var id = seedOverriddenTravel(page, DEP.plusHours(55));
        overrideCount(page, "Per diem allowance (partial day)", 1, "the leftover was worked");
        page.find(UnifiedButton.class).withText("Save").click();

        // 55 h → 31 h: the full days drop 2 → 1, but the trip still earns exactly one
        // partial day, so only the full-day override is invalidated.
        editTripReturn(page, DEP.plusHours(31));

        assertThat(clearingConfirm(page).getHeaderTitle()).isEqualTo("Clear your override?");
        assertThat(dialogText(clearingConfirm(page)))
                .contains("Per diem allowance (full day): calculated 2 → 1 day.")
                .doesNotContain("Per diem allowance (partial day)");
        page.find(UnifiedButton.class).withText("Clear and save trip").click();
        page.find(UnifiedButton.class).withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.quantityOverrides())
                .containsOnlyKeys(GeneratedLineKind.PER_DIEM_PARTIAL);
        assertThat(trip.generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL).orElseThrow()
                .overrideReason()).isEqualTo("the leftover was worked");
    }

    @Test
    void aMealCountChangeLeavesAPerDiemOverrideInPlace(UnifiedPage page) {
        // The other direction of per-kind clearing: a trip paying a meal allowance
        // (so earning no per-diem, issue #93) with an override on each kind — the
        // per-diem one dormant, since an override never conjures a line.
        var id = seedMealTravelOverriddenOnBothKinds();
        page.navigate(ReportDetailView.class, id);

        // Stop paying the meal allowance: its count drops 1 → 0. The trip stays not
        // eligible, so the per-diem count is 0 either side and its override stands.
        clickRowAction(page, TRIP, "Edit");
        page.find(UnifiedCheckbox.class).withLabel("Pay meal allowance?").uncheck();
        saveTrip(page);

        assertThat(dialogText(clearingConfirm(page)))
                .contains("Meal allowance: calculated 1 → 0 meals.")
                .doesNotContain("Per diem allowance");
        page.find(UnifiedButton.class).withText("Clear and save trip").click();
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(service.findMine(id).travels().getFirst().quantityOverrides())
                .containsOnlyKeys(GeneratedLineKind.PER_DIEM_FULL);
    }

    @Test
    void aTripEditThatMovesTwoOverriddenCountsReportsBothInOneConfirm(UnifiedPage page) {
        var id = seedOverriddenTravel(page, DEP.plusHours(55));
        overrideCount(page, "Per diem allowance (partial day)", 1, "the leftover was worked");
        page.find(UnifiedButton.class).withText("Save").click();

        // 55 h → 72 h: three whole days and no leftover at all, so both corrected
        // counts move (full 2 → 3, partial 1 → 0) — one dialog, both named.
        editTripReturn(page, DEP.plusHours(72));

        // One dialog, both corrections named — not two prompts in a row.
        assertThat(clearingConfirm(page).getHeaderTitle()).isEqualTo("Clear your overrides?");
        assertThat(dialogText(clearingConfirm(page))).contains(
                "Per diem allowance (full day): calculated 2 → 3 days.",
                "Per diem allowance (partial day): calculated 1 → 0 days.",
                "\"the Wednesday was personal\"", "\"the leftover was worked\"");
        page.find(UnifiedButton.class).withText("Clear and save trip").click();
        page.find(UnifiedButton.class).withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.quantityOverrides()).isEmpty();
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("162.00");
    }

    @Test
    void editingAnOverrideNeverChangesTheCalculationAndNeverPrompts(UnifiedPage page) {
        // The two surfaces are independent: an override is a correction to the
        // calculation's output, so it can never move the calculation's input.
        var id = seedOverriddenTravel(page, DEP.plusHours(55));

        clickRowAction(page, "Per diem allowance (full day)", "Edit override");
        page.find(UnifiedIntegerField.class).withLabel("Count").setValue(2);
        page.find(UnifiedTextArea.class).withLabel("Reason for the override")
                .setValue("both days were personal after all");
        page.find(UnifiedButton.class).withText("Save override").click();

        assertThat(page.find(UnifiedButton.class).withText("Clear and save trip").visible()).doesNotExist();
        assertSays(page, "Calculated: 2 × €54.00 = €108.00", "Reason: both days were personal after all");

        page.find(UnifiedButton.class).withText("Save").click();

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.returnAt()).isEqualTo(DEP.plusHours(55));
        assertThat(trip.quantityOverrides()
                .get(GeneratedLineKind.PER_DIEM_FULL).quantity())
                .isEqualByComparingTo("2.00");
    }

    @Test
    void theTripPreviewShowsCalculatedFiguresAnnotatedWhereAnOverrideIsInForce(UnifiedPage page) {
        // What makes the "2 → 3" warning legible: the dialog previews the trip inputs,
        // never the corrected figures — and says so where a correction is in force, so
        // the preview and the report showing different numbers is never a mystery.
        seedOverriddenTravel(page, DEP.plusHours(55));

        clickRowAction(page, TRIP, "Edit");

        assertThat(tripPreviewText(page)).contains(
                "Per diem allowance (full day)", "€108.00",
                "Overridden: 1 day on the report (the Wednesday was personal)");
        // The report row behind still shows the effective figure — the two disagree by
        // design, which is exactly why the preview line is annotated.
        assertSays(page, "€79.00");
    }

    @Test
    void aSuppressedKindIsAnnotatedInThePreviewThatNoLongerListsIt(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (partial day)", 0, "the leftover was personal");
        page.find(UnifiedButton.class).withText("Save").click();

        clickRowAction(page, TRIP, "Edit");

        // The calculated preview still lists the partial day (the rules award it), and
        // the note says the report does not.
        assertThat(tripPreviewText(page)).contains(
                "Per diem allowance (partial day)", "€25.00",
                "Overridden: no line on the report (the leftover was personal)");
    }

    /**
     * Seeds a DRAFT report with one domestic trip of the given return time, corrects
     * its full-day count down to 1, and saves — the state every clearing case below
     * starts from: a persisted override standing on a persisted trip. Returns the
     * report id, with the view left open on it.
     */
    private Long seedOverriddenTravel(UnifiedPage page, LocalDateTime returnAt) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, returnAt);
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (full day)", 1, "the Wednesday was personal");
        page.find(UnifiedButton.class).withText("Save").click();
        return id;
    }

    /**
     * The clearing confirm a trip save opened (issue #133), identified by a phrase
     * only it uses — the trip editor is still open behind it, so "the dialog" is
     * ambiguous.
     */
    private UnifiedDialog clearingConfirm(UnifiedPage page) {
        return openDialogSaying(page, "recalculates its allowances");
    }

    /** The trip editor's live allowance preview, as text. */
    private String tripPreviewText(UnifiedPage page) {
        return openDialogSaying(page, "Trip total").element().getText();
    }

    /**
     * The open dialog whose content includes {@code marker}, waiting for it to open.
     *
     * <p>Innermost match: on Browserless since Vaadin 25.3 a dialog opened while a
     * modal dialog is open attaches <em>inside</em> it (F-082), so the outer editor's
     * text also contains the confirm's marker; in a browser each is its own overlay.
     * The innermost match is the one with the least text, on either.
     */
    private UnifiedDialog openDialogSaying(UnifiedPage page, String marker) {
        page.waitUntil(() -> !openDialogsSaying(page, marker).isEmpty());
        return openDialogsSaying(page, marker).stream()
                .min(java.util.Comparator.comparingInt(dialog -> dialog.element().getText().length()))
                .orElseThrow(() -> new AssertionError("No open dialog says \"" + marker + "\""));
    }

    private static String dialogText(UnifiedDialog dialog) {
        return dialog.element().getText();
    }

    private static List<UnifiedDialog> openDialogsSaying(UnifiedPage page, String marker) {
        return page.find(UnifiedDialog.class).all().stream()
                .filter(UnifiedDialog::isOpen)
                .filter(dialog -> dialog.element().getText().contains(marker))
                .toList();
    }

    /** Re-costs the open report's only trip by moving its return time. */
    private void editTripReturn(UnifiedPage page, LocalDateTime returnAt) {
        clickRowAction(page, TRIP, "Edit");
        page.find(UnifiedDateTimePicker.class).withLabel("Return").setValue(returnAt);
        saveTrip(page);
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
    void theDailyAllowanceGroupOpensOnFullByDefaultAndSavesBothFlagsFalse(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();

        var group = dailyAllowance(page);
        assertThat(group).hasLabel("Daily allowance");
        assertThat(group).hasSelectedItem(TravelFormModel.DailyAllowance.FULL.label());
        assertThat(group.getItems())
                .containsExactly("Full daily allowance", "Halved, free lunch provided",
                        "Not eligible");
        // Vertical is Aura's default; the horizontal variant the frame draws is not set.
        String theme = group.element().getAttribute("theme");
        assertThat(theme == null || theme.isBlank()).as("no theme variant").isTrue();
        // The two checkboxes it replaces are gone; the two that stay are these.
        assertThat(page.find(UnifiedDialog.class).find(UnifiedCheckbox.class).all())
                .extracting(UnifiedCheckbox::getLabel)
                .containsExactly("Charge expenses from customer?", "Pay meal allowance?");

        fillMinimalTrip(page);
        saveTrip(page);
        page.find(UnifiedButton.class).withText("Save").click();

        var trip = service.findMine(service.listMine().getFirst().id()).travels().getFirst();
        assertThat(trip.notEligibleForAllowance()).isFalse();
        assertThat(trip.freeLunch()).isFalse();
    }

    @Test
    void choosingHalvedSavesEligibleWithAFreeLunch(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();
        fillMinimalTrip(page);

        dailyAllowance(page).selectItem("Halved, free lunch provided");
        // The per-diem previews halved: €54 → €27.
        assertThat(tripPreviewText(page)).contains("€27.00");
        saveTrip(page);
        page.find(UnifiedButton.class).withText("Save").click();

        var trip = service.findMine(service.listMine().getFirst().id()).travels().getFirst();
        assertThat(trip.notEligibleForAllowance()).isFalse();
        assertThat(trip.freeLunch()).isTrue();
    }

    @Test
    void choosingNotEligibleSavesNotEligibleWithoutAFreeLunch(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();
        fillMinimalTrip(page);

        dailyAllowance(page).selectItem("Not eligible");
        // Nothing earned: the no-allowances line stands in for the rows, and there is
        // no Trip total row to end on.
        assertThat(dialogText(openDialogSaying(page, "No allowances for this trip")))
                .doesNotContain("Per diem allowance", "Trip total");
        saveTrip(page);
        page.find(UnifiedButton.class).withText("Save").click();

        var trip = service.findMine(service.listMine().getFirst().id()).travels().getFirst();
        assertThat(trip.notEligibleForAllowance()).isTrue();
        assertThat(trip.freeLunch()).isFalse();
    }

    @Test
    void editingATripReadsItsFlagsBackIntoTheGroup(UnifiedPage page) {
        // freeLunch=true → Halved; notEligible=true → Not eligible — the mapping read
        // in the other direction, so an edit opens on what was saved.
        var zero = BigDecimal.ZERO.setScale(2);
        var halved = TravelDto.domestic(null, DEP, DEP.plusHours(11), "Helsinki",
                "Client visit", false, true, false, zero, false, zero);
        var id = service.create(new ReportDetailDto(null, LocalDate.of(2026, 7, 10),
                "seed", ReportStatus.DRAFT, 0L, List.of(), List.of(halved), zero, zero,
                zero, zero, zero, zero));
        page.navigate(ReportDetailView.class, id);
        clickRowAction(page, TRIP, "Edit");
        assertThat(dailyAllowance(page)).hasSelectedItem(TravelFormModel.DailyAllowance.HALVED.label());
        page.find(UnifiedDialog.class).find(UnifiedButton.class).withText("Cancel").click();

        var mealId = seedReportWithMealTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        page.navigate(ReportDetailView.class, mealId);
        clickRowAction(page, TRIP, "Edit");
        assertThat(dailyAllowance(page)).hasSelectedItem(TravelFormModel.DailyAllowance.NOT_ELIGIBLE.label());
        assertThat(checkboxValue(page, "Pay meal allowance?")).isTrue();
    }

    @Test
    void checkingPayMealAllowanceSelectsNotEligible(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();
        dailyAllowance(page).selectItem("Halved, free lunch provided");

        page.find(UnifiedCheckbox.class).withLabel("Pay meal allowance?").check();

        // Meal allowance needs "no per-diem": the group moves to Not eligible, which
        // by construction drops the free lunch too.
        assertThat(checkboxValue(page, "Pay meal allowance?")).isTrue();
        assertThat(dailyAllowance(page)).hasSelectedItem(TravelFormModel.DailyAllowance.NOT_ELIGIBLE.label());
    }

    @Test
    void selectingFullClearsPayMealAllowance(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();
        page.find(UnifiedCheckbox.class).withLabel("Pay meal allowance?").check();
        assertThat(dailyAllowance(page)).hasSelectedItem(TravelFormModel.DailyAllowance.NOT_ELIGIBLE.label());

        dailyAllowance(page).selectItem("Full daily allowance");

        // A per-diem applies again, so the meal allowance can't stand.
        assertThat(checkboxValue(page, "Pay meal allowance?")).isFalse();
        assertThat(dailyAllowance(page)).hasSelectedItem(TravelFormModel.DailyAllowance.FULL.label());
    }

    @Test
    void selectingHalvedClearsPayMealAllowance(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();
        page.find(UnifiedCheckbox.class).withLabel("Pay meal allowance?").check();

        dailyAllowance(page).selectItem("Halved, free lunch provided");

        assertThat(checkboxValue(page, "Pay meal allowance?")).isFalse();
        assertThat(dailyAllowance(page)).hasSelectedItem(TravelFormModel.DailyAllowance.HALVED.label());
    }

    @Test
    void travelEditorLaysItsFieldsOutAsTheDesignsTwoSectionGrid(UnifiedPage page) {
        // travel-editor-dialog.md § Layout — #179 delta items 1–4, 7, 13: one
        // two-column grid with the line editor's gaps, two eyebrows and two rules as
        // colspan-2 rows, the design's field order, and the totals as the last row.
        page.navigate(ReportDetailView.class);
        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();

        UnifiedDialog dialog = page.find(UnifiedDialog.class);
        assertThat(dialog.element().getProperty("width")).isEqualTo("35rem");
        assertThat(dialog).hasHeaderTitle("Add travel info");

        UnifiedGenericComponent form = dialog.findComponent(FormLayout.class);
        assertThat(form).exists();
        // Steps and spacings are server-side configuration with no stable DOM
        // reading across Testing Tools; checked where the component is at hand.
        form.tryUnwrap(FormLayout.class).ifPresent(layout -> {
            assertThat(layout.getResponsiveSteps())
                    .extracting(step -> step.toJson().toString())
                    .containsExactly(
                            new FormLayout.ResponsiveStep("0", 1).toJson().toString(),
                            new FormLayout.ResponsiveStep("24rem", 2).toJson().toString());
            assertThat(layout.getColumnSpacing()).isEqualTo("var(--em-section-gap)");
            assertThat(layout.getRowSpacing()).isEqualTo("var(--em-card-padding)");
        });

        // Until the dates are complete the totals and the rule above them hide.
        UnifiedDiv totals = dialog.find(UnifiedDiv.class).withClassName("trip-totals");
        assertThat(totals.visible()).doesNotExist();
        fillMinimalTrip(page);
        assertThat(totals.visible()).exists();

        // The design's order, read off the grid's children. Eyebrows are Spans with
        // the .section-label role, rules are Hr.form-rule, the totals a Div.trip-totals.
        // (Read once the totals show: a browser has never been sent a component that
        // was never visible, so before that they are not children there at all.)
        List<UnifiedElement> children = form.element().children();
        assertThat(children).extracting(ReportDetailViewUiTest::gridCell).containsExactly(
                "eyebrow:Destinations", "DateTimePicker:Departure", "DateTimePicker:Return",
                "ComboBox:Destination country", "TextField:Destinations",
                "TextField:Travel purpose", "rule", "eyebrow:Expenses",
                "Checkbox:Charge expenses from customer?",
                "BigDecimalField:Kilometre allowance (km)",
                "BigDecimalField:Parking fees (€)", "RadioButtonGroup:Daily allowance",
                "Checkbox:Pay meal allowance?", "rule", "totals");
        assertThat(children).extracting(ReportDetailViewUiTest::colspanOf)
                .containsExactly(2, 1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 2, 2, 2, 2);
        // The rules carry no margin of their own — the grid's row gap spaces them.
        assertThat(children.get(6).getInlineStyle("margin")).isNull();

        // Label and figure are separate spans in the totals card's classes; Trip
        // total is the LAST row, and the block paints no surface of its own.
        String lastRow = totals.element().children().getLast().getText();
        assertThat(lastRow).contains("Trip total").contains("€54.00");
        assertThat(lastRow.indexOf("Trip total")).isLessThan(lastRow.indexOf("€54.00"));
        assertThat(dialog.find(UnifiedSpan.class).withText("Trip total")
                .withClassName("totals-total-row")).exists();
        assertThat(dialog.find(UnifiedSpan.class).withText("€54.00")
                .withClassName("totals-grand")).exists();
        assertThat(dialog.find(UnifiedDiv.class).withClassName("travel-preview")).doesNotExist();

        // Footer: Cancel and a primary "Save" — no "Save trip", no close button.
        assertThat(dialog.find(UnifiedButton.class).withText("Save")).exists();
        assertThat(page.find(UnifiedButton.class).withText("Save trip").visible()).doesNotExist();
        assertThat(dialog.element().getPropertyBoolean("noCloseOnEsc")).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void travelEditorTitlesTheEditStateEditTravelInfo(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);
        clickRowAction(page, TRIP, "Edit");
        assertThat(page.find(UnifiedDialog.class)).hasHeaderTitle("Edit travel info");
    }

    /** "kind:label" for a field, or the grid row's role for a non-field child. */
    private static String gridCell(UnifiedElement child) {
        String classes = " " + java.util.Objects.requireNonNullElse(child.getAttribute("class"), "") + " ";
        if (child.getTag().equals("span") && classes.contains(" section-label ")) {
            return "eyebrow:" + child.getText().strip();
        }
        if (child.getTag().equals("hr") && classes.contains(" form-rule ")) {
            return "rule";
        }
        if (child.getTag().equals("div") && classes.contains(" trip-totals ")) {
            return "totals";
        }
        String kind = switch (child.getTag()) {
            case "vaadin-date-time-picker" -> "DateTimePicker";
            case "vaadin-combo-box" -> "ComboBox";
            case "vaadin-text-field" -> "TextField";
            case "vaadin-checkbox" -> "Checkbox";
            case "vaadin-big-decimal-field" -> "BigDecimalField";
            case "vaadin-radio-group" -> "RadioButtonGroup";
            default -> child.getTag();
        };
        return kind + ":" + child.getProperty("label");
    }

    /** A form-layout child's column span: its {@code colspan} attribute, 1 when unset. */
    private static int colspanOf(UnifiedElement child) {
        String value = child.getAttribute("colspan");
        return value == null || value.isBlank() ? 1 : Integer.parseInt(value.strip());
    }

    /** The open trip editor's {@code Daily allowance} radio group. */
    private UnifiedRadioButtonGroup dailyAllowance(UnifiedPage page) {
        return page.find(UnifiedRadioButtonGroup.class)
                .withLabel("Daily allowance");
    }

    /** Clicks the trip editor's primary — "Save", scoped to the dialog because the
     *  report behind it has a Save of its own. */
    private void saveTrip(UnifiedPage page) {
        page.find(UnifiedDialog.class).find(UnifiedButton.class).withText("Save").click();
    }

    /** Dates, country, destinations and purpose for an 11-hour domestic trip. */
    private void fillMinimalTrip(UnifiedPage page) {
        page.find(UnifiedDateTimePicker.class).withLabel("Departure").setValue(DEP);
        page.find(UnifiedDateTimePicker.class).withLabel("Return").setValue(DEP.plusHours(11));
        page.find(UnifiedComboBox.class).withLabel("Destination country")
                .selectItem("Finland (domestic)");
        page.find(UnifiedTextField.class).withLabel("Destinations").setValue("Helsinki");
        page.find(UnifiedTextField.class).withLabel("Travel purpose").setValue("Client visit");
    }

    /** The current value of the dialog checkbox carrying the given label. */
    private boolean checkboxValue(UnifiedPage page, String label) {
        return page.find(UnifiedCheckbox.class).withLabel(label).isChecked();
    }

    @Test
    void removingATripRemovesItsPerDiemOnSave(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);

        assertSays(page, "Per diem allowance");

        clickRowAction(page, TRIP, "Remove");
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(service.findMine(id).travels()).isEmpty();
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void choosingADepartureConstrainsTheReturnPickerRangeAndViceVersa(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();
        page.find(UnifiedDateTimePicker.class).withLabel("Departure").setValue(DEP);
        page.find(UnifiedDateTimePicker.class).withLabel("Return").setValue(DEP.plusHours(11));

        // The overlay can no longer offer an invalid range: the return can't reach
        // back to the departure, nor the departure forward to the return. The bounds
        // sit one 15-minute step clear of each other, not on the other's instant —
        // setMin/setMax are inclusive while the trip rule is strict, so an unshifted
        // bound still offers the same instant on both sides (issue #140).
        var ret = page.find(UnifiedDateTimePicker.class).withLabel("Return");
        var dep = page.find(UnifiedDateTimePicker.class).withLabel("Departure");
        page.waitUntil(() -> ret.element().getProperty("min") != null
                && dep.element().getProperty("max") != null);
        assertThat(LocalDateTime.parse(ret.element().getProperty("min")))
                .isEqualTo(DEP.plusMinutes(15));
        assertThat(LocalDateTime.parse(dep.element().getProperty("max")))
                .isEqualTo(DEP.plusHours(11).minusMinutes(15));
    }

    @Test
    void aReturnEqualToTheDepartureIsRefusedAndSaysWhy(UnifiedPage page) {
        // The reported case: same date *and* same time on both sides — typed by the
        // user even though the return's min sits a step past the departure. Prove
        // the value still never reaches a save silently: the constraint validation
        // lands the rule in the dialog's error summary, not in the generic
        // "something went wrong" dialog (issue #140).
        page.navigate(ReportDetailView.class);

        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();
        page.find(UnifiedDateTimePicker.class).withLabel("Departure").setValue(DEP);
        page.find(UnifiedComboBox.class).withLabel("Destination country")
                .selectItem("Finland (domestic)");
        page.find(UnifiedTextField.class).withLabel("Destinations").setValue("Helsinki");
        page.find(UnifiedTextField.class).withLabel("Travel purpose").setValue("Client visit");

        page.find(UnifiedDateTimePicker.class).withLabel("Return").setValue(DEP);

        saveTrip(page);
        assertUiSays(page, "Return must be after the departure");
        assertUiDoesNotSay(page, "Something went wrong");
        // The dialog stays open and the trip is not committed.
        assertThat(page.find(UnifiedDialog.class).find(UnifiedButton.class).withText("Save")).exists();
        assertThat(page.find(UnifiedSpan.class).withText("Per diem allowance").visible()).doesNotExist();
    }

    @Test
    void anIncompleteTripShowsTheErrorSummaryAndGeneratesNothing(UnifiedPage page) {
        // The reciprocal min/max on the pickers keeps the *overlay* from producing an
        // invalid range (see the constraint test above), but a hand-typed one still
        // reaches Save — covered by the range-rule tests above. Here we prove the
        // always-enabled Save + error-summary rule (ADR-0020) on the other invalid
        // case: missing required fields.
        page.navigate(ReportDetailView.class);

        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();
        saveTrip(page);

        // The dialog overlay carries the reasons and stays open; nothing generated.
        assertUiSays(page, "Departure date & time is required", "Destinations are required");
        assertThat(page.find(UnifiedDialog.class).find(UnifiedButton.class).withText("Save")).exists();
        assertThat(page.find(UnifiedSpan.class).withText("Per diem allowance").visible()).doesNotExist();
    }

    @Test
    void travelPickersCarryIncompleteAndBadInputErrorMessages(UnifiedPage page) {
        // Issue #85: entering only the date (not the time) in a departure/return
        // picker makes it invalid; without the incomplete-input message that error
        // reached the summary blank. The Unified API types partial and unparseable
        // input as a user does, so the summary's wording is asserted directly.
        page.navigate(ReportDetailView.class);
        page.find(UnifiedButton.class).withAccessibleName("Add travel").click();

        // A date the field can parse: Browserless parses ISO text only, a browser the
        // field's locale format (en-US here, the browser default).
        String parseableDate = page.hasRealBrowser() ? "7/1/2026" : "2026-07-01";
        for (String label : new String[] { "Departure", "Return" }) {
            UnifiedDateTimePicker picker = page.find(UnifiedDateTimePicker.class).withLabel(label);

            picker.typeText(parseableDate, "");
            saveTrip(page);
            assertUiSays(page, "Enter both a date and a time");

            picker.typeText("dsdds", "10:00");
            saveTrip(page);
            assertUiSays(page, "Enter a valid date and time");

            picker.clear();
        }
        // The range bounds are constraints too (issue #140) — unnamed, they'd reach
        // the summary as the same blank bullet issue #85 fixed. A browser keeps its
        // overlay inside the bounds, so their wording is read off the component
        // where the Testing Tool has it.
        for (String label : new String[] { "Departure", "Return" }) {
            page.find(UnifiedDateTimePicker.class).withLabel(label)
                    .tryUnwrap(DateTimePicker.class).ifPresent(picker -> {
                        assertThat(picker.getI18n().getMinErrorMessage())
                                .as(label + " min message").isNotBlank();
                        assertThat(picker.getI18n().getMaxErrorMessage())
                                .as(label + " max message").isNotBlank();
                    });
        }
    }

    @Test
    void aSubmittedReportShowsTheTripButHidesEveryTripAction(UnifiedPage page) {
        var id = seedSubmittedReportWithTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);

        // The trip info and its per-diem are still visible (read path)…
        assertSays(page, "Client visit", "Per diem allowance", "€54.00");
        // …a trip-only read-only report must not prompt "add your first": the empty
        // state is hidden, so no visible span carries it (ADR-0020)…
        assertThat(page.find(UnifiedSpan.class).withText("No expenses yet — add your first.").visible()).doesNotExist();
        // …and every mutation surface is gone on a read-only report.
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Add travel").visible()).doesNotExist();
        assertThat(rowActions(page, TRIP)).isEmpty();
        assertThat(page.find(UnifiedMenuBar.class).withClassName("row-action-menu").visible()).doesNotExist();
    }

    @Test
    void removingALinePersistsOnSave(UnifiedPage page) {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        assertSays(page, "€60.00");

        clickRowAction(rowMenus(page).getFirst(), "Remove");
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(service.findMine(id).lines()).isEmpty();
    }

    // ------------------------------------------------ the re-cut layout (#172)
    //
    // The design re-cut the line list rather than restyling it: one card per
    // SECTION holding its lines as rows, where the previous frame drew one bordered
    // card per line. These pin the shape, not the pixels — spacing, alignment and
    // colour are what /figma-visual-verification is for.

    @Test
    void eachSectionIsOneCardHoldingItsLinesAsRows(UnifiedPage page) {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(31));
        page.navigate(ReportDetailView.class, id);
        page.find(UnifiedButton.class).withAccessibleName("Add expense").click();
        addLineThroughEditor(page, "40.00");

        // Two cards for the whole view, not one per line: the travel variant and the
        // plain Expenses one.
        var cards = view(page).find(UnifiedDiv.class).withClassName("expense-item-card").all();
        assertThat(cards).hasSize(2);
        assertThat(classes(cards.getFirst())).contains("expense-item-card--travel");
        assertThat(classes(cards.get(1)))
                .doesNotContain("expense-item-card--travel");

        // The travel card holds the trip row and both per-diem rows it generated —
        // nested INSIDE it, indented, rather than in a wrapper beside it.
        assertThat(rowsIn(cards.getFirst())).hasSize(3);
        assertThat(classes(rowsIn(cards.getFirst()).get(1)))
                .contains("travel-line-row");
        assertThat(rowsIn(cards.get(1))).hasSize(1);

        // And nothing is a .line-card any more.
        assertThat(page.find(UnifiedHorizontalLayout.class).withClassName("line-card").visible()).doesNotExist();
    }

    @Test
    void aSectionWithNoRowsRendersNoCard(UnifiedPage page) {
        var id = seedReport(LocalDate.of(2026, 7, 1), "empty");
        page.navigate(ReportDetailView.class, id);

        // A fresh draft has no trip and no line, so both cards are absent while both
        // headings and their Add actions stand.
        assertThat(page.find(UnifiedDiv.class).withClassName("expense-item-card").visible()).doesNotExist();
        assertThat(page.find(UnifiedSpan.class).withText("Travel info")).exists();
        assertThat(page.find(UnifiedSpan.class).withText("Expenses")).exists();
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Add travel")).exists();
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Add expense")).exists();
    }

    @Test
    void aReadOnlyReportWithNothingInASectionRendersNoSectionAtAll(UnifiedPage page) {
        // No trip, and read-only: a "TRAVEL INFO" heading over nothing, beside an Add
        // the report cannot offer, is worse than no section (ADR-0020).
        var id = seedSubmittedReport(LocalDate.of(2026, 7, 1), "80.00");
        page.navigate(ReportDetailView.class, id);

        assertThat(page.find(UnifiedSpan.class).withText("Travel info").visible()).doesNotExist();
        assertThat(page.find(UnifiedSpan.class).withText("Expenses")).exists();
    }

    @Test
    void anExpenseRowDrawsItsTypesOwnGlyphAndNoColourSwatch(UnifiedPage page) {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        // The glyph is the type's persisted icon (ADR-0026), not a colour hashed off
        // its name. seedReportWithLine files the line under the first active type,
        // Travel allowance, whose seeded glyph is `plane` (V15).
        assertThat(page.find(UnifiedSpan.class).withClassName("expense-row-icon")
                .findComponent(SvgIcon.class)).hasProperty("symbol", "plane");
        // The hashed swatch is gone, along with the four-way --aura-red collision.
        assertThat(page.find(UnifiedDiv.class).withClassName("category-dot").visible()).doesNotExist();
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
    void aTypeWithNoGlyphChosenRendersNoneAndStillNamesItself(UnifiedPage page) {
        var rate = firstActiveRate();
        var type = referenceData.createExpenseType("Glyphless", rate.id(), null);
        var line = ExpenseLineDto.of(null, type.id(), type.name(), type.icon(),
                rate.id(), rate.value(), new BigDecimal("12.00"), null);
        var zero = BigDecimal.ZERO.setScale(2);
        var id = service.create(new ReportDetailDto(null, LocalDate.of(2026, 7, 1),
                "seed", ReportStatus.DRAFT, 0L, List.of(line), zero, zero, zero));

        page.navigate(ReportDetailView.class, id);

        assertThat(page.find(UnifiedSpan.class).withClassName("expense-row-icon")
                .findComponent(SvgIcon.class)).doesNotExist();
        assertThat(page.find(UnifiedSpan.class).withText("Glyphless")).exists();
    }

    @Test
    void aRowIsNotClickableAndTheMenuIsTheOnlyRouteToEditing(UnifiedPage page) {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        // Withdrawn with the card the row used to be: no .clickable anywhere, and the
        // row's own actions are exactly Edit and Remove behind the ⋮.
        assertThat(page.find(UnifiedHorizontalLayout.class).withClassName("clickable").visible()).doesNotExist();
        assertThat(rowActions(page, "Travel allowance")).containsExactly("Edit", "Remove");
    }

    @Test
    void everyRowMenuIsNamedAfterItsRowAndEveryActionCarriesText(UnifiedPage page) {
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00"));
        page.navigate(ReportDetailView.class, id);

        // "Actions" alone is useless on a page holding five of them, so each names
        // its row (ADR-0020, row-action-menu.md).
        // A per-diem-eligible trip earns no meal allowance — the two are mutually
        // exclusive under the Finnish rule (issue #93) — so this trip's four rows are
        // the trip itself plus per diem, kilometre and parking.
        assertThat(rowMenus(page))
                .extracting(ReportDetailViewUiTest::triggerName)
                .containsExactlyInAnyOrder("Actions for " + TRIP,
                        "Actions for Per diem allowance (full day)",
                        "Actions for Kilometre allowance",
                        "Actions for Parking");
        // And every item in every menu is text-labelled, never an icon alone.
        assertThat(rowMenus(page)).allSatisfy(menu ->
                assertThat(menu.getItems(triggerName(menu)))
                        .isNotEmpty()
                        .allSatisfy(item -> assertThat(item).isNotBlank()));
    }

    @Test
    void aRowMenusNameFollowsTheTypeTheEditorChanges(UnifiedPage page) {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);
        assertThat(triggerName(page.find(UnifiedMenuBar.class).withClassName("row-action-menu").visible()))
                .isEqualTo("Actions for Travel allowance");

        // The row keeps its component and re-reads the signal, so the accessible name
        // has to follow or it goes quietly stale.
        openLineCardEditor(page);
        page.find(UnifiedComboBox.class).withLabel("Expense type")
                .selectItem("Accommodation");
        saveExpense(page);

        assertThat(triggerName(page.find(UnifiedMenuBar.class).withClassName("row-action-menu").visible()))
                .isEqualTo("Actions for Accommodation");
    }

    @Test
    void bothAddActionsReadAddAndAreToldApartByTheirAccessibleNames(UnifiedPage page) {
        var id = seedReport(LocalDate.of(2026, 7, 1), "empty");
        page.navigate(ReportDetailView.class, id);

        // The design labels both simply "Add", so the page carries two controls with
        // the same visible name and the aria-label is what names the section.
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Add travel")).exists();
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Add expense")).exists();
        assertThat(page.find(UnifiedButton.class).withText("Add").visible()).hasCount(2);
        // And the back link keeps its own name.
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Back to reports")).exists();
    }

    @Test
    void onlyTheVatBearingGeneratedLineShowsANetVatSplit(UnifiedPage page) {
        // Parking is VAT-bearing at 25.5 %; the per-diem, kilometre and meal lines are
        // statutory tax-free allowances. The design draws the same net/VAT string on
        // every generated row including the per-diem one, which is mock text rather
        // than a specification (#173).
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00"));
        page.navigate(ReportDetailView.class, id);

        assertThat(splitOn(page, "Parking")).startsWith("net ").contains("(25.5 %)");
        assertThat(splitOn(page, "Per diem allowance (full day)")).isNull();
        assertThat(splitOn(page, "Kilometre allowance")).isNull();

        // …and the meal allowance, which this trip does not earn, on one that does.
        page.navigate(ReportDetailView.class, seedReportWithMealTravel(
                LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11)));
        assertThat(splitOn(page, "Meal allowance")).isNull();
    }

    @Test
    void aReadOnlyReportRendersNoRowMenusAndNoAddActionsButKeepsItsReceipts(UnifiedPage page) {
        var id = seedReportWithReceipt(LocalDate.of(2026, 7, 1), "70.00", "hotel.jpg");
        service.submit(id, service.findMine(id).version());
        page.navigate(ReportDetailView.class, id);

        // No menu at all — not a disabled trigger — and neither Add action.
        assertThat(page.find(UnifiedMenuBar.class).withClassName("row-action-menu").visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Add expense").visible()).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Add travel").visible()).doesNotExist();
        // The receipt stays activatable, so a submitted report's receipts remain
        // viewable (ADR-0021).
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Preview receipt: hotel.jpg")).exists();
    }

    @Test
    void anAttachmentRendersAsAPaperclipChipThatStillOpensThePreview(UnifiedPage page) {
        var id = seedReportWithReceipt(LocalDate.of(2026, 7, 1), "70.00", "hotel.jpg");
        page.navigate(ReportDetailView.class, id);

        // The resting state is the design's chip — a paperclip and the filename — and
        // only the resting state changed: activating it opens the enlarge dialog.
        UnifiedButton chip = page.find(UnifiedButton.class).withClassName("expense-row-attachment");
        assertThat(chip).hasText("hotel.jpg");
        assertThat(chip.element().findDescendants("vaadin-icon"))
                .extracting(icon -> icon.getProperty("symbol"))
                .containsExactly("paperclip");

        chip.click();
        assertThat(page.find(UnifiedDialog.class)).isOpen();
        assertThat(page.findComponent(Image.class)).exists();
    }

    @Test
    void theStatusHistoryIsOneBoxWithItsSharedSectionHeading(UnifiedPage page) {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Missing receipt");
        page.navigate(ReportDetailView.class, id);

        // One box holding every entry, not one bordered box per entry, under the same
        // uppercase .section-label role the other two section headings use.
        UnifiedDiv box = page.find(UnifiedDiv.class).withClassName("status-history-box");
        assertThat(box).exists();
        assertThat(box.element().children()).hasSize(2);       // Submitted, Rejected
        assertThat(page.find(UnifiedSpan.class).withText("Status history")
                .withClassName("section-label")).exists();
        assertThat(page.find(UnifiedVerticalLayout.class).withClassName("status-history-entry"))
                .hasCount(2);
    }

    /** Adds one expense line through the (already open) editor at the given amount. */
    private void addLineThroughEditor(UnifiedPage page, String amount) {
        page.find(UnifiedComboBox.class).withLabel("Expense type")
                .selectItem(firstActiveType().name());
        page.find(UnifiedComboBox.class).withLabel("VAT rate").selectItem("25.5 %");
        page.find(UnifiedBigDecimalField.class).withLabel("Unit price")
                .setValue(new BigDecimal(amount));
        saveExpense(page);
    }

    /** The {@code .expense-row} whose text starts with {@code rowLabel}. */
    private static UnifiedHorizontalLayout expenseRow(UnifiedPage page, String rowLabel) {
        return page.find(UnifiedHorizontalLayout.class).withClassName("expense-row")
                .where(row -> row.getText().strip().startsWith(rowLabel));
    }

    /**
     * The {@code net … · VAT …} sub-line on the named row, or {@code null} if it has
     * none. Read off the row rather than the whole view, since a manual line beside
     * it would otherwise satisfy the assertion.
     */
    private String splitOn(UnifiedPage page, String rowLabel) {
        assertThat(expenseRow(page, rowLabel)).exists();
        return expenseRow(page, rowLabel).find(UnifiedSpan.class).all().stream()
                .map(span -> span.getText().strip())
                .filter(text -> text.startsWith("net "))
                .findFirst().orElse(null);
    }

    /**
     * Opens the line editor for the report's first expense row. The row is no longer
     * clickable — the ⋮ menu is the only route to editing (#172) — so this opens the
     * first row's menu and chooses Edit.
     */
    private void openLineCardEditor(UnifiedPage page) {
        assertThat(page.find(UnifiedMenuBar.class).withClassName("row-action-menu").visible())
                .exists();
        clickRowAction(page.find(UnifiedMenuBar.class).withClassName("row-action-menu")
                .visible().all().getFirst(), "Edit");
    }

    /**
     * Clicks the line editor's primary button. It reads {@code Save} as of the
     * design's frame (358:3267), which is also the report's own save button, so it
     * has to be located <em>inside</em> the dialog rather than by text alone.
     */
    private void saveExpense(UnifiedPage page) {
        page.find(UnifiedDialog.class).find(UnifiedButton.class).withText("Save").click();
    }

    /** The line editor's BigDecimalField with the given label. */
    private UnifiedBigDecimalField bigDecimalField(UnifiedPage page, String label) {
        return page.find(UnifiedBigDecimalField.class).withLabel(label);
    }

    /** Every visible row ⋮ menu on the page, in page order. */
    private static List<UnifiedMenuBar> rowMenus(UnifiedPage page) {
        return page.find(UnifiedMenuBar.class).withClassName("row-action-menu").visible().all();
    }

    /** The element's class names. */
    private static List<String> classes(UnifiedElement element) {
        String value = element.getAttribute("class");
        return value == null || value.isBlank() ? List.of() : List.of(value.strip().split("\\s+"));
    }

    private static List<String> classes(com.vaadin.testing.unified.UnifiedWrapper wrapper) {
        return classes(wrapper.element());
    }

    /** The .expense-row children of one section card, in order. */
    private static List<UnifiedElement> rowsIn(UnifiedDiv card) {
        return card.element().children().stream()
                .filter(child -> classes(child).contains("expense-row"))
                .toList();
    }

    /** The ⋮ trigger's accessible name (its only item, an icon), which must identify its row. */
    private static String triggerName(UnifiedMenuBar menu) {
        return menu.getItems().getFirst();
    }

    /** The one row menu whose trigger names {@code rowLabel}. */
    private static UnifiedMenuBar rowMenu(UnifiedPage page, String rowLabel) {
        return page.find(UnifiedMenuBar.class).withClassName("row-action-menu")
                .withItem("Actions for " + rowLabel);
    }

    /** Opens the named row's ⋮ and activates the named action. */
    private void clickRowAction(UnifiedPage page, String rowLabel, String action) {
        rowMenu(page, rowLabel).clickItem("Actions for " + rowLabel, action);
    }

    /** Opens a row's ⋮ and activates the named action. */
    private void clickRowAction(UnifiedMenuBar menu, String action) {
        menu.clickItem(triggerName(menu), action);
    }

    /** The text labels a row's ⋮ offers, in order — empty if it has no menu at all. */
    private List<String> rowActions(UnifiedPage page, String rowLabel) {
        var menus = rowMenu(page, rowLabel).all();
        if (menus.isEmpty()) {
            return List.of();
        }
        return menus.getFirst().getItems("Actions for " + rowLabel);
    }

    // ---- page text ---------------------------------------------------------

    /** Every open dialog's text: a dialog renders outside the view column. */
    private static String dialogsText(UnifiedPage page) {
        return page.find(UnifiedDialog.class).all().stream()
                .filter(UnifiedDialog::isOpen)
                .map(dialog -> dialog.element().getText())
                .reduce("", (a, b) -> a + "\n" + b);
    }

    /** What the user can read: the view column plus any open dialog. */
    private static String uiText(UnifiedPage page) {
        return view(page).element().getText() + "\n" + dialogsText(page);
    }

    /** The view or an open dialog says {@code text} (waiting until it does). */
    private static void assertUiSays(UnifiedPage page, String... texts) {
        for (String text : texts) {
            page.waitUntil(() -> uiText(page).contains(text));
        }
    }

    /** Neither the view nor an open dialog says {@code text} (waiting until so). */
    private static void assertUiDoesNotSay(UnifiedPage page, String... texts) {
        for (String text : texts) {
            page.waitUntil(() -> !uiText(page).contains(text));
        }
    }
}
