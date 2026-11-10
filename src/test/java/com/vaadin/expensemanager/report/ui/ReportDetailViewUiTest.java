package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.base.ui.unified.UnifiedBigDecimalField;
import com.vaadin.expensemanager.base.ui.unified.UnifiedDatePickerInput;
import com.vaadin.expensemanager.base.ui.unified.UnifiedDateTimePicker;
import com.vaadin.expensemanager.base.ui.unified.UnifiedDiv;
import com.vaadin.expensemanager.base.ui.unified.UnifiedElements;
import com.vaadin.expensemanager.base.ui.unified.UnifiedFormLayout;
import com.vaadin.expensemanager.base.ui.unified.UnifiedHorizontalLayout;
import com.vaadin.expensemanager.base.ui.unified.UnifiedIntegerField;
import com.vaadin.expensemanager.base.ui.unified.UnifiedMenuBar;
import com.vaadin.expensemanager.base.ui.unified.UnifiedSpan;
import com.vaadin.expensemanager.base.ui.unified.UnifiedTextArea;
import com.vaadin.expensemanager.base.ui.unified.UnifiedUpload;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.QuantityOverride;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseLineDto;
import com.vaadin.expensemanager.report.service.ReportDetailDto;
import com.vaadin.expensemanager.report.service.TravelDto;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedCheckbox;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedRadioButtonGroup;
import com.vaadin.testing.unified.component.UnifiedTextField;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0027 proposed) for {@link ReportDetailView}
 * (UC-001/UC-005, ADR-0019) and its dialogs — the line editor, the travel editor, the
 * override and receipt dialogs. Runs as the seeded plain user via
 * {@link WithUserDetails}, unchanged on Browserless and Playwright.
 *
 * <p>Covers the create path (transient working copy, date defaulting to today,
 * first save persisting a DRAFT and routing to {@code /report/{id}}), the
 * always-enabled-Save + validation-error-summary rule (no disabled submit,
 * ADR-0020), report-level editing, submit/resubmit, receipts, the travel calculator,
 * quantity overrides and their clearing, and the draft-only delete returning to the
 * list. Persisted state is asserted through {@link #service}, which is in the test
 * JVM on every Testing Tool.
 *
 * <p>Written around what differs between the tools:
 * <ul>
 *   <li><b>Only visible components count.</b> Browserless's queries skip hidden
 *       components; a browser's find also returns elements with {@code hidden}. Every
 *       "is it offered" check and every click goes through a visible filter
 *       ({@link #hasButton}, {@link #click}).</li>
 *   <li><b>Text.</b> The view's text is {@code innerText} in a browser, so it never
 *       includes a dialog's (dialogs are overlays, outside the view on both tools), and
 *       dialog text is read from the open dialogs ({@link #dialogText}). Exact matches
 *       on a span compare its DOM text ({@link #rawText}), not {@code innerText}:
 *       {@code .section-label} is {@code text-transform: uppercase} (F-086).</li>
 *   <li><b>Nested dialogs.</b> Since 25.3 a dialog opened from a modal dialog nests
 *       inside it on both tools (F-082), so the outer editor's text contains the
 *       confirm's; {@link #openDialogSaying} picks the innermost by taking the
 *       shortest matching text.</li>
 *   <li><b>Configuration the browser never sees</b> (date-time picker i18n error
 *       messages) is the one Browserless-only test; see
 *       {@link #travelPickersCarryIncompleteAndBadInputErrorMessages}.</li>
 * </ul>
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class ReportDetailViewUiTest extends AbstractReportUiTest {

    /** For the one test that creates an expense type (as the admin). */
    @Autowired
    private ReferenceDataService referenceData;

    @Test
    void newReportSaveWithDefaultsPersistsTodayDatedDraftAndRoutesToId(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        screenshot(page, "new-report");

        // No field touched: the date defaults to today; first save persists.
        click(page, "Save");

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        assertThat(mine.getFirst().reportDate()).isEqualTo(LocalDate.now());
        // Still on the detail view — now the persisted /report/{id} form.
        assertThat(page).hasPath("report/" + mine.getFirst().id());
        screenshot(page, "saved-draft");
    }

    @Test
    void missingRequiredDateShowsErrorSummaryAndPersistsNothing(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        // A user emptying the required date, then saving.
        page.find(UnifiedDatePicker.class).withLabel("Report date").single().clear();
        click(page, "Save");
        screenshot(page, "error-summary");

        // Always-enabled Save + top-of-form error summary (ADR-0020).
        assertThat(viewText(page))
                .contains("Report date is required");
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void reportDateCarriesABadInputErrorMessage(UnifiedPage page) {
        // Issue #85: typing an unparseable date (e.g. "dsdds") makes the picker
        // invalid; without a bad-input message the error reaches the summary blank.
        // The Browserless version could only read the configured message; the bad-input
        // message is server-side i18n, never sent to the browser. Now the test types the
        // garbage and checks the message the summary actually carries.
        page.navigate(ReportDetailView.class);

        page.find(UnifiedDatePickerInput.class).withLabel("Report date").single()
                .typeText("dsdds");
        click(page, "Save");
        screenshot(page, "bad-input-summary");

        assertThat(viewText(page)).contains("Enter a valid date");
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void editingExistingReportUpdatesItsFields(UnifiedPage page) {
        var id = fixtures.seedReport(LocalDate.of(2026, 7, 1), "before");
        page.navigate(ReportDetailView.class, id);

        textArea(page, "Additional Info").setValue("after");
        page.find(UnifiedDatePicker.class).withLabel("Report date").single()
                .setValue(LocalDate.of(2026, 7, 20));
        screenshot(page, "edited");
        click(page, "Save");

        var reloaded = service.findMine(id);
        assertThat(reloaded.additionalInformation()).isEqualTo("after");
        assertThat(reloaded.reportDate()).isEqualTo(LocalDate.of(2026, 7, 20));
    }

    @Test
    void deletingADraftRemovesItAndReturnsToTheList(UnifiedPage page) {
        var id = fixtures.seedReport(LocalDate.of(2026, 7, 1), "scratch");
        page.navigate(ReportDetailView.class, id);

        click(page, "Delete");          // opens the confirm dialog
        screenshot(page, "delete-confirm");
        click(page, "Delete report");   // confirms

        assertThat(page).isAt(MyReportsView.class);
        assertThat(service.listMine()).noneMatch(r -> r.id().equals(id));
    }

    @Test
    void newReportDoesNotOfferDelete(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        // Delete is hidden until the report is a persisted DRAFT.
        assertThat(hasButton(page, "Delete")).isFalse();
    }

    @Test
    void addingALineViaTheDialogUpdatesLiveTotalsAndPersistsOnSave(UnifiedPage page) {
        var id = fixtures.seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        clickNamed(page, "Add expense");
        comboBox(page, "Expense type")
                .selectItem("Parking/supplies/goods");
        comboBox(page, "VAT rate").selectItem("25.5 %");
        bigDecimalField(page, "Unit price")
                .setValue(new BigDecimal("100"));
        screenshot(page, "line-editor-filled");
        saveDialog(page);

        // Live total bar reflects the added line before the report is saved.
        assertThat(viewText(page)).contains("€100.00");
        screenshot(page, "line-added");

        click(page, "Save");

        var reloaded = service.findMine(id);
        assertThat(reloaded.lines()).hasSize(1);
        assertThat(reloaded.lines().getFirst().amount()).isEqualByComparingTo("100.00");
        assertThat(reloaded.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void lineEditorDefaultsQuantityToOneAndShowsTheLineTotalLive(UnifiedPage page) {
        var id = fixtures.seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        clickNamed(page, "Add expense");

        // Quantity starts at 1, so the line total is the unit price itself.
        assertThat(bigDecimalField(page, "Quantity").getValue())
                .isEqualByComparingTo("1");
        bigDecimalField(page, "Unit price")
                .setValue(new BigDecimal("12.50"));
        assertThat(dialogText(page))
                .contains("Total").contains("€12.50");

        // Raising the quantity recomputes the total before anything is saved.
        bigDecimalField(page, "Quantity").setValue(new BigDecimal("3"));
        assertThat(dialogText(page)).contains("€37.50");
    }

    @Test
    void lineEditorLaysItsFieldsOutAsTheDesignsTwoColumnGrid(UnifiedPage page) {
        // docs/design/components/line-editor-dialog.md § Layout. Everything spans
        // both columns except unit price/quantity, which pair, and VAT rate, which
        // is drawn half width with the cell beside it deliberately empty.
        page.navigate(ReportDetailView.class);
        clickNamed(page, "Add expense");
        screenshot(page, "line-editor");

        var dialog = dialog(page);
        assertThat(dialog.element().getProperty("width")).isEqualTo("26rem");

        var form = dialog.find(UnifiedFormLayout.class).single();
        // The steps the spec prescribes, as minWidth:columns.
        assertThat(form.getResponsiveSteps()).containsExactly("0:1", "24rem:2");
        assertThat(form.getColumnSpacing()).isEqualTo("var(--em-section-gap)");
        assertThat(form.getRowSpacing()).isEqualTo("var(--em-card-padding)");

        assertThat(colspan(comboBox(page, "Expense type"))).isEqualTo(2);
        assertThat(colspan(bigDecimalField(page, "Unit price"))).isEqualTo(1);
        assertThat(colspan(bigDecimalField(page, "Quantity"))).isEqualTo(1);
        assertThat(colspan(comboBox(page, "VAT rate"))).isEqualTo(1);
        assertThat(colspan(textArea(page, "Comment"))).isEqualTo(2);
        // The total row and the receipt block are grid children too, both full width.
        assertThat(colspan(form.find(UnifiedHorizontalLayout.class)
                .withClassName("line-total-row").single())).isEqualTo(2);
        // The receipt block is the grid's one vertical layout, holding the Upload.
        var receiptBlock = form.getChildren().stream()
                .filter(child -> "vaadin-vertical-layout".equals(child.getTag()))
                .toList();
        assertThat(receiptBlock).singleElement().satisfies(block ->
                assertThat(UnifiedFormLayout.colspanOf(block)).isEqualTo(2));
        assertThat(form.find(UnifiedUpload.class).exists()).isTrue();
    }

    @Test
    void lineEditorUsesTheDesignsFieldTypesAndCopy(UnifiedPage page) {
        // Delta items 4, 9, 10, 11 of #175, plus the component mapping: the frame
        // hides the header close button, a third footer button and a checkbox.
        page.navigate(ReportDetailView.class);
        clickNamed(page, "Add expense");

        var comment = textArea(page, "Comment");
        assertThat(comment.getMinRows()).isEqualTo(2);
        assertThat(comment.getMaxLength()).isEqualTo(500);

        assertThat(bigDecimalField(page, "Unit price").getHelperText())
                .isEqualTo("Gross, each");
        assertThat(dialogText(page))
                .contains("Total").doesNotContain("Line total");

        assertThat(hasButton(dialog(page), "Save")).isTrue();
        assertThat(hasButton(page, "Save expense")).isFalse();
        assertThat(dialog(page).find(UnifiedCheckbox.class).exists()).isFalse();
    }

    @Test
    void lineEditorShowsNoReceiptStatusUntilOneIsAttached(UnifiedPage page) {
        // Delta item 12: the empty state carries no status text — the Upload control
        // already says it, and the design draws none.
        page.navigate(ReportDetailView.class);
        clickNamed(page, "Add expense");

        assertThat(dialogText(page))
                .doesNotContain("No receipt attached");
        assertThat(hasButton(page, "Remove")).isFalse();

        upload(page).upload("taxi.jpg", "image/jpeg", ReportFixtures.jpegBytes());
        screenshot(page, "receipt-attached");

        assertThat(dialogText(page))
                .contains("taxi.jpg");
        assertThat(hasButton(page, "Remove")).isTrue();
    }

    @Test
    void lineEditorPutsTheUploadGlyphInTheDropLabelNotOnTheButton(UnifiedPage page) {
        // Delta item 13, and iconography.md's Lucide-only rule (#163): the drawn
        // anatomy slots the glyph beside the drop label and leaves the button plain.
        page.navigate(ReportDetailView.class);
        clickNamed(page, "Add expense");

        var upload = upload(page);
        var button = upload.find(UnifiedButton.class).single();
        assertThat(button.getText()).isEqualTo("Upload receipt…");
        // Plain text: the label's Text node and nothing else — no icon child.
        assertThat(UnifiedElements.children(button.element())).isEmpty();
        // The upload's one Lucide icon sits in the drop-label-icon slot.
        var icons = upload.findComponent(SvgIcon.class).all().stream()
                .filter(icon -> icon.element().getProperty("symbol") != null).toList();
        assertThat(icons).singleElement().satisfies(icon -> {
            assertThat(icon.element().getAttribute("slot")).isEqualTo("drop-label-icon");
            assertThat(icon.element().getProperty("symbol"))
                    .isEqualTo(LucideIcon.UPLOAD.glyph());
        });
    }

    @Test
    void aQuantityLinePersistsItsUnitPriceAndTotalsTheProduct(UnifiedPage page) {
        var id = fixtures.seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        clickNamed(page, "Add expense");
        comboBox(page, "Expense type")
                .selectItem("Parking/supplies/goods");
        comboBox(page, "VAT rate").selectItem("25.5 %");
        bigDecimalField(page, "Unit price")
                .setValue(new BigDecimal("100"));
        bigDecimalField(page, "Quantity").setValue(new BigDecimal("3"));
        saveDialog(page);

        // The card shows the qty × unit = gross breakdown and the live total bar
        // reflects the product, both before the report is saved.
        var shown = viewText(page);
        assertThat(shown).contains("3 × €100.00 = €300.00").contains("€300.00");

        click(page, "Save");

        var line = service.findMine(id).lines().getFirst();
        assertThat(line.amount()).isEqualByComparingTo("100.00");     // unit price
        assertThat(line.quantity()).isEqualByComparingTo("3.00");
        assertThat(service.findMine(id).total()).isEqualByComparingTo("300.00");
    }

    @Test
    void aQuantityOneCardShowsNoQuantityBreakdown(UnifiedPage page) {
        // The feature is invisible until used (ADR-0023).
        var id = fixtures.seedReportWithLine(LocalDate.of(2026, 7, 1), "100.00");
        page.navigate(ReportDetailView.class, id);

        assertThat(viewText(page))
                .contains("€100.00")
                .doesNotContain("1 × €100.00");
    }

    @Test
    void aNonPositiveQuantityIsBlockedWithAnErrorSummary(UnifiedPage page) {
        var id = fixtures.seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        clickNamed(page, "Add expense");
        comboBox(page, "Expense type")
                .selectItem("Parking/supplies/goods");
        comboBox(page, "VAT rate").selectItem("25.5 %");
        bigDecimalField(page, "Unit price")
                .setValue(new BigDecimal("100"));
        bigDecimalField(page, "Quantity").setValue(BigDecimal.ZERO);
        // Always-enabled Save (ADR-0020): the click is allowed, the reason shows.
        saveDialog(page);

        assertThat(dialogText(page))
                .contains("Quantity must be greater than zero");
        assertThat(service.findMine(id).lines()).isEmpty();
    }

    @Test
    void editingALineLoadsAndUpdatesItsQuantity(UnifiedPage page) {
        var id = fixtures.seedReportWithLine(LocalDate.of(2026, 7, 1), "50.00");
        page.navigate(ReportDetailView.class, id);

        // Open the existing (quantity-1) line and give it a quantity. The card body
        // itself carries the click listener (no Edit button), so click that.
        openLineCardEditor(page);
        assertThat(bigDecimalField(page, "Quantity").getValue())
                .isEqualByComparingTo("1");
        bigDecimalField(page, "Quantity").setValue(new BigDecimal("4"));
        saveDialog(page);
        click(page, "Save");

        var line = service.findMine(id).lines().getFirst();
        assertThat(line.amount()).isEqualByComparingTo("50.00");
        assertThat(line.quantity()).isEqualByComparingTo("4.00");
        assertThat(service.findMine(id).total()).isEqualByComparingTo("200.00");
    }

    @Test
    void lineEditorKeepsSaveEnabledAndBlocksAnEmptyLine(UnifiedPage page) {
        var id = fixtures.seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        clickNamed(page, "Add expense");
        // Always-enabled Save (ADR-0020): clicking it with nothing filled must
        // not add a line — validation blocks it and the dialog stays open.
        saveDialog(page);

        // Dialog is still open (validation blocked the save) and its Save is still
        // enabled — never a disabled submit.
        assertThat(hasOpenDialog(page)).isTrue();
        assertThat(buttons(dialog(page), "Save").single().isEnabled()).isTrue();
        // … and no line was added: the empty state still shows, nothing persisted.
        assertThat(viewText(page))
                .contains("No expenses yet");
        assertThat(service.findMine(id).lines()).isEmpty();
    }

    @Test
    void submittingADraftWithALineLocksItReadOnly(UnifiedPage page) {
        var id = fixtures.seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        click(page, "Submit for approval"); // opens the confirm dialog
        click(page, "Submit report");       // confirms

        // The report is now SUBMITTED and read-only to the owner: the editing
        // affordances are gone and the status is shown as text (ADR-0020).
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(viewText(page))
                .contains("Submitted");
        assertThat(hasButton(page, "Save")).isFalse();
        assertThat(hasButton(page, "Submit for approval")).isFalse();
        assertThat(hasNamed(page, "Add expense")).isFalse();
        assertThat(hasButton(page, "Delete")).isFalse();
    }

    @Test
    void submittingAnEmptyDraftShowsTheReasonAndStaysEnabled(UnifiedPage page) {
        var id = fixtures.seedReport(LocalDate.of(2026, 7, 1), "empty");
        page.navigate(ReportDetailView.class, id);

        // Always-enabled Submit (ADR-0020): a zero-line submit is blocked with the
        // reason in the top-of-form error summary, not a silent no-op. The report
        // date is valid, so the confirm dialog opens; the domain guard fires on
        // confirm and the atomic save+submit rolls back (issue #81).
        click(page, "Submit for approval");
        click(page, "Submit report");

        assertThat(viewText(page))
                .contains("at least one line");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
        // Submit button is still there (never disabled) so the owner can retry.
        assertThat(hasButton(page, "Submit for approval")).isTrue();
    }

    // --- Submit saves the current state + confirmation dialog (issue #81) ---

    @Test
    void submittingPersistsAReportLevelEditThatWasNotSavedFirst(UnifiedPage page) {
        var id = fixtures.seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        // Edit a report-level field but never click Save — the edit lives only in
        // the working copy until the submit persists it (issue #81).
        textArea(page, "Additional Info")
                .setValue("edited but not saved");

        click(page, "Submit for approval"); // opens the confirm dialog
        click(page, "Submit report");       // confirms

        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.additionalInformation()).isEqualTo("edited but not saved");
    }

    @Test
    void submittingPersistsALineAddedButNotSavedFirst(UnifiedPage page) {
        // A seed with no lines: before issue #81 a line added in the dialog but not
        // Saved was lost on submit, so the submit failed the "≥1 line" guard. Now
        // the submit saves the pending line first, so it succeeds.
        var id = fixtures.seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        clickNamed(page, "Add expense");
        comboBox(page, "Expense type")
                .selectItem("Parking/supplies/goods");
        comboBox(page, "VAT rate").selectItem("25.5 %");
        bigDecimalField(page, "Unit price")
                .setValue(new BigDecimal("100"));
        saveDialog(page);

        click(page, "Submit for approval");
        click(page, "Submit report");

        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.lines()).hasSize(1);
        assertThat(reloaded.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void cancellingTheSubmitConfirmationLeavesItAnEditableDraft(UnifiedPage page) {
        var id = fixtures.seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        click(page, "Submit for approval"); // opens the dialog
        click(page, "Cancel");              // backs out

        // Nothing submitted: still an editable DRAFT with both actions offered.
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
        assertThat(hasButton(page, "Submit for approval")).isTrue();
        assertThat(hasButton(page, "Save")).isTrue();
    }

    @Test
    void submittingWithNoReportDateBlocksBeforeConfirming(UnifiedPage page) {
        var id = fixtures.seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        // Clearing the required date must block the submit at validation — the
        // confirm dialog never opens and nothing transitions.
        page.find(UnifiedDatePicker.class).withLabel("Report date").single().clear();
        click(page, "Submit for approval");

        assertThat(hasButton(page, "Submit report")).isFalse();
        assertThat(viewText(page))
                .contains("Report date is required");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
    }

    @Test
    void aRejectedReportShowsTheRealReasonRejecterAndDateToTheOwner(UnifiedPage page) {
        var id = fixtures.seedRejectedReport(LocalDate.of(2026, 7, 1), "100",
                "Please attach the hotel receipt.");
        page.navigate(ReportDetailView.class, id);

        // The owner's callout carries the real reason, who rejected it (the seeded
        // admin), and when — replacing the placeholder note. The reason and the
        // rejecter also appear in the status-history audit trail below.
        var text = viewText(page);
        assertThat(text).contains("Rejected — changes requested",
                "Please attach the hotel receipt.", "Expense Admin", "14 Jul 2026");
        // Ignoring case: .section-label is uppercased in CSS, which a browser's text shows.
        assertThat(text).containsIgnoringCase("Status history");
        // A rejected report is editable again (owner can resubmit), so Save shows.
        assertThat(hasButton(page, "Save")).isTrue();
    }

    // --- Resubmit a rejected report (Phase 5.5) ---

    @Test
    void aRejectedReportOffersResubmitNotSubmit(UnifiedPage page) {
        var id = fixtures.seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Fix the total.");
        page.navigate(ReportDetailView.class, id);

        // The forward action is relabelled "Resubmit" on a REJECTED report; the
        // first-submit label is gone (same button, flipped by status).
        assertThat(hasButton(page, "Resubmit")).isTrue();
        assertThat(hasButton(page, "Submit for approval")).isFalse();
    }

    @Test
    void resubmitIsNotOfferedOnDraftSubmittedOrApproved(UnifiedPage page) {
        // DRAFT offers the first-submit label, never "Resubmit".
        var draft = fixtures.seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, draft);
        assertThat(hasButton(page, "Submit for approval")).isTrue();
        assertThat(hasButton(page, "Resubmit")).isFalse();

        // SUBMITTED is read-only — no forward action of either label.
        var submitted = fixtures.seedSubmittedReport(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, submitted);
        assertThat(hasButton(page, "Resubmit")).isFalse();
        assertThat(hasButton(page, "Submit for approval")).isFalse();

        // APPROVED is terminal and read-only — likewise no forward action.
        var approved = fixtures.seedApprovedReport(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, approved);
        assertThat(hasButton(page, "Resubmit")).isFalse();
        assertThat(hasButton(page, "Submit for approval")).isFalse();
    }

    @Test
    void resubmittingARejectedReportSendsItBackToSubmittedAndLocksItReadOnly(UnifiedPage page) {
        var id = fixtures.seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Fix the total.");
        page.navigate(ReportDetailView.class, id);

        click(page, "Resubmit");         // opens the confirm dialog
        click(page, "Resubmit report");  // confirms

        // Back through the queue: REJECTED → SUBMITTED, and read-only to the owner
        // again (the editing affordances and the forward action are gone).
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(viewText(page))
                .contains("Submitted");
        assertThat(hasButton(page, "Resubmit")).isFalse();
        assertThat(hasButton(page, "Save")).isFalse();
        assertThat(hasNamed(page, "Add expense")).isFalse();
    }

    @Test
    void resubmittingPersistsAnEditThatWasNotSavedFirst(UnifiedPage page) {
        var id = fixtures.seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Add detail.");
        page.navigate(ReportDetailView.class, id);

        // Address the feedback but don't click Save — the resubmit must save it
        // as part of the transition (issue #81).
        textArea(page, "Additional Info")
                .setValue("addressed the feedback");

        click(page, "Resubmit");
        click(page, "Resubmit report");

        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.additionalInformation()).isEqualTo("addressed the feedback");
    }

    @Test
    void resubmittingAnEmptyRejectedReportShowsTheReasonAndStaysEnabled(UnifiedPage page) {
        var id = fixtures.seedEmptyRejectedReport(LocalDate.of(2026, 7, 1), "Fix the total.");
        page.navigate(ReportDetailView.class, id);

        // Always-enabled Resubmit (ADR-0020): a zero-line resubmit is blocked with
        // the reason in the top-of-form error summary, not a silent no-op. The
        // confirm dialog opens (the date is valid); the domain guard fires on
        // confirm and the atomic save+resubmit rolls back (issue #81).
        click(page, "Resubmit");
        click(page, "Resubmit report");

        assertThat(viewText(page))
                .contains("at least one line");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.REJECTED);
        // The button is still there (never disabled) so the owner can add a line and retry.
        assertThat(hasButton(page, "Resubmit")).isTrue();
    }

    @Test
    void anAlreadySubmittedReportOpensReadOnly(UnifiedPage page) {
        var id = fixtures.seedSubmittedReport(LocalDate.of(2026, 7, 1), "80.00");
        page.navigate(ReportDetailView.class, id);

        assertThat(viewText(page))
                .contains("Submitted");
        assertThat(hasButton(page, "Save")).isFalse();
        assertThat(hasButton(page, "Submit for approval")).isFalse();
        assertThat(hasButton(page, "Delete")).isFalse();
        assertThat(hasNamed(page, "Add expense")).isFalse();
        // The line is shown but carries no ⋮ menu at all — not a disabled one.
        assertThat(rowMenus(page)).isEmpty();
    }

    @Test
    void uploadingAReceiptOnAnUnsavedReportPersistsItOnFirstSave(UnifiedPage page) {
        // Brand-new, never-saved report: the receipt attaches at any time, no
        // "save first" gate (ADR-0021 overrides ADR-0019's save-before-attach).
        page.navigate(ReportDetailView.class);

        clickNamed(page, "Add expense");
        comboBox(page, "Expense type")
                .selectItem("Parking/supplies/goods");
        comboBox(page, "VAT rate").selectItem("25.5 %");
        bigDecimalField(page, "Unit price")
                .setValue(new BigDecimal("100"));
        // Drive the real UploadHandler: bytes are validated server-side by magic
        // bytes and buffered in memory (the browser mime is not trusted).
        upload(page).upload("taxi.jpg", "image/jpeg", ReportFixtures.jpegBytes());
        saveDialog(page);

        // The card reflects the buffered receipt before the report is even saved —
        // as an image thumbnail (its filename is the accessible name), matching how
        // a persisted receipt renders (issue #89).
        assertThat(hasNamed(page, "Preview receipt: taxi.jpg"))
                .isTrue();

        click(page, "Save");

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        var line = service.findMine(mine.getFirst().id()).lines().getFirst();
        assertThat(line.hasReceipt()).isTrue();
        assertThat(line.receiptFilename()).isEqualTo("taxi.jpg");
        assertThat(line.receiptContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void aMislabeledUploadIsRejectedAndNothingIsBuffered(UnifiedPage page) {
        var id = fixtures.seedReport(LocalDate.of(2026, 7, 1), "trip");
        page.navigate(ReportDetailView.class, id);

        clickNamed(page, "Add expense");
        comboBox(page, "Expense type")
                .selectItem("Parking/supplies/goods");
        comboBox(page, "VAT rate").selectItem("25.5 %");
        bigDecimalField(page, "Unit price")
                .setValue(new BigDecimal("100"));
        // A text file renamed to .jpg: magic-byte check rejects it at upload; the
        // control is never disabled, the reason is surfaced instead (ADR-0020).
        upload(page).upload("fake.jpg", "image/jpeg", "not an image".getBytes());
        saveDialog(page);
        click(page, "Save");

        // The line saved, but with no receipt — the rejected bytes were dropped.
        assertThat(service.findMine(id).lines().getFirst().hasReceipt()).isFalse();
    }

    @Test
    void aSubmittedReportShowsTheReceiptSummaryButOffersNoUpload(UnifiedPage page) {
        var id = fixtures.seedSubmittedReportWithReceipt(LocalDate.of(2026, 7, 1), "50.00",
                "hotel.jpg");
        page.navigate(ReportDetailView.class, id);

        // The receipt is viewable (read path, ADR-0021): its image preview shows
        // even on a read-only report. But every mutation surface is gone — no Add
        // expense to open the editor, so the receipt cannot be changed.
        assertThat(hasNamed(page, "Preview receipt: hotel.jpg"))
                .isTrue();
        assertThat(hasNamed(page, "Add expense")).isFalse();
        assertThat(rowMenus(page)).isEmpty();
    }

    @Test
    void aSavedImageReceiptRendersAnEnlargeablePreview(UnifiedPage page) {
        // A persisted image receipt: the card shows a keyboard-operable thumbnail
        // button (accessible name) that streams from the DB and enlarges in a
        // dialog (ADR-0021 / ADR-0020) — visible even on a read-only report.
        var id = fixtures.seedSubmittedReportWithReceipt(LocalDate.of(2026, 7, 1), "50.00",
                "hotel.jpg");
        page.navigate(ReportDetailView.class, id);

        assertThat(hasNamed(page, "Preview receipt: hotel.jpg"))
                .isTrue();
        // Since #172 a row shows a paperclip chip, not a thumbnail; activating it must
        // enlarge the receipt image itself.
        assertPreviewEnlarges(page, "hotel.jpg");
    }

    @Test
    void aSavedPdfReceiptOffersAnOpenAffordance(UnifiedPage page) {
        var id = fixtures.seedReportWithReceipt(LocalDate.of(2026, 7, 1), "70.00",
                "invoice.pdf", ReportFixtures.pdfBytes());
        page.navigate(ReportDetailView.class, id);

        // A PDF is not thumbnailed. On a row it is the design's chip — a paperclip
        // and the filename — and activating it still opens the browser's PDF viewer
        // in a new tab (ADR-0021); the "open" verb moved to the accessible name.
        assertThat(viewText(page))
                .contains("invoice.pdf");
        assertThat(page.findComponent(Anchor.class).withClassName("expense-row-attachment")
                .single().element().getAttribute("aria-label"))
                .isEqualTo("Open receipt: invoice.pdf");
    }

    @Test
    void anUnsavedImageAttachmentPreviewsFromBufferedBytes(UnifiedPage page) {
        // Brand-new report, receipt attached but nothing saved yet: the editor
        // previews the buffered bytes directly (no DB round-trip, no receipt id).
        page.navigate(ReportDetailView.class);

        clickNamed(page, "Add expense");
        comboBox(page, "Expense type")
                .selectItem("Parking/supplies/goods");
        comboBox(page, "VAT rate").selectItem("25.5 %");
        bigDecimalField(page, "Unit price")
                .setValue(new BigDecimal("100"));
        upload(page).upload("taxi.jpg", "image/jpeg", ReportFixtures.jpegBytes());

        // The preview affordance appears in the still-open dialog, before any save.
        assertThat(hasNamed(page, "Preview receipt: taxi.jpg"))
                .isTrue();
    }

    @Test
    void anUnsavedImageAttachmentPreviewsOnTheCardBeforeSave(UnifiedPage page) {
        // Issue #89: after the line editor closes, the card must show the image
        // thumbnail from the buffered bytes right away — not only after the report
        // is saved (previously the card showed just a "📎 filename" chip until the
        // receipt was persisted and had an id).
        page.navigate(ReportDetailView.class);

        clickNamed(page, "Add expense");
        comboBox(page, "Expense type")
                .selectItem("Parking/supplies/goods");
        comboBox(page, "VAT rate").selectItem("25.5 %");
        bigDecimalField(page, "Unit price")
                .setValue(new BigDecimal("100"));
        upload(page).upload("taxi.jpg", "image/jpeg", ReportFixtures.jpegBytes());
        saveDialog(page);

        // Dialog is closed; the preview button now belongs to the line card, and it
        // renders before the report has ever been saved (no receipt id yet).
        assertThat(hasOpenDialog(page)).isFalse();
        assertThat(hasNamed(page, "Preview receipt: taxi.jpg"))
                .isTrue();
        // Since #172 a row shows a paperclip chip, not a thumbnail; activating it must
        // enlarge the receipt image itself.
        assertPreviewEnlarges(page, "taxi.jpg");
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

        clickNamed(page, "Add travel");
        dateTimePicker(page, "Departure").setValue(DEP);
        dateTimePicker(page, "Return").setValue(DEP.plusHours(11));
        comboBox(page, "Destination country")
                .selectItem("Finland (domestic)");
        textField(page, "Destinations").setValue("Helsinki");
        textField(page, "Travel purpose").setValue("Client visit");

        // The per-diem previews live as the dates are filled — no separate compute
        // step (11 h → one full day €54). The preview lives in the dialog overlay
        // (outside the view), so assert against the open dialog's text.
        assertThat(dialogText(page))
                .contains("Per diem allowance (full day)", "€54.00", "Trip total");

        saveDialog(page);

        // The trip card + the live "Per diem allowance" subtotal row appear before
        // the report is even saved.
        var text = viewText(page);
        assertThat(text).contains("Client visit", "Helsinki, Finland",
                "Per diem allowance", "€54.00");

        click(page, "Save");

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

        clickNamed(page, "Add travel");
        dateTimePicker(page, "Departure").setValue(DEP);
        dateTimePicker(page, "Return").setValue(DEP.plusHours(11));
        // The picker lists the seeded foreign countries alongside Finland; pick one.
        var country = comboBox(page, "Destination country");
        assertThat(country.getItems())
                .contains("Finland (domestic)", "Germany", "Sweden");
        country.selectItem("Germany");
        textField(page, "Destinations").setValue("Berlin");
        textField(page, "Travel purpose").setValue("Conference");

        // Costed against Germany's €71.00/day, not the Finnish €54.00.
        assertThat(dialogText(page))
                .contains("Per diem allowance (full day)", "€71.00", "Germany");

        saveDialog(page);
        // The chosen country shows on the Trip & Allowance card ("destinations, country").
        assertThat(viewText(page))
                .contains("Berlin, Germany", "€71.00");

        click(page, "Save");

        var loaded = service.findMine(service.listMine().getFirst().id());
        assertThat(loaded.travels().getFirst().country()).isEqualTo("Germany");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("71.00");
    }

    @Test
    void aTripCannotBeSavedWithNoDestinationCountryChosen(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        clickNamed(page, "Add travel");
        dateTimePicker(page, "Departure").setValue(DEP);
        dateTimePicker(page, "Return").setValue(DEP.plusHours(11));
        textField(page, "Destinations").setValue("Helsinki");
        textField(page, "Travel purpose").setValue("Client visit");
        // Country left unchosen: Save surfaces a clear message and generates nothing.
        saveDialog(page);

        assertThat(dialogText(page))
                .contains("Destination country is required");
        assertThat(hasButton(dialog(page), "Save")).isTrue();
        assertThat(hasSpan(page, "Per diem allowance")).isFalse();
    }

    @Test
    void insertingATripWithKmMealParkingShowsEachSubtotalAndFoldsParkingIntoNetVat(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        clickNamed(page, "Add travel");
        dateTimePicker(page, "Departure").setValue(DEP);
        dateTimePicker(page, "Return").setValue(DEP.plusHours(11));
        comboBox(page, "Destination country")
                .selectItem("Finland (domestic)");
        textField(page, "Destinations").setValue("Helsinki");
        textField(page, "Travel purpose").setValue("Client visit");
        bigDecimalField(page, "Kilometre allowance (km)")
                .setValue(new BigDecimal("120"));
        // A meal allowance is only paid when no per-diem applies (issue #93), so
        // clicking it marks the trip not eligible — the per-diem line drops out and
        // the meal-allowance line takes its place.
        checkbox(page, "Pay meal allowance?").check();
        bigDecimalField(page, "Parking fees (€)")
                .setValue(new BigDecimal("12.00"));

        // The remaining outputs preview live in the dialog (120 km × €0.55 = €66.00,
        // etc.); no per-diem, since the trip is now not eligible for one.
        var previewText = dialogText(page);
        assertThat(previewText).contains("Kilometre allowance", "€66.00",
                "Meal allowance", "€13.50", "Parking", "€12.00");
        assertThat(previewText).doesNotContain("Per diem allowance");

        saveDialog(page);

        // The two new tax-free subtotal rows are visible; parking is not a subtotal.
        assertThat(hasSpan(page, "Kilometre allowance")).isTrue();
        assertThat(hasSpan(page, "Meal allowance")).isTrue();
        assertThat(hasSpan(page, "Per diem allowance")).isFalse();
        var text = viewText(page);
        assertThat(text).contains("€66.00", "€13.50");

        click(page, "Save");

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
        var id = fixtures.seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("12.5"), false, BigDecimal.ZERO);
        page.navigate(ReportDetailView.class, id);

        var shown = viewText(page);
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
    void reCostingATripUpdatesItsKilometreCardWithoutLeavingAStaleOne(UnifiedPage page) {
        var id = fixtures.seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("12.5"), false, BigDecimal.ZERO);
        page.navigate(ReportDetailView.class, id);

        // Re-cost the trip through its editor: 12.5 km → 120 km.
        clickRowAction(page, TRIP, "Edit");
        bigDecimalField(page, "Kilometre allowance (km)")
                .setValue(new BigDecimal("120"));
        saveDialog(page);

        var shown = viewText(page);
        assertThat(shown).contains("120 × €0.55 = €66.00")
                .doesNotContain("12.5 × €0.55");

        click(page, "Save");

        var loaded = service.findMine(id);
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("66.00");
        assertThat(loaded.travels().getFirst().generatedLines()).hasSize(2);
    }

    @Test
    void aTripWithoutKmOrMealHidesThoseSubtotalRows(UnifiedPage page) {
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);

        // Per-diem-only trip: its row shows, but the km/meal rows stay hidden.
        assertThat(hasSpan(page, "Per diem allowance")).isTrue();
        assertThat(hasSpan(page, "Kilometre allowance")).isFalse();
        assertThat(hasSpan(page, "Meal allowance")).isFalse();
    }

    @Test
    void aTripListsItsGeneratedLinesAndAReceiptCanBeAttachedToOne(UnifiedPage page) {
        var id = fixtures.seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00"));
        page.navigate(ReportDetailView.class, id);

        // Each earned line is listed under the trip with an attach affordance.
        assertThat(rowActions(page, "Per diem allowance (full day)"))
                .contains("Add receipt");
        assertThat(rowActions(page, "Parking")).contains("Add receipt");

        // Attach a receipt to the parking line via its focused editor.
        clickRowAction(page, "Parking", "Add receipt");
        upload(page).upload("parking.jpg", "image/jpeg", ReportFixtures.jpegBytes());
        click(page, "Save receipt");

        // Issue #89: the buffered receipt previews as a thumbnail on the row right
        // away — before the report is even saved — not just as a filename chip.
        assertThat(hasNamed(page, "Preview receipt: parking.jpg"))
                .isTrue();
        // Since #172 a row shows a paperclip chip, not a thumbnail; activating it must
        // enlarge the receipt image itself.
        assertPreviewEnlarges(page, "parking.jpg");

        click(page, "Save");

        var parking = service.findMine(id).travels().getFirst()
                .generatedLine(com.vaadin.expensemanager.report.domain.GeneratedLineKind
                        .PARKING).orElseThrow();
        assertThat(parking.hasReceipt()).isTrue();
        assertThat(parking.receiptFilename()).isEqualTo("parking.jpg");
        assertThat(parking.receiptId()).isNotNull();
    }

    @Test
    void editingATripWithFreeLunchRegeneratesAHalvedPerDiem(UnifiedPage page) {
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);

        clickRowAction(page, TRIP, "Edit");                    // the trip row's menu
        dailyAllowance(page).selectItem("Halved, free lunch provided");
        saveDialog(page);

        // Live per-diem halves to €27.00; a save regenerates the line.
        assertThat(viewText(page)).contains("€27.00");
        click(page, "Save");

        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("27.00");
    }

    @Test
    void aTripEarningAPartialDayListsTwoPerDiemCardsUnderOneSubtotal(UnifiedPage page) {
        // Issue #124: 31 h → a full-day card (1 × €54.00) and a partial-day card
        // (1 × €25.00), each labelled by the day it prices, both folded into the single
        // "Per diem allowance" subtotal row (€79.00).
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(31));
        page.navigate(ReportDetailView.class, id);

        var shown = viewText(page);
        assertThat(shown).contains("Per diem allowance (full day)", "€54.00",
                "Per diem allowance (partial day)", "€25.00");
        // One subtotal row for both lines, and it is the sum.
        assertThat(hasSpan(page, "Per diem allowance")).isTrue();
        assertThat(shown).contains("€79.00");

        var loaded = service.findMine(id);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("79.00");
        assertThat(loaded.total()).isEqualByComparingTo("79.00");
        assertThat(loaded.travels().getFirst().generatedLines()).hasSize(2);
    }

    @Test
    void aMultiDayPerDiemCardShowsTheDaysTimesRateBreakdown(UnifiedPage page) {
        // 55 h → 2 full days + 1 partial: the full-day card reads like an invoice line
        // (ADR-0023), the partial-day card stays a plain quantity-1 card.
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);

        var shown = viewText(page);
        assertThat(shown).contains("2 × €54.00 = €108.00")
                .doesNotContain("1 × €25.00");
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void reCostingATripDropsThePerDiemCardItNoLongerEarns(UnifiedPage page) {
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(31));
        page.navigate(ReportDetailView.class, id);
        assertThat(viewText(page))
                .contains("Per diem allowance (partial day)");

        // Shorten the trip to a whole 24 h through its editor: the partial-day card
        // disappears, the full-day one stays, and the subtotal follows live.
        clickRowAction(page, TRIP, "Edit");
        dateTimePicker(page, "Return").setValue(DEP.plusHours(24));
        saveDialog(page);

        var shown = viewText(page);
        assertThat(shown).contains("Per diem allowance (full day)")
                .doesNotContain("Per diem allowance (partial day)");

        click(page, "Save");

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
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        assertThat(viewText(page))
                .contains("€133.00");

        overrideCount(page, "Per diem allowance (full day)", 1, "the Wednesday was personal");

        // The row now reads the effective figures, badges the correction, gives the
        // reason and names what the calculator produced — before any save.
        var shown = viewText(page);
        assertThat(shown).contains("Overridden",
                "Reason: the Wednesday was personal",
                "Calculated: 2 × €54.00 = €108.00");
        // 1 full day + the untouched partial day, live in the subtotal and total.
        assertThat(shown).contains("€79.00");

        click(page, "Save");

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
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (full day)", 1, "the Wednesday was personal");
        click(page, "Save");

        // Reload the persisted report from scratch: the badge, the reason and the
        // statutory baseline are all still there.
        page.navigate(ReportDetailView.class, id);

        var shown = viewText(page);
        assertThat(shown).contains("Overridden",
                "Reason: the Wednesday was personal",
                "Calculated: 2 × €54.00 = €108.00", "€79.00");
    }

    @Test
    void theMealAllowanceCountIsOverridableToo(UnifiedPage page) {
        // A not-eligible trip paying a meal allowance: one flat €13.50 line.
        var id = fixtures.seedReportWithMealTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);

        overrideCount(page, "Meal allowance", 2, "two meals were taken");
        click(page, "Save");

        var loaded = service.findMine(id);
        assertThat(loaded.mealTotal()).isEqualByComparingTo("27.00");
        assertThat(loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.MEAL).orElseThrow()
                .overrideReason()).isEqualTo("two meals were taken");
    }

    @Test
    void aBlankReasonIsRejectedInTheDialogsErrorSummaryAndChangesNothing(UnifiedPage page) {
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);

        clickRowAction(page, "Per diem allowance (full day)", "Override");
        integerField(page, "Count").setValue(1);
        textArea(page, "Reason for the override").setValue("   ");
        // Always-enabled submit (ADR-0020): the click is allowed, the reason shows.
        click(page, "Save override");

        assertThat(dialogText(page))
                .contains("A reason for the override is required.");
        // The dialog stays open and nothing was corrected.
        assertThat(hasButton(page, "Save override")).isTrue();
        assertThat(hasSpan(page, "Overridden")).isFalse();
    }

    @Test
    void thePartialDayCountIsCappedAtOneWithTheReasonInTheErrorSummary(UnifiedPage page) {
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);

        clickRowAction(page, "Per diem allowance (partial day)", "Override");
        integerField(page, "Count").setValue(2);
        textArea(page, "Reason for the override").setValue("two leftovers");
        click(page, "Save override");

        assertThat(dialogText(page))
                .contains("one partial day");
        assertThat(hasButton(page, "Save override")).isTrue();
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void theCountFieldTakesWholeNumbersAtOrAboveTheFloor(UnifiedPage page) {
        // Integrality is enforced at the widget (an IntegerField) and the floor with
        // it; the domain enforces both again on save. The tester refuses to set an
        // out-of-range value, so assert the constraint that makes that so (F-021).
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);

        clickRowAction(page, "Per diem allowance (full day)", "Override");

        assertThat(integerField(page, "Count").getMin()).isEqualTo(0);
    }

    @Test
    void kilometreAndParkingLinesOfferNoOverride(UnifiedPage page) {
        // Their numbers are trip inputs with a single home — edited on the trip.
        var id = fixtures.seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), false,
                new BigDecimal("12.00"));
        page.navigate(ReportDetailView.class, id);

        assertThat(rowActions(page, "Per diem allowance (full day)")).contains("Override");
        assertThat(rowActions(page, "Kilometre allowance")).doesNotContain("Override");
        assertThat(rowActions(page, "Parking")).doesNotContain("Override");
    }

    @Test
    void resetToCalculatedRemovesTheOverrideWithoutTouchingTheTrip(UnifiedPage page) {
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (full day)", 1, "the Wednesday was personal");
        click(page, "Save");

        clickRowAction(page, "Per diem allowance (full day)", "Reset to calculated");

        // Back to the statutory figure, live, and the badge is gone.
        var shown = viewText(page);
        assertThat(shown).contains("€133.00").doesNotContain("Overridden");

        click(page, "Save");

        var loaded = service.findMine(id);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("133.00");
        assertThat(loaded.travels().getFirst().quantityOverrides()).isEmpty();
        // The trip itself was never edited.
        assertThat(loaded.travels().getFirst().returnAt()).isEqualTo(DEP.plusHours(55));
    }

    @Test
    void aSubmittedReportShowsAnOverrideReadOnlyWithNoWayToChangeIt(UnifiedPage page) {
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (full day)", 1, "the Wednesday was personal");
        click(page, "Save");
        service.submit(id, service.findMine(id).version());

        page.navigate(ReportDetailView.class, id);

        // Editability falls out of the existing DRAFT/REJECTED gating — the badge,
        // reason and baseline still read, but every mutation surface is gone.
        var shown = viewText(page);
        assertThat(shown).contains("Overridden", "Reason: the Wednesday was personal",
                "Calculated: 2 × €54.00 = €108.00");
        // A read-only report builds no menu at all, rather than a disabled trigger.
        assertThat(rowActions(page, "Per diem allowance (full day)")).isEmpty();
    }

    @Test
    void aRejectedReportCanStillBeCorrectedWithAnOverride(UnifiedPage page) {
        // The owner acts on an approver's feedback: REJECTED is editable again.
        var id = fixtures.seedRejectedReport(LocalDate.of(2026, 7, 1), "100",
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
        click(page, "Save");

        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("79.00");
    }

    /**
     * Drives the whole override flow for one generated line: opens the row's
     * override dialog, enters a count and a reason, and confirms.
     */
    private void overrideCount(UnifiedPage page, String lineLabel, int count, String reason) {
        clickRowAction(page, lineLabel, "Override");
        integerField(page, "Count").setValue(count);
        textArea(page, "Reason for the override").setValue(reason);
        click(page, "Save override");
    }

    // --- Zero suppresses the line, with the receipt-destruction confirm (issue #132) ---
    //
    // A count of 0 drops the line — the only way to say "keep the full days, lose the
    // partial leftover". Because a dropped line takes its receipt with it (a DB-level
    // cascade, ADR-0024), the user is warned first and the file is named.

    @Test
    void aZeroCountRemovesTheLineWithoutPromptingWhenItCarriesNoReceipt(UnifiedPage page) {
        // 55 h → 2 full days (€108.00) + 1 partial day (€25.00) = €133.00.
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);

        overrideCount(page, "Per diem allowance (partial day)", 0, "the leftover was personal");

        // Nothing to destroy, so nothing to confirm — the correction applied at once.
        assertThat(hasButton(page, "Remove line and receipt")).isFalse();
        var shown = viewText(page);
        // The line is gone from the report; the full days are untouched and the
        // per-diem subtotal and total drop to just them.
        assertThat(shown).contains("€108.00").doesNotContain("€133.00", "1 × €25.00");
        // What is left in its place says what was dropped and why, and offers the way
        // back — without which a zero override would be unreachable once made.
        assertThat(shown).contains("Removed",
                "Removed from the report. Reason: the leftover was personal");
        assertThat(rowActions(page, "Per diem allowance (partial day)"))
                .containsExactly("Reset to calculated");

        click(page, "Save");

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
        var prompt = dialogText(page);
        assertThat(prompt).contains("removes the Meal allowance line", "lunch.jpg",
                "deleted with the line and cannot be recovered");
        assertThat(service.findMine(id).mealTotal()).isEqualByComparingTo("13.50");

        click(page, "Remove line and receipt");
        click(page, "Save");

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
        click(page, "Cancel");

        // The line still stands at its statutory figure, with its receipt attached and
        // no override recorded — cancelling is a full retreat, not a half-applied one.
        var shown = viewText(page);
        assertThat(shown).contains("Meal allowance", "€13.50").doesNotContain("Removed");
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
        var id = fixtures.seedReportWithMealTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);
        clickRowAction(page, "Meal allowance", "Add receipt");
        upload(page).upload("lunch.jpg", "image/jpeg", ReportFixtures.jpegBytes());
        click(page, "Save receipt");

        overrideCount(page, "Meal allowance", 0, "no meal was taken");
        // A buffered receipt is just as destructible as a saved one — same warning.
        assertThat(dialogText(page))
                .contains("lunch.jpg");
        click(page, "Remove line and receipt");

        click(page, "Save");

        // The save went through (a stale ref would have failed it) and left nothing
        // behind: no line, no receipt, no error.
        assertThat(hasSpan(page, "Something went wrong")).isFalse();
        var loaded = service.findMine(id);
        assertThat(loaded.travels().getFirst().generatedLines()).isEmpty();
        assertThat(loaded.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void resetToCalculatedRestoresASuppressedLineAtItsStatutoryCount(UnifiedPage page) {
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (partial day)", 0, "the leftover was personal");
        click(page, "Save");
        // Reload, so the reset acts on a persisted suppression rather than a live one.
        page.navigate(ReportDetailView.class, id);
        assertThat(viewText(page))
                .contains("Removed from the report. Reason: the leftover was personal");

        clickRowAction(page, "Per diem allowance (partial day)", "Reset to calculated");

        var shown = viewText(page);
        assertThat(shown).contains("Per diem allowance (partial day)", "€25.00",
                "€133.00").doesNotContain("Removed");

        click(page, "Save");

        var loaded = service.findMine(id);
        assertThat(loaded.travels().getFirst().quantityOverrides()).isEmpty();
        assertThat(loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL).orElseThrow()
                .quantity()).isEqualByComparingTo("1.00");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void aSuppressedLineIsShownReadOnlyOnASubmittedReport(UnifiedPage page) {
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (partial day)", 0, "the leftover was personal");
        click(page, "Save");
        service.submit(id, service.findMine(id).version());

        page.navigate(ReportDetailView.class, id);

        // The record of what was dropped and why still reads — an approver's question
        // about a missing partial day has an answer on the report — but the way back is
        // gone with every other mutation surface (the existing DRAFT/REJECTED gating).
        assertThat(viewText(page))
                .contains("Removed from the report. Reason: the leftover was personal");
        assertThat(rowActions(page, "Per diem allowance (partial day)")).isEmpty();
    }

    /**
     * A DRAFT report with one meal-allowance trip whose line carries a <em>saved</em>
     * receipt — the state the receipt-destruction warning is about.
     *
     * <p>Every click is its own request with its own persistence session, so the
     * suppression runs against a fresh load, as in production: a request that loads a
     * report never loads its receipts (nothing references them — {@code ExpenseLine}
     * has no back-reference, which is why the cascade lives in the schema). With no
     * test transaction there is no longer a long-lived session to clear.
     */
    private Long seedMealTravelWithSavedReceipt(UnifiedPage page, String filename) {
        var id = fixtures.seedReportWithMealTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);
        clickRowAction(page, "Meal allowance", "Add receipt");
        upload(page).upload(filename, "image/jpeg", ReportFixtures.jpegBytes());
        click(page, "Save receipt");
        click(page, "Save");
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
        click(page, "Clear and save trip");

        // The line is back to the statutory count, live: 3 × €54.00 + the partial day.
        var shown = viewText(page);
        assertThat(shown).contains("3 × €54.00 = €162.00", "€187.00")
                .doesNotContain("Overridden", "the Wednesday was personal");

        click(page, "Save");

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.quantityOverrides()).isEmpty();
        assertThat(trip.returnAt()).isEqualTo(DEP.plusHours(79));
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("187.00");
    }

    @Test
    void cancellingTheClearingConfirmAbandonsTheTripSaveAndKeepsTheOverride(UnifiedPage page) {
        var id = seedOverriddenTravel(page, DEP.plusHours(55));

        editTripReturn(page, DEP.plusHours(79));
        click(page, "Keep editing");

        // A full retreat: the trip editor is still open on the user's edit, and behind
        // it the trip and its override are untouched (1 × €54.00 + €25.00 = €79.00).
        assertThat(hasButton(dialog(page), "Save")).isTrue();
        var shown = viewText(page);
        assertThat(shown).contains("Overridden",
                "Reason: the Wednesday was personal", "€79.00");

        // Abandon the trip edit, then save the report: nothing of the edit survived.
        click(page, "Cancel");
        click(page, "Save");

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.returnAt()).isEqualTo(DEP.plusHours(55));
        assertThat(trip.quantityOverrides()).containsKey(GeneratedLineKind.PER_DIEM_FULL);
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("79.00");
    }

    @Test
    void editingOnlyThePurposeOrDestinationsNeverPromptsAndNeverClears(UnifiedPage page) {
        var id = seedOverriddenTravel(page, DEP.plusHours(55));

        clickRowAction(page, TRIP, "Edit");
        textField(page, "Travel purpose").setValue("Client visit (Acme)");
        textField(page, "Destinations").setValue("Helsinki, Espoo");
        saveDialog(page);

        // Nothing the calculation can see changed, so nothing is asked and nothing lost.
        assertThat(hasButton(page, "Clear and save trip")).isFalse();
        assertThat(viewText(page))
                .contains("Overridden", "Reason: the Wednesday was personal", "€79.00");

        click(page, "Save");

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

        assertThat(hasButton(page, "Clear and save trip")).isFalse();
        assertThat(viewText(page))
                .contains("Overridden", "Reason: the Wednesday was personal", "€79.00");

        click(page, "Save");

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
        click(page, "Save");

        // 55 h → 31 h: the full days drop 2 → 1, but the trip still earns exactly one
        // partial day, so only the full-day override is invalidated.
        editTripReturn(page, DEP.plusHours(31));

        assertThat(clearingConfirm(page).getHeaderTitle()).isEqualTo("Clear your override?");
        assertThat(dialogText(clearingConfirm(page)))
                .contains("Per diem allowance (full day): calculated 2 → 1 day.")
                .doesNotContain("Per diem allowance (partial day)");
        click(page, "Clear and save trip");
        click(page, "Save");

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
        var id = seedMealTravelOverriddenOnBothKinds(page);
        page.navigate(ReportDetailView.class, id);

        // Stop paying the meal allowance: its count drops 1 → 0. The trip stays not
        // eligible, so the per-diem count is 0 either side and its override stands.
        clickRowAction(page, TRIP, "Edit");
        checkbox(page, "Pay meal allowance?").uncheck();
        saveDialog(page);

        assertThat(dialogText(clearingConfirm(page)))
                .contains("Meal allowance: calculated 1 → 0 meals.")
                .doesNotContain("Per diem allowance");
        click(page, "Clear and save trip");
        click(page, "Save");

        assertThat(service.findMine(id).travels().getFirst().quantityOverrides())
                .containsOnlyKeys(GeneratedLineKind.PER_DIEM_FULL);
    }

    @Test
    void aTripEditThatMovesTwoOverriddenCountsReportsBothInOneConfirm(UnifiedPage page) {
        var id = seedOverriddenTravel(page, DEP.plusHours(55));
        overrideCount(page, "Per diem allowance (partial day)", 1, "the leftover was worked");
        click(page, "Save");

        // 55 h → 72 h: three whole days and no leftover at all, so both corrected
        // counts move (full 2 → 3, partial 1 → 0) — one dialog, both named.
        editTripReturn(page, DEP.plusHours(72));

        // One dialog, both corrections named — not two prompts in a row.
        assertThat(clearingConfirm(page).getHeaderTitle()).isEqualTo("Clear your overrides?");
        assertThat(dialogText(clearingConfirm(page))).contains(
                "Per diem allowance (full day): calculated 2 → 3 days.",
                "Per diem allowance (partial day): calculated 1 → 0 days.",
                "\"the Wednesday was personal\"", "\"the leftover was worked\"");
        click(page, "Clear and save trip");
        click(page, "Save");

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
        integerField(page, "Count").setValue(2);
        textArea(page, "Reason for the override")
                .setValue("both days were personal after all");
        click(page, "Save override");

        assertThat(hasButton(page, "Clear and save trip")).isFalse();
        assertThat(viewText(page))
                .contains("Calculated: 2 × €54.00 = €108.00",
                        "Reason: both days were personal after all");

        click(page, "Save");

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
        assertThat(viewText(page)).contains("€79.00");
    }

    @Test
    void aSuppressedKindIsAnnotatedInThePreviewThatNoLongerListsIt(UnifiedPage page) {
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (partial day)", 0, "the leftover was personal");
        click(page, "Save");

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
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, returnAt);
        page.navigate(ReportDetailView.class, id);
        overrideCount(page, "Per diem allowance (full day)", 1, "the Wednesday was personal");
        click(page, "Save");
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
        return dialogText(openDialogSaying(page, "Trip total"));
    }

    /**
     * The open dialog whose content includes {@code marker}.
     *
     * <p>Innermost match: since Vaadin 25.3 a dialog opened while a modal dialog is
     * open attaches <em>inside</em> it (F-082), on the server and so in the DOM, so the
     * outer editor's text also contains the confirm's marker. The innermost match is
     * the one with the least text — its text is part of every dialog enclosing it —
     * which avoids walking a tree each tool exposes differently.
     */
    private static UnifiedDialog openDialogSaying(UnifiedPage page, String marker) {
        return openDialogs(page).stream()
                .filter(dialog -> dialogText(dialog).contains(marker))
                .min(Comparator.comparingInt(dialog -> dialogText(dialog).length()))
                .orElseThrow(() -> new AssertionError(
                        "No open dialog says \"" + marker + "\""));
    }

    /** Re-costs the open report's only trip by moving its return time. */
    private void editTripReturn(UnifiedPage page, LocalDateTime returnAt) {
        clickRowAction(page, TRIP, "Edit");
        dateTimePicker(page, "Return").setValue(returnAt);
        saveDialog(page);
    }

    /**
     * Seeds a DRAFT report with one meal-allowance trip carrying an override on
     * <em>both</em> the meal and the full-day per-diem kind. The per-diem one is
     * dormant — the trip is not eligible, and an override never conjures a line — which
     * is precisely what makes it the control in a per-kind clearing test. Seeded
     * through the service because a dormant override has no row to be made on.
     */
    private Long seedMealTravelOverriddenOnBothKinds(UnifiedPage page) {
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
        clickNamed(page, "Add travel");

        screenshot(page, "travel-editor");
        var group = dailyAllowance(page);
        assertThat(group.getLabel()).isEqualTo("Daily allowance");
        assertThat(group.getSelectedItem())
                .isEqualTo(TravelFormModel.DailyAllowance.FULL.label());
        assertThat(group.getItems())
                .containsExactly("Full daily allowance", "Halved, free lunch provided",
                        "Not eligible");
        // Vertical is Aura's default; the horizontal variant the frame draws is not set.
        assertThat(group.element().getAttribute("theme")).isNullOrEmpty();
        // The two checkboxes it replaces are gone; the two that stay are these.
        assertThat(dialog(page).find(UnifiedCheckbox.class).all())
                .extracting(UnifiedCheckbox::getLabel)
                .containsExactly("Charge expenses from customer?", "Pay meal allowance?");

        fillMinimalTrip(page);
        saveDialog(page);
        click(page, "Save");

        var trip = service.findMine(service.listMine().getFirst().id()).travels().getFirst();
        assertThat(trip.notEligibleForAllowance()).isFalse();
        assertThat(trip.freeLunch()).isFalse();
    }

    @Test
    void choosingHalvedSavesEligibleWithAFreeLunch(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        clickNamed(page, "Add travel");
        fillMinimalTrip(page);

        dailyAllowance(page).selectItem("Halved, free lunch provided");
        // The per-diem previews halved: €54 → €27.
        assertThat(tripPreviewText(page)).contains("€27.00");
        saveDialog(page);
        click(page, "Save");

        var trip = service.findMine(service.listMine().getFirst().id()).travels().getFirst();
        assertThat(trip.notEligibleForAllowance()).isFalse();
        assertThat(trip.freeLunch()).isTrue();
    }

    @Test
    void choosingNotEligibleSavesNotEligibleWithoutAFreeLunch(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        clickNamed(page, "Add travel");
        fillMinimalTrip(page);

        dailyAllowance(page).selectItem("Not eligible");
        // Nothing earned: the no-allowances line stands in for the rows, and there is
        // no Trip total row to end on.
        assertThat(dialogText(openDialogSaying(page, "No allowances for this trip")))
                .doesNotContain("Per diem allowance", "Trip total");
        saveDialog(page);
        click(page, "Save");

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
        assertThat(dailyAllowance(page).getSelectedItem())
                .isEqualTo(TravelFormModel.DailyAllowance.HALVED.label());
        click(dialog(page), "Cancel");

        var mealId = fixtures.seedReportWithMealTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        page.navigate(ReportDetailView.class, mealId);
        clickRowAction(page, TRIP, "Edit");
        assertThat(dailyAllowance(page).getSelectedItem())
                .isEqualTo(TravelFormModel.DailyAllowance.NOT_ELIGIBLE.label());
        assertThat(checkboxValue(page, "Pay meal allowance?")).isTrue();
    }

    @Test
    void checkingPayMealAllowanceSelectsNotEligible(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        clickNamed(page, "Add travel");
        dailyAllowance(page).selectItem("Halved, free lunch provided");

        checkbox(page, "Pay meal allowance?").check();

        // Meal allowance needs "no per-diem": the group moves to Not eligible, which
        // by construction drops the free lunch too.
        assertThat(checkboxValue(page, "Pay meal allowance?")).isTrue();
        assertThat(dailyAllowance(page).getSelectedItem())
                .isEqualTo(TravelFormModel.DailyAllowance.NOT_ELIGIBLE.label());
    }

    @Test
    void selectingFullClearsPayMealAllowance(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        clickNamed(page, "Add travel");
        checkbox(page, "Pay meal allowance?").check();
        assertThat(dailyAllowance(page).getSelectedItem())
                .isEqualTo(TravelFormModel.DailyAllowance.NOT_ELIGIBLE.label());

        dailyAllowance(page).selectItem("Full daily allowance");

        // A per-diem applies again, so the meal allowance can't stand.
        assertThat(checkboxValue(page, "Pay meal allowance?")).isFalse();
        assertThat(dailyAllowance(page).getSelectedItem())
                .isEqualTo(TravelFormModel.DailyAllowance.FULL.label());
    }

    @Test
    void selectingHalvedClearsPayMealAllowance(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        clickNamed(page, "Add travel");
        checkbox(page, "Pay meal allowance?").check();

        dailyAllowance(page).selectItem("Halved, free lunch provided");

        assertThat(checkboxValue(page, "Pay meal allowance?")).isFalse();
        assertThat(dailyAllowance(page).getSelectedItem())
                .isEqualTo(TravelFormModel.DailyAllowance.HALVED.label());
    }

    @Test
    void travelEditorLaysItsFieldsOutAsTheDesignsTwoSectionGrid(UnifiedPage page) {
        // travel-editor-dialog.md § Layout — #179 delta items 1–4, 7, 13: one
        // two-column grid with the line editor's gaps, two eyebrows and two rules as
        // colspan-2 rows, the design's field order, and the totals as the last row.
        page.navigate(ReportDetailView.class);
        clickNamed(page, "Add travel");

        screenshot(page, "travel-editor-empty");

        var dialog = dialog(page);
        assertThat(dialog.element().getProperty("width")).isEqualTo("35rem");
        assertThat(dialog.getHeaderTitle()).isEqualTo("Add travel info");

        var form = dialog.find(UnifiedFormLayout.class).single();
        assertThat(form.getResponsiveSteps()).containsExactly("0:1", "24rem:2");
        assertThat(form.getColumnSpacing()).isEqualTo("var(--em-section-gap)");
        assertThat(form.getRowSpacing()).isEqualTo("var(--em-card-padding)");

        // Until the dates are complete the totals and the rule above them hide.
        var children = form.getChildren();
        assertThat(children).hasSize(15);
        assertThat(children.get(13).isVisible()).isFalse();
        assertThat(children.get(14).isVisible()).isFalse();
        fillMinimalTrip(page);
        screenshot(page, "travel-editor-filled");
        children = form.getChildren();
        assertThat(children.get(13).isVisible()).isTrue();
        assertThat(children.get(14).isVisible()).isTrue();

        // The design's order, read off the grid's children. Eyebrows are Spans with
        // the .section-label role, rules are Hr.form-rule, the totals a Div.trip-totals.
        // Read once everything shows: Flow sends a hidden component's classes to the
        // browser only when it becomes visible, so a browser sees bare placeholders.
        assertThat(children).extracting(ReportDetailViewUiTest::gridCell).containsExactly(
                "eyebrow:Destinations", "DateTimePicker:Departure", "DateTimePicker:Return",
                "ComboBox:Destination country", "TextField:Destinations",
                "TextField:Travel purpose", "rule", "eyebrow:Expenses",
                "Checkbox:Charge expenses from customer?",
                "BigDecimalField:Kilometre allowance (km)",
                "BigDecimalField:Parking fees (€)", "RadioButtonGroup:Daily allowance",
                "Checkbox:Pay meal allowance?", "rule", "totals");
        assertThat(children).extracting(UnifiedFormLayout::colspanOf)
                .containsExactly(2, 1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 2, 2, 2, 2);
        // The rules carry no margin of their own — the grid's row gap spaces them.
        assertThat(UnifiedElements.style(children.get(6)).get("margin")).isNull();

        // Label and figure are separate spans in the totals card's classes; Trip
        // total is the LAST row, and the block paints no surface of its own.
        var totals = children.get(14);
        var rows = UnifiedElements.children(totals);
        // Whitespace dropped: a browser's text puts a line break between the spans.
        assertThat(rows.getLast().getText().replaceAll("\\s+", ""))
                .isEqualTo("Triptotal€54.00");
        assertThat(dialog.find(UnifiedSpan.class).withClassName("totals-total-row").all())
                .extracting(ReportDetailViewUiTest::rawText).containsExactly("Trip total");
        assertThat(dialog.find(UnifiedSpan.class).withClassName("totals-grand").all())
                .extracting(ReportDetailViewUiTest::rawText).contains("€54.00");
        assertThat(UnifiedElements.classNames(totals)).contains("trip-totals")
                .doesNotContain("travel-preview");

        // Footer: Cancel and a primary "Save" — no "Save trip", no close button.
        assertThat(hasButton(dialog(page), "Save")).isTrue();
        assertThat(hasButton(page, "Save trip")).isFalse();
        assertThat(dialog.element().getPropertyBoolean("noCloseOnEsc")).isFalse();
    }

    @Test
    void travelEditorTitlesTheEditStateEditTravelInfo(UnifiedPage page) {
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);
        clickRowAction(page, TRIP, "Edit");
        assertThat(dialog(page).getHeaderTitle())
                .isEqualTo("Edit travel info");
    }

    /** "kind:label" for a field, or the grid row's role for a non-field child. */
    private static String gridCell(UnifiedElement child) {
        String tag = child.getTag();
        if ("span".equals(tag) && UnifiedElements.hasClassName(child, "section-label")) {
            return "eyebrow:" + rawText(child);
        }
        if ("hr".equals(tag) && UnifiedElements.hasClassName(child, "form-rule")) {
            return "rule";
        }
        if ("div".equals(tag) && UnifiedElements.hasClassName(child, "trip-totals")) {
            return "totals";
        }
        String kind = switch (tag) {
            case "vaadin-date-time-picker" -> "DateTimePicker";
            case "vaadin-combo-box" -> "ComboBox";
            case "vaadin-text-field" -> "TextField";
            case "vaadin-checkbox" -> "Checkbox";
            case "vaadin-big-decimal-field" -> "BigDecimalField";
            case "vaadin-radio-group" -> "RadioButtonGroup";
            default -> tag;
        };
        return kind + ":" + child.getProperty("label");
    }

    /** The open trip editor's {@code Daily allowance} radio group. */
    private static UnifiedRadioButtonGroup dailyAllowance(UnifiedPage page) {
        return page.find(UnifiedRadioButtonGroup.class).withLabel("Daily allowance").single();
    }

    /** Dates, country, destinations and purpose for an 11-hour domestic trip. */
    private void fillMinimalTrip(UnifiedPage page) {
        dateTimePicker(page, "Departure").setValue(DEP);
        dateTimePicker(page, "Return").setValue(DEP.plusHours(11));
        comboBox(page, "Destination country")
                .selectItem("Finland (domestic)");
        textField(page, "Destinations").setValue("Helsinki");
        textField(page, "Travel purpose").setValue("Client visit");
    }

    /** The current value of the dialog checkbox carrying the given label. */
    private boolean checkboxValue(UnifiedPage page, String label) {
        return checkbox(page, label).isChecked();
    }

    @Test
    void removingATripRemovesItsPerDiemOnSave(UnifiedPage page) {
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);

        assertThat(viewText(page))
                .contains("Per diem allowance");

        clickRowAction(page, TRIP, "Remove");
        click(page, "Save");

        assertThat(service.findMine(id).travels()).isEmpty();
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void choosingADepartureConstrainsTheReturnPickerRangeAndViceVersa(UnifiedPage page) {
        page.navigate(ReportDetailView.class);

        clickNamed(page, "Add travel");
        dateTimePicker(page, "Departure").setValue(DEP);
        dateTimePicker(page, "Return").setValue(DEP.plusHours(11));

        // The overlay can no longer offer an invalid range: the return can't reach
        // back to the departure, nor the departure forward to the return. The bounds
        // sit one 15-minute step clear of each other, not on the other's instant —
        // setMin/setMax are inclusive while the trip rule is strict, so an unshifted
        // bound still offers the same instant on both sides (issue #140).
        var ret = dateTimePicker(page, "Return");
        var dep = dateTimePicker(page, "Departure");
        assertThat(ret.getMin()).isEqualTo(DEP.plusMinutes(15));
        assertThat(dep.getMax()).isEqualTo(DEP.plusHours(11).minusMinutes(15));
    }

    @Test
    void aReturnEqualToTheDepartureIsRefusedAndSaysWhy(UnifiedPage page) {
        // The reported case: same date *and* same time on both sides. The overlay
        // no longer offers it now that the return's min sits a step past the
        // departure, so type it past the constraint — by hand in a browser, straight
        // on the component on Browserless, whose tester refuses it — and prove the
        // value still never reaches a save silently: the
        // binder's constraint validation lands the rule in the dialog's error
        // summary, not in the generic "something went wrong" dialog (issue #140).
        page.navigate(ReportDetailView.class);

        clickNamed(page, "Add travel");
        dateTimePicker(page, "Departure").setValue(DEP);
        comboBox(page, "Destination country")
                .selectItem("Finland (domestic)");
        textField(page, "Destinations").setValue("Helsinki");
        textField(page, "Travel purpose").setValue("Client visit");

        dateTimePicker(page, "Return").typeOutOfRange(DEP);

        saveDialog(page);
        screenshot(page, "range-rule-summary");

        var text = dialogText(page);
        assertThat(text).contains("Return must be after the departure");
        assertThat(text).doesNotContain("Something went wrong");
        // The dialog stays open and the trip is not committed.
        assertThat(hasButton(dialog(page), "Save")).isTrue();
        assertThat(hasSpan(page, "Per diem allowance")).isFalse();
    }

    @Test
    void anIncompleteTripShowsTheErrorSummaryAndGeneratesNothing(UnifiedPage page) {
        // The reciprocal min/max on the pickers keeps the *overlay* from producing an
        // invalid range (see the constraint test above), but a hand-typed one still
        // reaches Save — covered by the range-rule tests above. Here we prove the
        // always-enabled Save + error-summary rule (ADR-0020) on the other invalid
        // case: missing required fields.
        page.navigate(ReportDetailView.class);

        clickNamed(page, "Add travel");
        saveDialog(page);

        // The dialog overlay carries the reasons and stays open; nothing generated.
        assertThat(dialogText(page)).contains(
                "Departure date & time is required", "Destinations are required");
        assertThat(hasButton(dialog(page), "Save")).isTrue();
        assertThat(hasSpan(page, "Per diem allowance")).isFalse();
    }

    /**
     * Issue #85 for the trip's date-time pickers: their i18n carries an incomplete-input,
     * a bad-input and both range messages, so none reaches the summary as a blank bullet.
     *
     * <p><strong>Browserless only</strong> ({@code @RequiresTestingTool}, ADR-0027 §4).
     * The messages are server-side configuration: a browser's {@code i18n} property
     * carries only the formatting keys ({@code firstDayOfWeek}, {@code formatDate},
     * {@code parseDate}), so there is nothing to read there. The portable form would type
     * a date without a time, or garbage, into one half of the picker — which Browserless
     * can't express: {@code DateTimePickerTester} takes only a whole
     * {@code LocalDateTime}, and the half-pickers are internal components. The min rule
     * is covered portably by {@link #aReturnEqualToTheDepartureIsRefusedAndSaysWhy}.
     */
    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void travelPickersCarryIncompleteAndBadInputErrorMessages(UnifiedPage page) {
        page.navigate(ReportDetailView.class);
        clickNamed(page, "Add travel");

        for (String label : new String[] { "Departure", "Return" }) {
            // The escape hatch: the server-side component, the only place the i18n is.
            var picker = dateTimePicker(page, label).unwrap(DateTimePicker.class);
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
    void aSubmittedReportShowsTheTripButHidesEveryTripAction(UnifiedPage page) {
        var id = fixtures.seedSubmittedReportWithTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        page.navigate(ReportDetailView.class, id);

        // The trip info and its per-diem are still visible (read path)…
        var text = viewText(page);
        assertThat(text).contains("Client visit", "Per diem allowance", "€54.00");
        // …a trip-only read-only report must not prompt "add your first": the empty
        // state is hidden, so no visible span carries it (ADR-0020)…
        assertThat(hasSpan(page, "No expenses yet — add your first."))
                .isFalse();
        // …and every mutation surface is gone on a read-only report.
        assertThat(hasNamed(page, "Add travel")).isFalse();
        assertThat(rowActions(page, TRIP)).isEmpty();
        assertThat(rowMenus(page)).isEmpty();
    }

    @Test
    void removingALinePersistsOnSave(UnifiedPage page) {
        var id = fixtures.seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        assertThat(viewText(page)).contains("€60.00");

        rowMenus(page).getFirst().clickSubMenuItem("Remove");
        click(page, "Save");

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
        var id = fixtures.seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(31));
        page.navigate(ReportDetailView.class, id);
        clickNamed(page, "Add expense");
        addLineThroughEditor(page, "40.00");
        screenshot(page, "two-section-cards");

        // Two cards for the whole view, not one per line: the travel variant and the
        // plain Expenses one.
        // Re-queried until the page settles: right after the editor closes, a browser
        // is still tearing down its overlay, and a page-wide div scan can find an
        // element gone by the time its class is read.
        var cards = settled(() -> page.find(UnifiedDiv.class)
                .withClassName("expense-item-card").all());
        assertThat(cards).hasSize(2);
        assertThat(UnifiedElements.classNames(cards.getFirst().element()))
                .contains("expense-item-card--travel");
        assertThat(UnifiedElements.classNames(cards.get(1).element()))
                .doesNotContain("expense-item-card--travel");

        // The travel card holds the trip row and both per-diem rows it generated —
        // nested INSIDE it, indented, rather than in a wrapper beside it.
        var travelRows = rowsIn(cards.getFirst());
        assertThat(travelRows).hasSize(3);
        assertThat(UnifiedElements.classNames(travelRows.get(1))).contains("travel-line-row");
        assertThat(rowsIn(cards.get(1))).hasSize(1);

        // And nothing is a .line-card any more.
        assertThat(page.find(UnifiedHorizontalLayout.class).withClassName("line-card")
                .exists()).isFalse();
    }

    @Test
    void aSectionWithNoRowsRendersNoCard(UnifiedPage page) {
        var id = fixtures.seedReport(LocalDate.of(2026, 7, 1), "empty");
        page.navigate(ReportDetailView.class, id);
        screenshot(page, "empty-draft");

        // A fresh draft has no trip and no line, so both cards are absent while both
        // headings and their Add actions stand.
        assertThat(page.find(UnifiedDiv.class).withClassName("expense-item-card")
                .exists()).isFalse();
        assertThat(hasSpan(page, "Travel info")).isTrue();
        assertThat(hasSpan(page, "Expenses")).isTrue();
        assertThat(hasNamed(page, "Add travel")).isTrue();
        assertThat(hasNamed(page, "Add expense")).isTrue();
    }

    @Test
    void aReadOnlyReportWithNothingInASectionRendersNoSectionAtAll(UnifiedPage page) {
        // No trip, and read-only: a "TRAVEL INFO" heading over nothing, beside an Add
        // the report cannot offer, is worse than no section (ADR-0020).
        var id = fixtures.seedSubmittedReport(LocalDate.of(2026, 7, 1), "80.00");
        page.navigate(ReportDetailView.class, id);

        assertThat(hasSpan(page, "Travel info")).isFalse();
        assertThat(hasSpan(page, "Expenses")).isTrue();
    }

    @Test
    void anExpenseRowDrawsItsTypesOwnGlyphAndNoColourSwatch(UnifiedPage page) {
        var id = fixtures.seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        // The glyph is the type's persisted icon (ADR-0026), not a colour hashed off
        // its name. seedReportWithLine files the line under the first active type,
        // Travel allowance, whose seeded glyph is `plane` (V15).
        var glyph = page.find(UnifiedSpan.class).withClassName("expense-row-icon").single();
        assertThat(glyph.findComponent(SvgIcon.class).single().element().getProperty("symbol"))
                .isEqualTo("plane");
        // The hashed swatch is gone, along with the four-way --aura-red collision.
        assertThat(page.find(UnifiedDiv.class).withClassName("category-dot").exists())
                .isFalse();
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
        var rate = fixtures.firstActiveRate();
        var type = referenceData.createExpenseType("Glyphless", rate.id(), null);
        var line = ExpenseLineDto.of(null, type.id(), type.name(), type.icon(),
                rate.id(), rate.value(), new BigDecimal("12.00"), null);
        var zero = BigDecimal.ZERO.setScale(2);
        var id = service.create(new ReportDetailDto(null, LocalDate.of(2026, 7, 1),
                "seed", ReportStatus.DRAFT, 0L, List.of(line), zero, zero, zero));

        page.navigate(ReportDetailView.class, id);

        assertThat(page.find(UnifiedSpan.class).withClassName("expense-row-icon").single()
                .findComponent(SvgIcon.class).exists()).isFalse();
        assertThat(hasSpan(page, "Glyphless")).isTrue();
    }

    @Test
    void aRowIsNotClickableAndTheMenuIsTheOnlyRouteToEditing(UnifiedPage page) {
        var id = fixtures.seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);

        // Withdrawn with the card the row used to be: no .clickable anywhere, and the
        // row's own actions are exactly Edit and Remove behind the ⋮.
        assertThat(page.find(UnifiedHorizontalLayout.class).withClassName("clickable")
                .exists()).isFalse();
        assertThat(rowActions(page, "Travel allowance")).containsExactly("Edit", "Remove");
    }

    @Test
    void everyRowMenuIsNamedAfterItsRowAndEveryActionCarriesText(UnifiedPage page) {
        var id = fixtures.seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00"));
        page.navigate(ReportDetailView.class, id);
        screenshot(page, "trip-rows");

        // "Actions" alone is useless on a page holding five of them, so each names
        // its row (ADR-0020, row-action-menu.md).
        // A per-diem-eligible trip earns no meal allowance — the two are mutually
        // exclusive under the Finnish rule (issue #93) — so this trip's four rows are
        // the trip itself plus per diem, kilometre and parking.
        assertThat(rowMenus(page))
                .extracting(UnifiedMenuBar::getTriggerName)
                .containsExactlyInAnyOrder("Actions for " + TRIP,
                        "Actions for Per diem allowance (full day)",
                        "Actions for Kilometre allowance",
                        "Actions for Parking");
        // And every item in every menu is text-labelled, never an icon alone.
        assertThat(rowMenus(page)).allSatisfy(menu ->
                assertThat(menu.getSubMenuItems())
                        .isNotEmpty()
                        .allSatisfy(item -> assertThat(item).isNotBlank()));
    }

    @Test
    void aRowMenusNameFollowsTheTypeTheEditorChanges(UnifiedPage page) {
        var id = fixtures.seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        page.navigate(ReportDetailView.class, id);
        assertThat(rowMenus(page)).singleElement()
                .extracting(UnifiedMenuBar::getTriggerName)
                .isEqualTo("Actions for Travel allowance");

        // The row keeps its component and re-reads the signal, so the accessible name
        // has to follow or it goes quietly stale.
        openLineCardEditor(page);
        comboBox(page, "Expense type")
                .selectItem("Accommodation");
        saveDialog(page);

        assertThat(rowMenus(page)).singleElement()
                .extracting(UnifiedMenuBar::getTriggerName)
                .isEqualTo("Actions for Accommodation");
    }

    @Test
    void bothAddActionsReadAddAndAreToldApartByTheirAccessibleNames(UnifiedPage page) {
        var id = fixtures.seedReport(LocalDate.of(2026, 7, 1), "empty");
        page.navigate(ReportDetailView.class, id);

        // The design labels both simply "Add", so the page carries two controls with
        // the same visible name and the aria-label is what names the section.
        assertThat(hasNamed(page, "Add travel")).isTrue();
        assertThat(hasNamed(page, "Add expense")).isTrue();
        assertThat(buttons(page, "Add").all()).hasSize(2);
        // And the back link keeps its own name.
        assertThat(hasNamed(page, "Back to reports")).isTrue();
    }

    @Test
    void onlyTheVatBearingGeneratedLineShowsANetVatSplit(UnifiedPage page) {
        // Parking is VAT-bearing at 25.5 %; the per-diem, kilometre and meal lines are
        // statutory tax-free allowances. The design draws the same net/VAT string on
        // every generated row including the per-diem one, which is mock text rather
        // than a specification (#173).
        var id = fixtures.seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00"));
        page.navigate(ReportDetailView.class, id);

        assertThat(splitOn(page, "Parking")).startsWith("net ").contains("(25.5 %)");
        assertThat(splitOn(page, "Per diem allowance (full day)")).isNull();
        assertThat(splitOn(page, "Kilometre allowance")).isNull();

        // …and the meal allowance, which this trip does not earn, on one that does.
        page.navigate(ReportDetailView.class, fixtures.seedReportWithMealTravel(
                LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11)));
        assertThat(splitOn(page, "Meal allowance")).isNull();
    }

    @Test
    void aReadOnlyReportRendersNoRowMenusAndNoAddActionsButKeepsItsReceipts(UnifiedPage page) {
        var id = fixtures.seedReportWithReceipt(LocalDate.of(2026, 7, 1), "70.00", "hotel.jpg");
        service.submit(id, service.findMine(id).version());
        page.navigate(ReportDetailView.class, id);

        // No menu at all — not a disabled trigger — and neither Add action.
        assertThat(rowMenus(page)).isEmpty();
        assertThat(hasNamed(page, "Add expense")).isFalse();
        assertThat(hasNamed(page, "Add travel")).isFalse();
        // The receipt stays activatable, so a submitted report's receipts remain
        // viewable (ADR-0021).
        assertThat(hasNamed(page, "Preview receipt: hotel.jpg")).isTrue();
    }

    @Test
    void anAttachmentRendersAsAPaperclipChipThatStillOpensThePreview(UnifiedPage page) {
        var id = fixtures.seedReportWithReceipt(LocalDate.of(2026, 7, 1), "70.00", "hotel.jpg");
        page.navigate(ReportDetailView.class, id);

        // The resting state is the design's chip — a paperclip and the filename — and
        // only the resting state changed: activating it opens the enlarge dialog.
        var chip = page.find(UnifiedButton.class).withClassName("expense-row-attachment")
                .single();
        assertThat(chip.getText()).isEqualTo("hotel.jpg");
        // The chip's icon, read from the attachments box that holds only the chip.
        var box = page.find(UnifiedDiv.class).withClassName("expense-row-attachments").single();
        assertThat(box.find(UnifiedButton.class).all()).hasSize(1);
        assertThat(box.findComponent(SvgIcon.class).all())
                .extracting(icon -> icon.element().getProperty("symbol"))
                .containsExactly("paperclip");

        chip.click();
        screenshot(page, "receipt-enlarged");
        assertThat(hasOpenDialog(page)).isTrue();
        assertThat(dialog(page).findComponent(Image.class).withAttribute("alt", "hotel.jpg")
                .exists()).isTrue();
    }

    @Test
    void theStatusHistoryIsOneBoxWithItsSharedSectionHeading(UnifiedPage page) {
        var id = fixtures.seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Missing receipt");
        page.navigate(ReportDetailView.class, id);
        screenshot(page, "status-history");

        // One box holding every entry, not one bordered box per entry, under the same
        // uppercase .section-label role the other two section headings use.
        var box = page.find(UnifiedDiv.class).withClassName("status-history-box").single();
        assertThat(UnifiedElements.children(box.element())).hasSize(2); // Submitted, Rejected
        assertThat(page.find(UnifiedSpan.class)
                .where(span -> "Status history".equals(rawText(span))).single().element())
                .satisfies(heading -> assertThat(UnifiedElements.classNames(heading))
                        .contains("section-label"));
        assertThat(page.findComponent(VerticalLayout.class)
                .withClassName("status-history-entry").all()).hasSize(2);
    }

    // --------------------------------------------------------------- helpers

    /** Runs {@code lookup}, once more if the page changed under it. */
    private static <T> T settled(java.util.function.Supplier<T> lookup) {
        try {
            return lookup.get();
        } catch (RuntimeException firstTry) {
            return lookup.get();
        }
    }

    /** Adds one expense line through the (already open) editor at the given amount. */
    private void addLineThroughEditor(UnifiedPage page, String amount) {
        comboBox(page, "Expense type")
                .selectItem(fixtures.firstActiveType().name());
        comboBox(page, "VAT rate").selectItem("25.5 %");
        bigDecimalField(page, "Unit price")
                .setValue(new BigDecimal(amount));
        saveDialog(page);
        // Until the dialog has closed, a page-wide scan can race its teardown.
        page.waitUntil(() -> !hasOpenDialog(page));
    }

    /** The .expense-row children of one section card, in order. */
    private static List<UnifiedElement> rowsIn(UnifiedDiv card) {
        return UnifiedElements.children(card.element()).stream()
                .filter(child -> UnifiedElements.hasClassName(child, "expense-row"))
                .toList();
    }

    /**
     * The {@code net … · VAT …} sub-line on the named row, or {@code null} if it has
     * none. Read off the row rather than the whole view, since a manual line beside
     * it would otherwise satisfy the assertion.
     */
    private static String splitOn(UnifiedPage page, String rowLabel) {
        var rows = page.find(UnifiedHorizontalLayout.class).withClassName("expense-row")
                .where(row -> rawText(row).startsWith(rowLabel)).all();
        assertThat(rows).as("rows starting with '%s'", rowLabel).hasSize(1);
        return rows.getFirst().find(UnifiedSpan.class).all().stream()
                .map(span -> rawText(span.element()))
                .filter(text -> text.startsWith("net "))
                .findFirst().orElse(null);
    }

    /**
     * Opens the line editor for the report's first expense row. The row is no longer
     * clickable — the ⋮ menu is the only route to editing (#172) — so this opens the
     * first row's menu and chooses Edit.
     */
    private static void openLineCardEditor(UnifiedPage page) {
        rowMenus(page).getFirst().clickSubMenuItem("Edit");
    }

    /**
     * Clicks the open dialog's primary button. It reads {@code Save} — the line
     * editor (frame 358:3267) and the trip editor alike — which is also the report's
     * own save button, so it has to be located <em>inside</em> the dialog.
     */
    private static void saveDialog(UnifiedPage page) {
        click(dialog(page), "Save");
    }

    /** Every row menu (⋮) on the page; the class keeps the shell's menu bars out. */
    private static List<UnifiedMenuBar> rowMenus(UnifiedPage page) {
        return page.find(UnifiedMenuBar.class).withClassName("row-action-menu").all();
    }

    /** The row menus whose trigger names {@code rowLabel} — none on a read-only row. */
    private static List<UnifiedMenuBar> rowMenusFor(UnifiedPage page, String rowLabel) {
        return rowMenus(page).stream()
                .filter(menu -> ("Actions for " + rowLabel).equals(menu.getTriggerName()))
                .toList();
    }

    /** Opens the named row's ⋮ and activates the named action. */
    private static void clickRowAction(UnifiedPage page, String rowLabel, String action) {
        var menus = rowMenusFor(page, rowLabel);
        assertThat(menus).as("row menu for '%s'", rowLabel).hasSize(1);
        assertThat(menus.getFirst().getSubMenuItems())
                .as("menu item '%s' is present", action).contains(action);
        menus.getFirst().clickSubMenuItem(action);
    }

    /** The text labels a row's ⋮ offers, in order — empty if it has no menu at all. */
    private static List<String> rowActions(UnifiedPage page, String rowLabel) {
        var menus = rowMenusFor(page, rowLabel);
        return menus.isEmpty() ? List.of() : menus.getFirst().getSubMenuItems();
    }

    /** The view's own text — never a dialog's: dialogs are overlays outside it. */
    private static String viewText(UnifiedPage page) {
        return page.findComponent(ReportDetailView.class).withClassName("report-detail")
                .single().element().getText();
    }

    /**
     * An element's DOM text: {@code textContent} in a browser, the server-side string
     * on Browserless (which has no such property and falls back to {@code getText}).
     * Not {@code innerText}, which applies CSS {@code text-transform} (F-086).
     */
    private static String rawText(UnifiedElement element) {
        String content = element.getProperty("textContent");
        return (content == null ? element.getText() : content).strip();
    }

    private static String rawText(UnifiedWrapper wrapper) {
        return rawText(wrapper.element());
    }

    /** Whether a visible span reads exactly {@code text}. */
    private static boolean hasSpan(UnifiedPage page, String text) {
        return page.find(UnifiedSpan.class)
                .where(span -> span.isVisible() && text.equals(rawText(span))).exists();
    }

    /** Visible buttons reading {@code text} in {@code scope}. */
    private static UnifiedButton.ButtonQuery buttons(Scope scope, String text) {
        return scope.find(UnifiedButton.class).withText(text).where(UnifiedElement::isVisible);
    }

    private static void click(Scope scope, String text) {
        buttons(scope, text).single().click();
    }

    private static boolean hasButton(Scope scope, String text) {
        return buttons(scope, text).exists();
    }

    /** Visible buttons whose accessible name is {@code name} (an aria-label). */
    private static UnifiedButton.ButtonQuery named(Scope scope, String name) {
        return scope.find(UnifiedButton.class).withAccessibleName(name)
                .where(UnifiedElement::isVisible);
    }

    private static void clickNamed(Scope scope, String name) {
        named(scope, name).single().click();
    }

    private static boolean hasNamed(Scope scope, String name) {
        return named(scope, name).exists();
    }

    /** The open dialogs, outermost first; a closing one drops out. */
    private static List<UnifiedDialog> openDialogs(UnifiedPage page) {
        return page.find(UnifiedDialog.class)
                .where(dialog -> Boolean.TRUE.equals(dialog.getPropertyBoolean("opened")))
                .all();
    }

    private static boolean hasOpenDialog(UnifiedPage page) {
        return !openDialogs(page).isEmpty();
    }

    /** The outermost open dialog — the editor, when a confirm is nested in it. */
    private static UnifiedDialog dialog(UnifiedPage page) {
        page.waitUntil(() -> hasOpenDialog(page));
        return openDialogs(page).getFirst();
    }

    /** What the open dialogs say — the text a user sees over the view. */
    private static String dialogText(UnifiedPage page) {
        return openDialogs(page).stream().map(ReportDetailViewUiTest::dialogText)
                .collect(Collectors.joining("\n"));
    }

    private static String dialogText(UnifiedDialog dialog) {
        return dialog.element().getText();
    }

    private static int colspan(UnifiedWrapper field) {
        return UnifiedFormLayout.colspanOf(field.element());
    }

    private static UnifiedComboBox comboBox(UnifiedPage page, String label) {
        return page.find(UnifiedComboBox.class).withLabel(label).single();
    }

    private static UnifiedBigDecimalField bigDecimalField(UnifiedPage page, String label) {
        return page.find(UnifiedBigDecimalField.class).withLabel(label).single();
    }

    private static UnifiedIntegerField integerField(UnifiedPage page, String label) {
        return page.find(UnifiedIntegerField.class).withLabel(label).single();
    }

    private static UnifiedTextArea textArea(UnifiedPage page, String label) {
        return page.find(UnifiedTextArea.class).withLabel(label).single();
    }

    private static UnifiedTextField textField(UnifiedPage page, String label) {
        return page.find(UnifiedTextField.class).withLabel(label).single();
    }

    private static UnifiedDateTimePicker dateTimePicker(UnifiedPage page, String label) {
        return page.find(UnifiedDateTimePicker.class).withLabel(label).single();
    }

    private static UnifiedCheckbox checkbox(UnifiedPage page, String label) {
        return page.find(UnifiedCheckbox.class).withLabel(label).single();
    }

    /**
     * Activates the receipt's preview affordance and checks the enlarge dialog shows
     * that receipt's image (its filename is the alt text), then closes it. Scoped to
     * the dialog and the filename: a page-wide "an image exists" is satisfied by the
     * app header's logo.
     */
    private void assertPreviewEnlarges(UnifiedPage page, String filename) {
        clickNamed(page, "Preview receipt: " + filename);
        screenshot(page, "preview-" + filename);
        assertThat(openDialogSaying(page, "Close").findComponent(Image.class)
                .withAttribute("alt", filename).exists()).isTrue();
        click(openDialogSaying(page, "Close"), "Close");
    }

    private static UnifiedUpload upload(UnifiedPage page) {
        return page.find(UnifiedUpload.class).single();
    }
}
