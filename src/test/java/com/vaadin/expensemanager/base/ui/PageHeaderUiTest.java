package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.allowance.ui.AllowanceRatesView;
import com.vaadin.expensemanager.base.AbstractUiTest;
import com.vaadin.expensemanager.base.ui.unified.ElementSnapshot;
import com.vaadin.expensemanager.base.ui.unified.UnifiedVerticalLayout;
import com.vaadin.expensemanager.reference.ui.ExpenseTypeView;
import com.vaadin.expensemanager.reference.ui.VatRateView;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedContainer;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0027 proposed) for the shared
 * {@link PageHeader} block across the three reference routes it opens —
 * {@code /vat-rates}, {@code /expense-types} and {@code /allowance-rates} (#184,
 * {@code docs/design/components/page-header.md}). Runs unchanged on Browserless and
 * Playwright.
 *
 * <p>The three composed the block themselves and had already drifted apart on
 * the gap below the title, so what this asserts is <strong>sameness</strong>:
 * one {@link PageHeader} per route, the same anatomy inside it, the same two
 * gaps around and within it.
 *
 * <p>The anatomy is read as elements rather than as Java components: the block is
 * the {@code vaadin-vertical-layout.page-header}, the view is the one element in the
 * shell's {@code <main>}, and their order and inline {@code gap} come from
 * {@link ElementSnapshot} — the Unified API has no portable "direct children in
 * order". A {@code setSpacing(String)} is an inline {@code gap}, so comparing that
 * style is the same check as comparing {@code getSpacing()}.
 *
 * <p><strong>What is deliberately not here.</strong> That the heading renders at
 * 40px, that the intro's 1.6 leading is unitless rather than Aura's inherited
 * 20px length, that the heading's colour survives dark mode, and that the title
 * row wraps at 360px instead of pushing a scrollbar — none of those are
 * decidable on every Testing Tool. They are the visual verification's, and this
 * class stops at the class names and the tokens the two gaps name.
 */
@WithUserDetails("admin@vaadin.com")
class PageHeaderUiTest extends AbstractUiTest {

    /**
     * The create button's label, from the frame. The {@code h2} beside it carries
     * the noun, so the button does not repeat it.
     */
    private static final String CREATE_LABEL = "New";

    @Test
    void vatRatesOpensWithTheSharedHeaderAndACreateButton(UnifiedPage page) {
        page.navigate(VatRateView.class);
        screenshot(page, "vat-rates");

        assertHeader(page, "VAT rates", CREATE_LABEL);
        assertSectionRhythm(page);
    }

    @Test
    void expenseTypesOpensWithTheSharedHeaderAndACreateButton(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);
        screenshot(page, "expense-types");

        assertHeader(page, "Expense types", CREATE_LABEL);
        assertSectionRhythm(page);
    }

    /**
     * The allowance view's own actions seed a <em>year</em> rather than create the
     * page's record, so the design leaves its title row without a button and puts
     * them in the toolbar below. The block is otherwise identical.
     */
    @Test
    void allowanceRatesOpensWithTheSameBlockAndNoHeaderAction(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);
        screenshot(page, "allowance-rates");

        assertHeader(page, "Allowance Rates", null);
        assertSectionRhythm(page);
    }

    // ------------------------------------------------------------- assertions

    /**
     * @param title  the page name the {@code h2} must read
     * @param action the create button's label, or {@code null} when the title row
     *               carries no action at all
     */
    private void assertHeader(UnifiedPage page, String title, String action) {
        // One block per page: composed by PageHeader, not by the view.
        var headers = page.find(UnifiedVerticalLayout.class).withClassName("page-header").all();
        assertThat(headers).hasSize(1);
        var header = headers.getFirst();

        var anatomy = ElementSnapshot.of(header, 2);
        // The design's 20px between the title row and the intro, through the
        // layout API rather than CSS.
        assertThat(anatomy.gap()).isEqualTo("var(--em-card-padding)");
        assertThat(anatomy.children()).hasSize(2);

        var top = anatomy.child(0);
        assertThat(top.hasClassName("page-header-top")).isTrue();

        // The class carries the display size, not the element — the shell owns
        // the page's h1, so a view's own heading is the second level.
        var headingElement = top.child(0);
        assertThat(headingElement.tag()).isEqualTo("h2");
        assertThat(headingElement.hasClassName("page-title")).isTrue();
        var heading = header.findComponent(H2.class).withClassName("page-title").single();
        assertThat(heading.element().getText()).isEqualTo(title);

        var buttons = header.find(UnifiedContainer.class).withClassName("page-header-top")
                .single().find(UnifiedButton.class).all();
        if (action == null) {
            assertThat(buttons).isEmpty();
        } else {
            assertThat(buttons).singleElement().satisfies(button -> {
                assertThat(button.getText()).isEqualTo(action);
                assertThat(button.element().getAttribute("theme"))
                        .contains(ButtonVariant.PRIMARY.getVariantName());
            });
        }

        var intro = anatomy.child(1);
        assertThat(intro.tag()).isEqualTo("p");
        assertThat(intro.hasClassName("page-intro")).isTrue();
        // The intro is where the consequences live, so it is never absent.
        assertThat(header.findComponent(Paragraph.class).withClassName("page-intro").single()
                .element().getText()).isNotBlank();
    }

    /**
     * The tab bar, the header block and whatever the view puts below it are
     * siblings one section gap apart — the 40px all three frames draw.
     */
    private void assertSectionRhythm(UnifiedPage page) {
        var content = ElementSnapshot.of(page.findComponent(Main.class).single(), 2);
        assertThat(content.children()).hasSize(1);
        var view = content.child(0);

        assertThat(view.gap()).isEqualTo("var(--em-section-gap)");
        assertThat(view.children())
                .hasSizeGreaterThan(2)
                .satisfies(children -> {
                    assertThat(children.get(0).tag()).isEqualTo("vaadin-tabs");
                    assertThat(children.get(0).hasClassName("reference-tabs")).isTrue();
                    assertThat(children.get(1).hasClassName("page-header")).isTrue();
                });
    }
}
