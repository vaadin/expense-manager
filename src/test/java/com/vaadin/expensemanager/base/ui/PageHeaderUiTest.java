package com.vaadin.expensemanager.base.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.locator.Locators;
import com.vaadin.expensemanager.allowance.ui.AllowanceRatesView;
import com.vaadin.expensemanager.reference.ui.ExpenseTypeView;
import com.vaadin.expensemanager.reference.ui.VatRateView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.ThemableLayout;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless test (pyramid layer 3, ADR-0012) for the shared {@link PageHeader}
 * block across the three reference routes it opens — {@code /vat-rates},
 * {@code /expense-types} and {@code /allowance-rates} (#184,
 * {@code docs/design/components/page-header.md}).
 *
 * <p>The three composed the block themselves and had already drifted apart on
 * the gap below the title, so what this asserts is <strong>sameness</strong>:
 * one {@link PageHeader} per route, the same anatomy inside it, the same two
 * gaps around and within it.
 *
 * <p><strong>What is deliberately not here.</strong> That the heading renders at
 * 40px, that the intro's 1.6 leading is unitless rather than Aura's inherited
 * 20px length, that the heading's colour survives dark mode, and that the title
 * row wraps at 360px instead of pushing a scrollbar — none of those are
 * decidable from a component tree. They are the visual verification's, and this
 * class stops at the class names and the tokens the two gaps name.
 */
@SpringBootTest
@ActiveProfiles("test")
class PageHeaderUiTest extends SpringBrowserlessTest implements Locators {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    /**
     * The create button's label, from the frame. The {@code h2} beside it carries
     * the noun, so the button does not repeat it.
     */
    private static final String CREATE_LABEL = "New";

    @Test
    @WithUserDetails("admin@vaadin.com")
    void vatRatesOpensWithTheSharedHeaderAndACreateButton() {
        var view = navigate(VatRateView.class);

        assertHeader("VAT rates", CREATE_LABEL);
        assertSectionRhythm(view);
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void expenseTypesOpensWithTheSharedHeaderAndACreateButton() {
        var view = navigate(ExpenseTypeView.class);

        assertHeader("Expense types", CREATE_LABEL);
        assertSectionRhythm(view);
    }

    /**
     * The allowance view's own actions seed a <em>year</em> rather than create the
     * page's record, so the design leaves its title row without a button and puts
     * them in the toolbar below. The block is otherwise identical.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void allowanceRatesOpensWithTheSameBlockAndNoHeaderAction() {
        var view = navigate(AllowanceRatesView.class);

        assertHeader("Allowance Rates", null);
        assertSectionRhythm(view);
    }

    // ------------------------------------------------------------- assertions

    /**
     * @param title  the page name the {@code h2} must read
     * @param action the create button's label, or {@code null} when the title row
     *               carries no action at all
     */
    private void assertHeader(String title, String action) {
        // One block per page: composed by PageHeader, not by the view.
        var header = find(PageHeader.class).single();
        assertThat(header.getElement().getClassList()).contains("page-header");
        // The design's 20px between the title row and the intro, through the
        // layout API rather than CSS.
        assertThat(header.getSpacing()).isEqualTo("var(--em-card-padding)");

        var parts = header.getChildren().toList();
        assertThat(parts).hasSize(2);

        var top = parts.get(0);
        assertThat(top.getElement().getClassList()).contains("page-header-top");

        var heading = top.getChildren().findFirst().orElseThrow();
        assertThat(heading).isInstanceOf(H2.class);
        assertThat(((H2) heading).getText()).isEqualTo(title);
        // The class carries the display size, not the element — the shell owns
        // the page's h1, so a view's own heading is the second level.
        assertThat(heading.getElement().getClassList()).contains("page-title");

        var buttons = top.getChildren().filter(Button.class::isInstance)
                .map(Button.class::cast).toList();
        if (action == null) {
            assertThat(buttons).isEmpty();
        } else {
            assertThat(buttons).singleElement().satisfies(button -> {
                assertThat(button.getText()).isEqualTo(action);
                assertThat(button.getThemeNames())
                        .contains(ButtonVariant.PRIMARY.getVariantName());
            });
        }

        var intro = parts.get(1);
        assertThat(intro).isInstanceOf(Paragraph.class);
        assertThat(intro.getElement().getClassList()).contains("page-intro");
        // The intro is where the consequences live, so it is never absent.
        assertThat(((Paragraph) intro).getText()).isNotBlank();
    }

    /**
     * The tab bar, the header block and whatever the view puts below it are
     * siblings one section gap apart — the 40px all three frames draw.
     */
    private void assertSectionRhythm(Component view) {
        assertThat(((ThemableLayout) view).getSpacing())
                .isEqualTo("var(--em-section-gap)");
        assertThat(view.getChildren().toList())
                .hasSizeGreaterThan(2)
                .satisfies(children -> {
                    assertThat(children.get(0)).isInstanceOf(ReferenceTabs.class);
                    assertThat(children.get(1)).isInstanceOf(PageHeader.class);
                });
    }
}
